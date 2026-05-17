package com.eight87.whisperboy.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Robolectric-backed round-trip tests for [AndroidThemeSettings].
 *
 * `ThemeMode` round-trips through `enum.name` via the R.B.1 [EnumSetting];
 * an unknown stored value should coerce to the default ([ThemeMode.FollowSystem]).
 * `dynamicColor` is a plain Boolean [Setting] with a `true` default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidThemeSettingsTest {

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: AndroidThemeSettings
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storeFile = File(context.filesDir, "theme_settings_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { storeFile },
        )
        settings = AndroidThemeSettings(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        storeFile.delete()
    }

    @Test
    fun `mode defaults to FollowSystem on a fresh store`() = scope.runTest {
        assertEquals(ThemeMode.FollowSystem, settings.mode.first())
    }

    @Test
    fun `dynamicColor defaults to true on a fresh store`() = scope.runTest {
        assertEquals(true, settings.dynamicColor.first())
    }

    @Test
    fun `setMode round-trips every ThemeMode value`() = scope.runTest {
        for (value in ThemeMode.entries) {
            settings.setMode(value)
            assertEquals(value, settings.mode.first())
        }
    }

    @Test
    fun `setDynamicColor round-trips true and false`() = scope.runTest {
        settings.setDynamicColor(false)
        assertEquals(false, settings.dynamicColor.first())
        settings.setDynamicColor(true)
        assertEquals(true, settings.dynamicColor.first())
    }

    @Test
    fun `unknown stored mode string coerces to FollowSystem on read`() = scope.runTest {
        // Forward-compat / corrupt-store scenario: a future build wrote a mode name the
        // current build doesn't know. Reads should fall back to the default.
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("mode")] = "Sepia"
        }
        assertEquals(ThemeMode.FollowSystem, settings.mode.first())
    }

    @Test
    fun `corrupted prefs file falls back to defaults via ReplaceFileCorruptionHandler`() =
        scope.runTest {
            // Simulate a corrupted DataStore-Preferences file by writing garbage bytes
            // to the backing file BEFORE the DataStore reads it for the first time.
            // Without the corruption handler the first read throws IOException up
            // through `data.map { … }` Flow collectors and crashes recomposition;
            // with `ReplaceFileCorruptionHandler { emptyPreferences() }` (the helper
            // [AppGraph.createPrefs] installs) the file is silently reset to empty
            // defaults — settings are lost for that facet, the app stays alive.
            val context =
                ApplicationProvider.getApplicationContext<android.content.Context>()
            val corruptFile = File(
                context.filesDir,
                "theme_settings_corrupt_${UUID.randomUUID()}.preferences_pb",
            )
            try {
                corruptFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05))
                val corruptStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = dataStoreScope,
                    produceFile = { corruptFile },
                )
                val recoveredSettings = AndroidThemeSettings(corruptStore)
                // Reads succeed and return defaults — no IOException propagates.
                assertEquals(ThemeMode.FollowSystem, recoveredSettings.mode.first())
                assertEquals(true, recoveredSettings.dynamicColor.first())
            } finally {
                corruptFile.delete()
            }
        }

    @Test
    fun `setters persist across a recreated settings instance backed by the same store`() =
        scope.runTest {
            settings.setMode(ThemeMode.Dark)
            settings.setDynamicColor(false)

            val reloaded = AndroidThemeSettings(dataStore)
            assertEquals(ThemeMode.Dark, reloaded.mode.first())
            assertEquals(false, reloaded.dynamicColor.first())
        }

    // K.5 follow-up — round-trip tests for the two custom-colour
    // pickers (ported-from-tonearmboy work).

    @Test
    fun `customBaseSeed defaults to 0L on a fresh store`() = scope.runTest {
        assertEquals(0L, settings.customBaseSeed.first())
    }

    @Test
    fun `customChromeTint defaults to 0L on a fresh store`() = scope.runTest {
        assertEquals(0L, settings.customChromeTint.first())
    }

    @Test
    fun `setCustomBaseSeed round-trips a non-zero RGB long`() = scope.runTest {
        settings.setCustomBaseSeed(0xFF8800L)
        assertEquals(0xFF8800L, settings.customBaseSeed.first())
    }

    @Test
    fun `setCustomBaseSeed round-trips zero (reset path)`() = scope.runTest {
        settings.setCustomBaseSeed(0x123456L)
        assertEquals(0x123456L, settings.customBaseSeed.first())
        settings.setCustomBaseSeed(0L)
        assertEquals(0L, settings.customBaseSeed.first())
    }

    @Test
    fun `setCustomChromeTint round-trips a non-zero RGB long`() = scope.runTest {
        settings.setCustomChromeTint(0x6464C8L)
        assertEquals(0x6464C8L, settings.customChromeTint.first())
    }

    @Test
    fun `setCustomChromeTint round-trips zero (reset path)`() = scope.runTest {
        settings.setCustomChromeTint(0xDEADBEL)
        assertEquals(0xDEADBEL, settings.customChromeTint.first())
        settings.setCustomChromeTint(0L)
        assertEquals(0L, settings.customChromeTint.first())
    }

    @Test
    fun `tintChromeByAlbumArt defaults to true on a fresh store`() = scope.runTest {
        assertEquals(true, settings.tintChromeByAlbumArt.first())
    }

    @Test
    fun `setTintChromeByAlbumArt round-trips true and false`() = scope.runTest {
        settings.setTintChromeByAlbumArt(false)
        assertEquals(false, settings.tintChromeByAlbumArt.first())
        settings.setTintChromeByAlbumArt(true)
        assertEquals(true, settings.tintChromeByAlbumArt.first())
    }

    @Test
    fun `custom colour setters persist across a recreated settings instance`() = scope.runTest {
        settings.setCustomBaseSeed(0xC0FFEEL)
        settings.setCustomChromeTint(0xABCDEFL)

        val reloaded = AndroidThemeSettings(dataStore)
        assertEquals(0xC0FFEEL, reloaded.customBaseSeed.first())
        assertEquals(0xABCDEFL, reloaded.customChromeTint.first())
    }
}
