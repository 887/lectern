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
 */
interface LibraryRescanCoordinator {

    val state: StateFlow<RescanState>

    /** Trigger a rescan. Conflated — calling repeatedly while a scan is running queues at most one follow-up. */
    fun requestRescan()
}

/**
 * Lifecycle of a single rescan pass. Failures surface their cause so the UI can render
 * a "tap to retry" affordance with diagnostic context.
 */
sealed class RescanState {
    data object Idle : RescanState()
    data object Running : RescanState()
    data class Failed(val cause: Throwable) : RescanState()
}
