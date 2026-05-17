package com.eight87.whisperboy.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.whisperboy.data.library.BookmarkEntity
import com.eight87.whisperboy.data.library.BookmarkSource
import com.eight87.whisperboy.data.playback.SleepTimerSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * State-machine tests for [AndroidSleepTimer].
 *
 * Uses [TestScope] so the timer's internal `delay()` calls are virtualised (no real sleeping).
 * The accelerometer surface is not driven from these tests — Robolectric's default SensorManager
 * has no accelerometer registered, so the post-fire branch lands as if shake-to-resume is
 * unavailable. The shake-to-resume *re-arming* sub-case is skipped (see comment on
 * `fire_without_accelerometer_transitions_to_Inactive`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerTest {

    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    private class FakePlayerHandle : PlayerHandle {
        val volumes = mutableListOf<Float>()
        var pauseCalls = 0
        var bookId: String? = "book-1"
        var chapterId: String? = "chap-1"
        var positionMs: Long = 12_345L
        private val transitions = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 8)
        override val mediaItemTransitions: SharedFlow<Unit> = transitions.asSharedFlow()
        suspend fun emitChapterTransition() = transitions.emit(Unit)

        override suspend fun setVolumeNow(volume: Float) { volumes += volume }
        override suspend fun pauseNow() { pauseCalls++ }
        override suspend fun currentPositionMs(): Long = positionMs
        override suspend fun currentBookId(): String? = bookId
        override suspend fun currentChapterId(): String? = chapterId
    }

    private class FakeBookmarkSource : BookmarkSource {
        data class Saved(
            val bookId: String,
            val chapterId: String?,
            val title: String?,
            val positionInBookMs: Long,
            val setBySleepTimer: Boolean,
        )

        val saved = mutableListOf<Saved>()
        override fun observeBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> = flowOf(emptyList())
        override suspend fun addBookmark(
            bookId: String,
            chapterId: String?,
            title: String?,
            positionInBookMs: Long,
            setBySleepTimer: Boolean,
        ) {
            saved += Saved(bookId, chapterId, title, positionInBookMs, setBySleepTimer)
        }
        override suspend fun renameBookmark(id: String, title: String?) = Unit
        override suspend fun deleteBookmark(id: String) = Unit
    }

    private class FakeSleepTimerSettings(
        fadeOut: kotlin.time.Duration = 5.seconds,
        shake: Boolean = true,
    ) : SleepTimerSettings {
        override val defaultDuration: Flow<kotlin.time.Duration> = MutableStateFlow(15.seconds).asStateFlow()
        override val fadeOutDuration: Flow<kotlin.time.Duration> = MutableStateFlow(fadeOut).asStateFlow()
        override val shakeToResume: Flow<Boolean> = MutableStateFlow(shake).asStateFlow()
        override val autoArmWindowStart: Flow<LocalTime?> = MutableStateFlow<LocalTime?>(null).asStateFlow()
        override val autoArmWindowEnd: Flow<LocalTime?> = MutableStateFlow<LocalTime?>(null).asStateFlow()
        override suspend fun setDefaultDuration(duration: kotlin.time.Duration) = Unit
        override suspend fun setFadeOutDuration(duration: kotlin.time.Duration) = Unit
        override suspend fun setShakeToResume(enabled: Boolean) = Unit
        override suspend fun setAutoArmWindow(start: LocalTime?, end: LocalTime?) = Unit
    }

    private fun TestScope.newTimer(
        player: FakePlayerHandle = FakePlayerHandle(),
        bookmarks: FakeBookmarkSource = FakeBookmarkSource(),
        settings: FakeSleepTimerSettings = FakeSleepTimerSettings(),
    ): Triple<AndroidSleepTimer, FakePlayerHandle, FakeBookmarkSource> {
        val timer = AndroidSleepTimer(
            context = appContext,
            playerHandle = player,
            bookmarkSource = bookmarks,
            sleepTimerSettings = settings,
            applicationScope = this,
        )
        return Triple(timer, player, bookmarks)
    }

    @Test
    fun arm_timed_transitions_to_Running_with_full_remaining() = runTest {
        val (timer, _, _) = newTimer()
        timer.arm(SleepTimerMode.Timed(10.seconds))
        runCurrent()
        val st = timer.state.value
        assertTrue("expected Running, got $st", st is SleepTimerState.Running)
        st as SleepTimerState.Running
        assertEquals(10_000L, st.remainingMs)
        assertEquals(false, st.fadingOut)
    }

    @Test
    fun tick_decrements_remaining_and_reaches_zero_then_fires() = runTest {
        val (timer, player, bookmarks) = newTimer(settings = FakeSleepTimerSettings(fadeOut = 0.milliseconds))
        timer.arm(SleepTimerMode.Timed(2.seconds))
        runCurrent()
        // Advance halfway: ~1s in. Use 200ms tick boundary.
        advanceTimeBy(1_000)
        runCurrent()
        val mid = timer.state.value
        assertTrue(mid is SleepTimerState.Running)
        val midRemaining = (mid as SleepTimerState.Running).remainingMs
        assertTrue("remaining should be < 2000, was $midRemaining", midRemaining < 2_000L)
        assertTrue("remaining should be > 0, was $midRemaining", midRemaining > 0L)
        // Now drive to fire.
        advanceUntilIdle()
        assertEquals("pauseNow must be invoked when timer fires", 1, player.pauseCalls)
        assertEquals("auto-bookmark with setBySleepTimer=true must be written", 1, bookmarks.saved.size)
        val saved = bookmarks.saved.single()
        assertEquals("book-1", saved.bookId)
        assertEquals(true, saved.setBySleepTimer)
    }

    @Test
    fun fade_window_ramps_volume_from_near_1_to_near_0() = runTest {
        // Pick fade = 1s, total = 1s so the entire run is the fade window.
        val (timer, player, _) = newTimer(settings = FakeSleepTimerSettings(fadeOut = 1.seconds))
        timer.arm(SleepTimerMode.Timed(1.seconds))
        runCurrent()
        advanceUntilIdle()
        // setVolumeNow is called from three places: cancelInternal's restore-to-1f at arm time,
        // each tick inside the fade window, then fire()'s restore-to-1f after pause. We want only
        // the in-fade samples, i.e. drop the leading 1f (arm-time restore) and the trailing 1f
        // (post-fire restore).
        val ramp = player.volumes.drop(1).dropLast(1)
        assertTrue("expected several ramp samples, got ${player.volumes}", ramp.size >= 2)
        assertTrue("first ramp sample should be < 1f, got ${ramp.first()}", ramp.first() < 1f)
        assertTrue("last ramp sample should be near 0, got ${ramp.last()}", ramp.last() <= 0.2f)
        // Monotone non-increasing along the ramp.
        for (i in 1 until ramp.size) {
            assertTrue(
                "ramp must be non-increasing: ramp[${i - 1}]=${ramp[i - 1]} ramp[$i]=${ramp[i]}",
                ramp[i] <= ramp[i - 1] + 1e-3f,
            )
        }
    }

    @Test
    fun cancel_clears_state_at_any_phase() = runTest {
        val (timer, _, _) = newTimer()
        timer.arm(SleepTimerMode.Timed(10.seconds))
        runCurrent()
        assertTrue(timer.state.value is SleepTimerState.Running)
        timer.cancel()
        runCurrent()
        assertEquals(SleepTimerState.Inactive, timer.state.value)
    }

    @Test
    fun cancel_before_arm_is_noop() = runTest {
        val (timer, _, _) = newTimer()
        timer.cancel()
        runCurrent()
        assertEquals(SleepTimerState.Inactive, timer.state.value)
    }

    @Test
    fun endOfChapter_fires_on_mediaItemTransition_not_on_tick() = runTest {
        val (timer, player, bookmarks) = newTimer()
        timer.arm(SleepTimerMode.EndOfChapter)
        runCurrent()
        // No transition yet — advance "lots" of time; must still not fire.
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("EndOfChapter must not fire on tick alone", 0, player.pauseCalls)
        assertEquals(0, bookmarks.saved.size)
        // Now emit a chapter transition: must fire.
        player.emitChapterTransition()
        runCurrent()
        advanceUntilIdle()
        assertEquals(1, player.pauseCalls)
        assertEquals(1, bookmarks.saved.size)
    }

    @Test
    fun fire_without_accelerometer_transitions_to_Inactive() = runTest {
        // Robolectric's default SensorManager exposes no accelerometer, so registerShakeDetector
        // bails and the state lands on Inactive immediately after fire. This covers the no-shake
        // arm-end branch; the shake-resume re-arm sub-case is intentionally skipped — it requires
        // driving a synthetic SensorEvent through Robolectric's shadow SensorManager, which is
        // load-bearing on `ShadowSensorManager` internals we don't want to wire up for one test.
        val (timer, _, _) = newTimer(settings = FakeSleepTimerSettings(fadeOut = 0.milliseconds))
        timer.arm(SleepTimerMode.Timed(500.milliseconds))
        runCurrent()
        advanceUntilIdle()
        assertEquals(SleepTimerState.Inactive, timer.state.value)
    }

    @Test
    fun arm_records_lastMode_implicitly_by_allowing_subsequent_arm_replacements() = runTest {
        // Re-arming replaces the prior timer (cancelInternal is called). Net effect we can observe:
        // state reflects the new mode + duration, and only ONE fire eventually occurs.
        val (timer, player, _) = newTimer(settings = FakeSleepTimerSettings(fadeOut = 0.milliseconds))
        timer.arm(SleepTimerMode.Timed(10.seconds))
        runCurrent()
        // Re-arm with a different duration. AndroidSleepTimer enforces MIN_TIMER_MS = 1000 — use
        // 2s so the replacement is observably different from the first arm.
        timer.arm(SleepTimerMode.Timed(2.seconds))
        runCurrent()
        val st = timer.state.value
        assertTrue(st is SleepTimerState.Running)
        assertEquals(2_000L, (st as SleepTimerState.Running).remainingMs)
        advanceUntilIdle()
        assertEquals("only the second timer should have fired", 1, player.pauseCalls)
    }
}
