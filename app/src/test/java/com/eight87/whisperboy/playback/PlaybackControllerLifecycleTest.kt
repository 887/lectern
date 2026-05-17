package com.eight87.whisperboy.playback

import org.junit.Test

/**
 * Lifecycle / threading contract tests for [PlaybackController].
 *
 * The class wraps a Media3 [androidx.media3.session.MediaController], which enforces a
 * strict main-thread invariant on most of its getter / setter surface — calling
 * `currentMediaItemIndex`, `currentPosition`, `isPlaying`, transport commands, etc. from a
 * non-Main dispatcher throws `IllegalStateException("MediaController method is called from
 * a wrong thread")` at runtime.
 *
 * Constructing a real `MediaController` in a JVM unit test requires a live `MediaSession`,
 * which requires a `Service`, which is not available outside an instrumented test. The
 * tests below therefore document the contract via source-level assertions: a regression
 * that re-introduces a non-Main controller read from a coroutine collector would land in
 * source review as a violation of the patterns these tests describe.
 *
 * The canonical channels for hitting the controller from arbitrary coroutine threads are:
 *
 *  - [PlaybackController.onMain] — wraps a `(MediaController) -> Unit` block in
 *    `withContext(Dispatchers.Main)` so the block reads the controller safely. Every
 *    transport command (`play` / `pause` / `seekTo` / …) routes through this.
 *  - [PlaybackController.saveCurrentPositionFromMain] — helper invoked from `onMain` by
 *    the 1Hz `sample()` collector in `init`. Previously the collector called
 *    `saveCurrentPosition()` directly off the application scope's default dispatcher,
 *    which crashed on `currentMediaItemIndex`. The fix was to route through `onMain`.
 *
 * Public callers of [PlaybackController.saveCurrentPosition] MUST already be on Main:
 *
 *  - [androidx.media3.common.Player.Listener.onIsPlayingChanged] — fires on the
 *    application thread (Main) per Media3's listener dispatch contract.
 *  - [androidx.media3.common.Player.Listener.onMediaItemTransition] — same.
 *  - [androidx.lifecycle.DefaultLifecycleObserver.onStop] — `Lifecycle` callbacks are
 *    always on the Main thread per the AndroidX `Lifecycle` contract.
 *  - [PlaybackController.release] / [com.eight87.whisperboy.AppGraph.flushPlaybackPosition]
 *    — invoked from `PlaybackService.onDestroy`, which runs on the Main thread.
 *
 * Any new caller of `saveCurrentPosition()` must be added to this list (and verified to
 * actually run on Main). A new caller from a coroutine collector should use
 * `onMain { c -> saveCurrentPositionFromMain(c) }` instead.
 */
class PlaybackControllerLifecycleTest {

    /**
     * Documents the contract — see the class-level kdoc. This test always passes; its
     * value is the source-review surface it creates the next time a regression lands.
     */
    @Test
    fun saveCurrentPosition_contract_documented() {
        // No assertion — this is a documentation anchor. A regression that re-introduces
        // `saveCurrentPosition()` calls from a coroutine collector running on the
        // application scope's default dispatcher will crash at runtime with
        // `IllegalStateException("MediaController method is called from a wrong thread")`
        // within ~1s of any active playback (the 1Hz `sample` tick).
    }
}
