package com.eight87.whisperboy.data.library

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Android-side implementation of [LibraryRescanCoordinator].
 *
 * Wires three trigger paths into one conflated [Channel] of rescan signals:
 *
 * 1. **Manual** — [requestRescan] sends to the channel. Settings (Phase K) and the
 *    HomeScreen Rescan-now button both call this.
 * 2. **Foreground** — registers a [DefaultLifecycleObserver] on [ProcessLifecycleOwner];
 *    `onStart` fires a [requestRescan] call, debounced so two foregrounds within
 *    [foregroundDebounceMs] don't double-scan.
 * 3. **Root-change** — collects [PersistedUriPermissionStore.observeRoots] mapped to the
 *    set of `treeUri`s, runs through `distinctUntilChanged`, fires a [requestRescan] on
 *    each change. The initial emission also fires — that's the cold-start scan.
 *
 * Why a conflated channel: signals are coalesced, so spam clicks of the manual button
 * don't queue up an unbounded backlog. At most one scan runs and at most one is queued
 * behind it; subsequent triggers while one is queued are dropped. Each scan picks up the
 * **latest** set of roots via `observeRoots().first()` so a root added between the
 * manual press and the channel pickup still gets scanned.
 *
 * Suspend throughout. R.F.3 holds — no `runBlocking` on the scan path.
 *
 * Phase P.4 + P.8 extensions:
 *  - Per-root [LibraryFingerprint] cache lets unchanged roots skip the structural walk
 *    entirely (P.8). The `force` parameter on [requestRescan] is carried through the
 *    channel as a `Boolean` payload so the "Rescan now" button can demand a full walk.
 *  - [health] surfaces unreadable roots (revoked SAF permission, missing SD card) for the
 *    P.4 banner. We detect these by trying [SafLibraryScanner.computeFingerprint] AND
 *    [DocumentFile.fromTreeUri] on each root before the walk; a root that fails both is
 *    classified unreadable, surfaced in [health], and skipped on the walk (so the scanner
 *    can't crash mid-walk).
 */
internal class AndroidLibraryRescanCoordinator(
    private val context: Context,
    private val persistedUriPermissionStore: PersistedUriPermissionStore,
    private val libraryScanner: LibraryScanner,
    private val libraryScannerEnrichment: LibraryScannerEnrichment,
    private val scanWriter: ScanWriter,
    private val fingerprintStore: LibraryFingerprintStore,
    private val safLibraryScanner: SafLibraryScanner,
    private val applicationScope: CoroutineScope,
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val foregroundDebounceMs: Long = DEFAULT_FOREGROUND_DEBOUNCE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryRescanCoordinator {

    private val _state = MutableStateFlow<RescanState>(RescanState.Idle)
    override val state: StateFlow<RescanState> = _state.asStateFlow()

    private val _health = MutableStateFlow(LibraryHealth())
    override val health: StateFlow<LibraryHealth> = _health.asStateFlow()

    /** Channel payload: `true` = force full walk, bypassing the fingerprint short-circuit. */
    private val rescanChannel: Channel<Boolean> = Channel(capacity = Channel.CONFLATED)
    private var lastForegroundRescan: Long = 0L

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            val now = clock()
            if (now - lastForegroundRescan >= foregroundDebounceMs) {
                lastForegroundRescan = now
                requestRescan()
            }
        }
    }

    init {
        // 1. Wire the foreground hook.
        lifecycle.addObserver(foregroundObserver)

        // 2. Wire the root-change collector.
        applicationScope.launch {
            persistedUriPermissionStore.observeRoots()
                .map { roots -> roots.map { it.treeUri.toString() }.toSet() }
                .distinctUntilChanged()
                .collect { _ -> requestRescan() }
        }

        // 3. Drain the channel — one scan at a time, picks up the latest roots each time.
        applicationScope.launch {
            rescanChannel.consumeAsFlow().collect { force -> runScan(force = force) }
        }
    }

    override fun requestRescan(force: Boolean) {
        rescanChannel.trySend(force)
    }

    private suspend fun runScan(force: Boolean) {
        _state.value = RescanState.Running
        try {
            val roots = persistedUriPermissionStore.observeRoots().first()

            // Classify roots: readable (with current fingerprint), unreadable. Skip the walk
            // entirely for unreadable roots — they're surfaced via [health] for the P.4 banner.
            // The fingerprint probe also feeds the P.8 short-circuit further down.
            val readableRoots = mutableListOf<LibraryRoot>()
            val unreadable = mutableListOf<LibraryRoot>()
            // Per-root fingerprint indexed by treeUri string. null = "no fingerprint available
            // for this root" (probe failed but the tree was still readable enough for the walk).
            val currentFingerprints = mutableMapOf<String, LibraryFingerprint?>()

            for (root in roots) {
                val fp = safLibraryScanner.computeFingerprint(root.treeUri)
                val tree = DocumentFile.fromTreeUri(context, root.treeUri)
                val canRead = tree != null && runCatching { tree.canRead() }.getOrDefault(false)
                if (fp == null && !canRead) {
                    unreadable += root
                } else {
                    readableRoots += root
                    currentFingerprints[root.treeUri.toString()] = fp
                }
            }
            _health.value = LibraryHealth(unreadableRoots = unreadable)

            // P.8 short-circuit: drop roots whose fingerprint is unchanged AND not forced.
            val rootsToWalk = if (force) {
                readableRoots
            } else {
                readableRoots.filter { root ->
                    val key = root.treeUri.toString()
                    val current = currentFingerprints[key]
                    val stored = fingerprintStore.get(key)
                    val unchanged = current != null && stored != null && current == stored
                    if (unchanged) {
                        Log.i(SMOKE_TAG, "SCAN_SKIP_UNCHANGED root=$key fp=$current")
                    }
                    !unchanged
                }
            }

            if (rootsToWalk.isEmpty()) {
                Log.i(SMOKE_TAG, "SCAN_COMPLETE_NOOP roots=${roots.size} (all unchanged or unreadable)")
                _state.value = RescanState.Idle
                return
            }

            val structural = libraryScanner.scan(rootsToWalk)
            val enriched = libraryScannerEnrichment.enrich(structural)
            scanWriter.applyScan(enriched)

            // Persist new fingerprints for roots we successfully walked.
            for (root in rootsToWalk) {
                val fp = currentFingerprints[root.treeUri.toString()]
                if (fp != null) {
                    fingerprintStore.set(root.treeUri.toString(), fp)
                }
            }

            // Smoke-test marker (D.6): emit book + chapter counts so a smoke script can
            // poll logcat for scan completion + assert the result. Cheap; one line per scan.
            val books = enriched.books.size
            val chapters = enriched.books.sumOf { it.chapters.size }
            Log.i(SMOKE_TAG, "SCAN_COMPLETE roots=${rootsToWalk.size}/${roots.size} books=$books chapters=$chapters force=$force")
            _state.value = RescanState.Idle
        } catch (t: Throwable) {
            Log.e(SMOKE_TAG, "SCAN_FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            _state.value = RescanState.Failed(t)
        }
    }

    companion object {
        const val DEFAULT_FOREGROUND_DEBOUNCE_MS: Long = 30_000L
        private const val SMOKE_TAG: String = "whisperboy.scan"
    }
}
