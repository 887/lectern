// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  // cold-start-perf F.1 — both pulled in here at apply-false so the
  // classpath is satisfied for `:app` (consumes baseline profile) and
  // `:baselineprofile` (`com.android.test` macrobenchmark module).
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
}