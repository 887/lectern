package com.eight87.whisperboy.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    fun `setters persist across a recreated settings instance backed by the same store`() =
        scope.runTest {
            settings.setMode(ThemeMode.Dark)
            settings.setDynamicColor(false)

            val reloaded = AndroidThemeSettings(dataStore)
            assertEquals(ThemeMode.Dark, reloaded.mode.first())
            assertEquals(false, reloaded.dynamicColor.first())
        }
}
