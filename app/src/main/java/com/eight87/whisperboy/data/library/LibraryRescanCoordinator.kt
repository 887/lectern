package com.eight87.whisperboy.data.library

import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow data interface (Phase R.A.1 extension) for triggering and observing library
 * rescans. Composables and ViewModels depend on this contract; the concrete
 * `AndroidLibraryRescanCoordinator` lives behind it and wires the Android-side hooks
 * (lifecycle observer, root-change collection) internally.
 *
 * Phase D.5 wires three trigger paths into the implementation:
 *
 * 1. **Manual** — [requestRescan] is the public method. Settings → Rescan (Phase K) calls
 *    this; HomeScreen's bridge in Phase C/D wires a temporary "Rescan now" button to it.
 * 2. **Foreground** — `ProcessLifecycleOwner.ON_START` triggers an internal call to
 *    [requestRescan], debounced so two foregrounds within 30s don't double-scan.
 * 3. **Root-change** — the [PersistedUriPermissionStore.observeRoots] Flow drives an
 *    internal `distinctUntilChanged` collector; whenever the set of roots changes (the
 *    user picks a new folder or removes one), an internal [requestRescan] fires.
 *
 * The coordinator does NOT use a `ContentObserver` on the SAF tree — SAF doesn't surface
 * change events the same way `MediaStore` does. Phase D's design is **rescan on signal,
 * not on observation**.
 *
 * Phase P.4 extension: [health] surfaces unreadable roots (revoked SAF permission, missing
 * SD card, etc.) to the library screen so it can render a banner offering re-pick.
 *
 * Phase P.8 extension: [requestRescan] takes an optional `force` flag. When `false` (the
 * default for foreground / root-change auto-triggers and the implicit-trigger paths), the
 * coordinator consults the per-root [LibraryFingerprint] cache and skips the structural
 * walk for roots whose `(documentCount, maxMtime)` is unchanged since the last scan. Settings
 * → "Rescan now" passes `force = true` so the user can demand a full walk on suspicion of
 * a missed change.
 */
interface LibraryRescanCoordinator {

    val state: StateFlow<RescanState>

    /** Phase P.4 — set of roots that failed to read on the most recent scan attempt. */
    val health: StateFlow<LibraryHealth>

    /**
     * Trigger a rescan. Conflated — calling repeatedly while a scan is running queues at most
     * one follow-up. Pass `force = true` to bypass the per-root fingerprint short-circuit
     * (Phase P.8).
     */
    fun requestRescan(force: Boolean = false)
}

/**
 * Lifecycle of a single rescan pass. Failures surface their cause so the UI can render
 * a "tap to retry" affordance with diagnostic context.
 */
sealed class RescanState {
    data object Idle : RescanState()

    /**
     * Active scan. [booksFound] / [chaptersFound] tick up as the enrichment pipeline
     * finishes each book (the structural pass emits the initial book count once the
     * tree walk completes; enrichment refines `chaptersFound` per book). [currentFolder]
     * (when set) is the folder/title of the book currently being enriched and is
     * surfaced by the in-library progress banner.
     *
     * Defaults of 0 / null keep the indeterminate-progress UX rendering correctly
     * during the structural walk before any per-book numbers are available.
     */
    data class Running(
        val booksFound: Int = 0,
        val chaptersFound: Int = 0,
        val currentFolder: String? = null,
    ) : RescanState()

    data class Failed(val cause: Throwable) : RescanState()
}

/**
 * Phase P.4 — library health snapshot. [unreadableRoots] is the list of [LibraryRoot]s
 * whose SAF tree could not be opened on the most recent scan pass (typically because the
 * persisted URI permission was revoked, or the SD card holding the tree was removed).
 *
 * When this list is non-empty the library screen renders a banner above the rail+content
 * row offering to navigate to the folder-management screen so the user can re-pick.
 */
data class LibraryHealth(
    val unreadableRoots: List<LibraryRoot> = emptyList(),
)
