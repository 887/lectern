package com.eight87.whisperboy.ui.settings

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * oss-licenses Phase C.1 — guards the Licensee-generated
 * `assets/licenses/artifacts.json` inventory and its companion SPDX
 * license-text assets. Catches three regressions:
 *
 *   - dependency removal (a known sample missing → tests fail loud)
 *   - new dependency with an unrecognised SPDX (not in the allowlist)
 *   - inventory drift (entry has a SPDX id but the `<spdx>.txt` is absent)
 *
 * Pure-JVM, no Robolectric — Whisperboy has no Robolectric dep yet, and
 * the catalog is a flat JSON file on disk that we can read directly via
 * `File("src/main/assets/...")`. Gradle's `:app:test` runs with
 * `user.dir = app/`, so the relative path resolves correctly.
 */
class LicensesCatalogTest {

  @Serializable
  private data class LicenseeArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val spdxLicenses: List<LicenseeSpdx> = emptyList(),
  )

  @Serializable
  private data class LicenseeSpdx(val identifier: String)

  private val json = Json { ignoreUnknownKeys = true }
  private val licensesDir = File("src/main/assets/licenses")

  private fun loadArtifacts(): List<LicenseeArtifact> {
    val artifactsFile = File(licensesDir, "artifacts.json")
    assertTrue(
      "Expected ${artifactsFile.absolutePath} to exist — run :app:licenseeAndroidDebug.",
      artifactsFile.exists(),
    )
    return json.decodeFromString<List<LicenseeArtifact>>(artifactsFile.readText())
  }

  @Test
  fun `catalog is non-empty`() {
    val artifacts = loadArtifacts()
    assertTrue("artifacts.json should contain at least one entry", artifacts.isNotEmpty())
  }

  @Test
  fun `every SPDX in artifacts has matching license text`() {
    val artifacts = loadArtifacts()
    val seenSpdx = artifacts.flatMap { it.spdxLicenses }.map { it.identifier }.toSet()
    val allowed = setOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")

    val disallowed = seenSpdx - allowed
    assertTrue(
      "SPDX ids in inventory but not in the allowlist " +
        "(extend licensee { allow(...) } in app/build.gradle.kts and ship the " +
        "canonical text at app/src/main/assets/licenses/<spdx>.txt): $disallowed",
      disallowed.isEmpty(),
    )

    for (spdx in seenSpdx) {
      val textFile = File(licensesDir, "$spdx.txt")
      assertTrue(
        "Missing license text for $spdx at ${textFile.absolutePath}",
        textFile.exists(),
      )
    }
  }

  @Test
  fun `catalog contains known shipping samples`() {
    val artifacts = loadArtifacts()
    val coords = artifacts.map { "${it.groupId}:${it.artifactId}" }.toSet()
    val expected = listOf(
      "androidx.media3:media3-exoplayer",
      "androidx.room:room-runtime",
      "io.coil-kt.coil3:coil-compose",
    )
    expected.forEach { sample ->
      assertNotNull(
        "Expected $sample in the inventory — has a critical shipping dep been removed?",
        coords.firstOrNull { it == sample },
      )
    }
  }
}
