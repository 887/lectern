package com.eight87.whisperboy.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
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
        for (value in listOf(5, 10, 30, 60)) {
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

        settings.setAutoRewindSeconds(0)
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

    @Test
    fun `setters persist across a recreated settings instance using the same store`() =
        scope.runTest {
            settings.setRewindSeconds(10)
            settings.setForwardSeconds(60)
            settings.setAutoRewindSeconds(30)

            val reloaded = AndroidPlaybackSettings(dataStore)
            assertEquals(10, reloaded.rewindSeconds.first())
            assertEquals(60, reloaded.forwardSeconds.first())
            assertEquals(30, reloaded.autoRewindSeconds.first())
        }
}
