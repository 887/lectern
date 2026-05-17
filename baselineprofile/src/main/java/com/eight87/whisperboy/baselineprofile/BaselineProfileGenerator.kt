package com.eight87.whisperboy.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * cold-start-perf F.2 — records the cold-boot path of whisperboy:
 * `LAUNCHER` tap → `WhisperboyActivity` first frame → library grid /
 * first-run picker visible.
 *
 * Run via `./gradlew :app:generateBaselineProfile` against a connected
 * AVD or rooted physical device. Output lands in
 * `app/src/main/baseline-prof.txt`, which is committed and packaged
 * into the APK by the `androidx.baselineprofile` plugin applied to
 * `:app`.
 *
 * The journey deliberately stops at "first usable screen visible"
 * (`waitForIdle`) — we do NOT tap into a specific book, because the
 * generator runs against a freshly installed app with no SAF tree
 * picked yet (so library is empty / first-run picker is the
 * "first usable screen"). Recording deeper interactions would require
 * fixturing the SAF state ahead of time, which is more brittle than
 * the win is worth. Add deeper journeys here if/when an interactive
 * surface ends up on the cold-start critical path.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.eight87.whisperboy",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Wait briefly for the splash → first-frame composition → library /
        // first-run picker. `waitForIdle` returns when the device's UI
        // automator reports no pending work; 2s is enough on the headless
        // AVD without blowing wall-clock on real devices.
        device.waitForIdle(2_000)
    }
}
