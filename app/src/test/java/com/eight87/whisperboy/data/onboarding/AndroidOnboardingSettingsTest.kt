package com.eight87.whisperboy.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
 * Issue-1 (onboarding loop) — round-trip + persistence-across-recreation tests for the
 * `OnboardingSettings.completed` flag. The earlier bug let the flag stay `false` if the
 * user closed the app mid-first-scan; this regression guard pins that once written, the
 * flag survives the DataStore being re-created from the same file (the cold-start
 * scenario where `AppGraph` rebuilds [AndroidOnboardingSettings] from scratch).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidOnboardingSettingsTest {

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: AndroidOnboardingSettings
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storeFile = File(context.filesDir, "onboarding_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { storeFile },
        )
        settings = AndroidOnboardingSettings(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        storeFile.delete()
    }

    @Test
    fun `completed defaults to false on a fresh store`() = scope.runTest {
        assertEquals(false, settings.completed.first())
    }

    @Test
    fun `setCompleted round-trips true and false`() = scope.runTest {
        settings.setCompleted(true)
        assertEquals(true, settings.completed.first())
        settings.setCompleted(false)
        assertEquals(false, settings.completed.first())
    }

    @Test
    fun `setCompleted true persists across AppGraph re-creation`() = scope.runTest {
        // Simulate "user finishes onboarding".
        settings.setCompleted(true)
        assertEquals(true, settings.completed.first())

        // Tear down the first DataStore + scope (process death equivalent), then
        // rebuild from the same file on a fresh scope — i.e. cold-start, AppGraph
        // constructs a new AndroidOnboardingSettings instance.
        dataStoreScope.cancel()
        val secondDispatcher = StandardTestDispatcher(scope.testScheduler)
        val secondScope = CoroutineScope(secondDispatcher + Job())
        val reloadedStore = PreferenceDataStoreFactory.create(
            scope = secondScope,
            produceFile = { storeFile },
        )
        val reloadedSettings = AndroidOnboardingSettings(reloadedStore)
        try {
            assertEquals(
                "Flag must survive AppGraph re-creation — Issue-1 regression guard",
                true,
                reloadedSettings.completed.first(),
            )
        } finally {
            secondScope.cancel()
        }
    }
}
