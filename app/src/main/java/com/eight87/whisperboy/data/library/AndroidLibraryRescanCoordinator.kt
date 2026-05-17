package com.eight87.whisperboy.data.library

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val bookSource: BookSource,
    private val coverStore: CoverStore,
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
        _state.value = RescanState.Running()
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
                // Still GC orphan covers — `forgetBook` may have run since the last scan, and
                // a no-op scan is still a "scan completed successfully" point for cleanup.
                gcOrphanCovers()
                _state.value = RescanState.Idle
                return
            }

            // Streaming pipeline: discovery streams books into a shared channel, N=4 parallel
            // workers steal from the channel, each runs `enrichBook` for one book and writes
            // the enriched row immediately so the library grid fills in as workers finish.
            // Structural rows land in Room every BATCH_SIZE books via `applyBookBatch`, so
            // discovered books appear in `BookSource.observeActive()` BEFORE the full scan
            // completes.
            val touchedRoots = mutableSetOf<String>()
            val seenBookIds = mutableSetOf<String>()
            val seenLock = Mutex()
            val progressLock = Mutex()
            var lastDiscoveryEmit = 0L
            var discoveryBooksFound = 0
            var discoveryChaptersFound = 0
            var analyzedBooks = 0
            var analyzedChapters = 0
            var discoveryDone = false
            // Snapshot total chapter count grows during discovery; once discovery completes the
            // analyzer-phase banner uses the final total for its determinate progress bar.

            suspend fun emitState(currentFolder: String?) {
                progressLock.withLock {
                    _state.value = RescanState.Running(
                        booksFound = discoveryBooksFound,
                        chaptersFound = discoveryChaptersFound,
                        currentFolder = currentFolder,
                        phase = if (discoveryDone) ScanPhase.Analyzing else ScanPhase.Discovering,
                        analyzedChapters = analyzedChapters,
                        totalChapters = discoveryChaptersFound,
                    )
                }
            }

            coroutineScope {
                val analyzeQueue: Channel<ScannedBook> = Channel(capacity = Channel.UNLIMITED)
                val pending = mutableListOf<ScannedBook>()
                val pendingLock = Mutex()

                suspend fun flushPending(force: Boolean) {
                    val toWrite: List<ScannedBook> = pendingLock.withLock {
                        if (pending.isEmpty()) return@withLock emptyList()
                        if (!force && pending.size < BATCH_SIZE) return@withLock emptyList()
                        val out = pending.toList()
                        pending.clear()
                        out
                    }
                    if (toWrite.isNotEmpty()) {
                        runCatching { scanWriter.applyBookBatch(toWrite) }
                            .onFailure { Log.w(SMOKE_TAG, "BATCH_APPLY_FAILED size=${toWrite.size}: ${it.message}", it) }
                    }
                }

                // 1. Discovery coroutine — walks the SAF tree, accumulates books in BATCH_SIZE
                //    chunks, writes each chunk to Room, dispatches every book onto the analyze
                //    queue for the worker pool. Closes the queue when the walk completes.
                val discoveryJob = launch(Dispatchers.IO) {
                    libraryScanner.scanStreaming(
                        roots = rootsToWalk,
                        onProgress = { books, chapters, folder ->
                            val now = clock()
                            discoveryBooksFound = books
                            discoveryChaptersFound = chapters
                            if (now - lastDiscoveryEmit >= PROGRESS_THROTTLE_MS) {
                                lastDiscoveryEmit = now
                                emitState(folder)
                            }
                        },
                        onBookDiscovered = { book ->
                            touchedRoots += book.treeUriString
                            seenLock.withLock { seenBookIds += book.bookId }
                            val shouldFlush = pendingLock.withLock {
                                pending += book
                                pending.size >= BATCH_SIZE
                            }
                            if (shouldFlush) flushPending(force = false)
                            // Dispatch immediately so a free worker can pick up the book before
                            // the rest of the batch even lands in Room.
                            analyzeQueue.send(book)
                        },
                    )
                    // Final partial batch — anything below BATCH_SIZE that didn't trigger a flush.
                    flushPending(force = true)
                    // Snap to final discovery counts and flip phase to Analyzing.
                    discoveryDone = true
                    emitState(null)
                    analyzeQueue.close()
                }

                // 2. N=4 parallel workers — each pulls one book at a time from the shared
                //    channel, enriches it, lands the enrichment via `applyBookEnrichment`.
                //    Whichever worker is free picks up the next book (work-stealing).
                val workers = List(WORKER_COUNT) {
                    launch(Dispatchers.IO) {
                        for (book in analyzeQueue) {
                            val enrichedBook = runCatching { libraryScannerEnrichment.enrichBook(book) }
                                .getOrElse { t ->
                                    Log.w(SMOKE_TAG, "ENRICH_FAILED bookId=${book.bookId}: ${t.message}", t)
                                    book.copy(durationMs = 0L)
                                }
                            runCatching { scanWriter.applyBookEnrichment(enrichedBook) }
                                .onFailure { Log.w(SMOKE_TAG, "ENRICH_APPLY_FAILED bookId=${book.bookId}: ${it.message}", it) }
                            progressLock.withLock {
                                analyzedBooks += 1
                                analyzedChapters += enrichedBook.chapters.size
                            }
                            emitState(book.title)
                        }
                    }
                }

                discoveryJob.join()
                workers.joinAll()
            }

            // Sweep books that disappeared from touched roots (soft-delete).
            scanWriter.sweepInactiveRoots(touchedRoots, seenBookIds)

            _state.value = RescanState.Running(
                booksFound = discoveryBooksFound,
                chaptersFound = discoveryChaptersFound,
                currentFolder = null,
                phase = ScanPhase.Writing,
                analyzedChapters = analyzedChapters,
                totalChapters = discoveryChaptersFound,
            )

            // Persist new fingerprints for roots we successfully walked.
            for (root in rootsToWalk) {
                val fp = currentFingerprints[root.treeUri.toString()]
                if (fp != null) {
                    fingerprintStore.set(root.treeUri.toString(), fp)
                }
            }

            // Smoke-test marker (D.6): emit book + chapter counts so a smoke script can
            // poll logcat for scan completion + assert the result. Cheap; one line per scan.
            Log.i(SMOKE_TAG, "SCAN_COMPLETE roots=${rootsToWalk.size}/${roots.size} books=$discoveryBooksFound chapters=$discoveryChaptersFound force=$force")

            gcOrphanCovers()
            _state.value = RescanState.Idle
        } catch (t: Throwable) {
            Log.e(SMOKE_TAG, "SCAN_FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            _state.value = RescanState.Failed(t)
        }
    }

    /**
     * Orphan-cover GC: after the Room write lands, sweep any cover file on disk whose basename
     * doesn't match an active book. Catches leaks from `forgetBook` (a process death between
     * `deleteById` and `deleteCover` left the file), `markRootInactive` (soft-deleted books
     * accumulate covers indefinitely), and scan-removed books.
     *
     * Soft-deleted (inactive) books are NOT in `observeBooks()` (which delegates to the
     * `active = 1` query), so their covers are reaped here — acceptable because a re-add of the
     * same folder re-scans cover bytes from disk / embedded source.
     */
    private suspend fun gcOrphanCovers() {
        val activeBookIds = bookSource.observeBooks().first().map { it.bookId }.toSet()
        val orphansDeleted = coverStore.gcOrphans(activeBookIds)
        if (orphansDeleted > 0) {
            Log.i(SMOKE_TAG, "SCAN_GC_COVERS deleted=$orphansDeleted active=${activeBookIds.size}")
        }
    }

    companion object {
        const val DEFAULT_FOREGROUND_DEBOUNCE_MS: Long = 30_000L
        private const val SMOKE_TAG: String = "whisperboy.scan"
        /** Bug 1: per-emission throttle on the discovery-phase progress callback so a fast SD
         *  card's flurry of book-found callbacks doesn't thrash the StateFlow / banner. 200ms
         *  matches Compose's perceived-update budget — the banner still feels live. */
        private const val PROGRESS_THROTTLE_MS: Long = 200L
        /** Streaming-scan batch size: every N discovered books trigger an `applyBookBatch` write
         *  so the library grid fills in continuously instead of waiting for the full walk. */
        private const val BATCH_SIZE: Int = 5
        /** Parallel enrichment worker count. Four feels right for medium libraries on modest
         *  phones: enough parallelism to hide per-file IO latency, not so many that the
         *  MediaMetadataRetriever instances thrash the CPU. */
        private const val WORKER_COUNT: Int = 4
    }
}
