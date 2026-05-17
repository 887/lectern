package com.eight87.whisperboy.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM-only tests for [AndroidPlaybackSettings] — mirrors `AndroidLibraryUiSettingsTest`'s shape.
 */
class AndroidPlaybackSettingsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: AndroidPlaybackSettings

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.root, "playback_settings.preferences_pb") },
        )
        settings = AndroidPlaybackSettings(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `rewindSeconds defaults to 30 on a fresh store`() = scope.runTest {
        assertEquals(30, settings.rewindSeconds.first())
    }

    @Test
    fun `forwardSeconds defaults to 30 on a fresh store`() = scope.runTest {
        assertEquals(30, settings.forwardSeconds.first())
    }

    @Test
    fun `autoRewindSeconds defaults to 5 on a fresh store`() = scope.runTest {
        assertEquals(5, settings.autoRewindSeconds.first())
    }

    @Test
    fun `setRewindSeconds round-trips each allowed value`() = scope.runTest {
        for (value in listOf(5, 10, 30, 60)) {
            settings.setRewindSeconds(value)
            assertEquals(value, settings.rewindSeconds.first())
        }
    }

    @Test
    fun `setForwardSeconds round-trips each allowed value`() = scope.runTest {
        for (value in listOf(5, 10, 30, 60)) {
            settings.setForwardSeconds(value)
            assertEquals(value, settings.forwardSeconds.first())
        }
    }

    @Test
    fun `setAutoRewindSeconds round-trips each allowed value`() = scope.runTest {
        // K.2 picker shape: 0 (off) / 3 / 5 / 10.
        for (value in listOf(0, 3, 5, 10)) {
            settings.setAutoRewindSeconds(value)
            assertEquals(value, settings.autoRewindSeconds.first())
        }
    }

    @Test
    fun `out-of-range setter inputs coerce to default on write`() = scope.runTest {
        settings.setRewindSeconds(7) // not in allowed set
        assertEquals(30, settings.rewindSeconds.first())

        settings.setForwardSeconds(-1)
        assertEquals(30, settings.forwardSeconds.first())

        settings.setAutoRewindSeconds(60) // not in K.2 auto-rewind set
        assertEquals(5, settings.autoRewindSeconds.first())
    }

    @Test
    fun `out-of-range persisted values coerce to default on read`() = scope.runTest {
        // Simulate a future build's stored value or a corrupted store.
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("rewind_seconds")] = 99
            prefs[intPreferencesKey("forward_seconds")] = 1
            prefs[intPreferencesKey("auto_rewind_seconds")] = 1234
        }
        assertEquals(30, settings.rewindSeconds.first())
        assertEquals(30, settings.forwardSeconds.first())
        assertEquals(5, settings.autoRewindSeconds.first())
    }

    // ---- K.2 per-book defaults ----

    @Test
    fun `defaultSpeed defaults to 1f on a fresh store`() = scope.runTest {
        assertEquals(1.0f, settings.defaultSpeed.first(), 0.0001f)
    }

    @Test
    fun `defaultSkipSilence defaults to false on a fresh store`() = scope.runTest {
        assertEquals(false, settings.defaultSkipSilence.first())
    }

    @Test
    fun `defaultGainDb defaults to 0f on a fresh store`() = scope.runTest {
        assertEquals(0.0f, settings.defaultGainDb.first(), 0.0001f)
    }

    @Test
    fun `setDefaultSpeed round-trips an in-range value`() = scope.runTest {
        settings.setDefaultSpeed(1.5f)
        assertEquals(1.5f, settings.defaultSpeed.first(), 0.0001f)
    }

    @Test
    fun `setDefaultSpeed clamps out-of-range input`() = scope.runTest {
        settings.setDefaultSpeed(10f)
        assertEquals(3.5f, settings.defaultSpeed.first(), 0.0001f)

        settings.setDefaultSpeed(0.1f)
        assertEquals(0.5f, settings.defaultSpeed.first(), 0.0001f)
    }

    @Test
    fun `setDefaultSkipSilence round-trips both values`() = scope.runTest {
        settings.setDefaultSkipSilence(true)
        assertEquals(true, settings.defaultSkipSilence.first())
        settings.setDefaultSkipSilence(false)
        assertEquals(false, settings.defaultSkipSilence.first())
    }

    @Test
    fun `setDefaultGainDb round-trips an in-range value`() = scope.runTest {
        settings.setDefaultGainDb(4.5f)
        assertEquals(4.5f, settings.defaultGainDb.first(), 0.0001f)
    }

    @Test
    fun `setDefaultGainDb clamps out-of-range input`() = scope.runTest {
        settings.setDefaultGainDb(50f)
        assertEquals(12.0f, settings.defaultGainDb.first(), 0.0001f)

        settings.setDefaultGainDb(-100f)
        assertEquals(-3.0f, settings.defaultGainDb.first(), 0.0001f)
    }

    @Test
    fun `out-of-range persisted default speed coerces to 1f on read`() = scope.runTest {
        dataStore.edit { prefs ->
            prefs[floatPreferencesKey("default_speed")] = 99f
        }
        assertEquals(1.0f, settings.defaultSpeed.first(), 0.0001f)
    }

    @Test
    fun `out-of-range persisted default gain coerces to 0f on read`() = scope.runTest {
        dataStore.edit { prefs ->
            prefs[floatPreferencesKey("default_gain_db")] = 99f
        }
        assertEquals(0.0f, settings.defaultGainDb.first(), 0.0001f)
    }

    @Test
    fun `persisted default skip silence round-trips through a recreated instance`() = scope.runTest {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("default_skip_silence")] = true
        }
        val reloaded = AndroidPlaybackSettings(dataStore)
        assertEquals(true, reloaded.defaultSkipSilence.first())
    }

    @Test
    fun `setters persist across a recreated settings instance using the same store`() =
        scope.runTest {
            settings.setRewindSeconds(10)
            settings.setForwardSeconds(60)
            settings.setAutoRewindSeconds(10)

            val reloaded = AndroidPlaybackSettings(dataStore)
            assertEquals(10, reloaded.rewindSeconds.first())
            assertEquals(60, reloaded.forwardSeconds.first())
            assertEquals(10, reloaded.autoRewindSeconds.first())
        }
}
