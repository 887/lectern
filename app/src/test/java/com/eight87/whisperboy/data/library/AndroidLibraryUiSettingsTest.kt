package com.eight87.whisperboy.data.library

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
 * Robolectric-backed reference test for [AndroidLibraryUiSettings].
 *
 * Exercises the [com.eight87.whisperboy.data.settings.Setting] round-trip
 * through a real [DataStore]&lt;Preferences&gt; backed by a Robolectric-shadow
 * `Context.filesDir` file — the same shape every "needs Android Context but no
 * UI" facet will pick up. Three setters + three Flow reads + a corruption
 * fall-back + a recreated-instance survival check.
 *
 * `@Config(sdk = [34])` pins the shadow SDK level so the run doesn't drift
 * with whatever the local Robolectric pulls as default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLibraryUiSettingsTest {

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: AndroidLibraryUiSettings
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        // Robolectric provides a shadow `Application` via `ApplicationProvider`;
        // its `filesDir` is a real per-test JVM tmp dir, which is exactly the
        // shape DataStore's `produceFile` block wants. Per-test UUID prefix keeps
        // parallel-class isolation honest (`PreferenceDataStoreFactory` rejects
        // a second instance pointed at the same file in the same process).
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storeFile = File(context.filesDir, "library_ui_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { storeFile },
        )
        settings = AndroidLibraryUiSettings(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        storeFile.delete()
    }

    @Test
    fun `gridMode defaults to Grid on a fresh store`() = scope.runTest {
        assertEquals(GridMode.Grid, settings.gridMode.first())
    }

    @Test
    fun `sortKey defaults to Title on a fresh store`() = scope.runTest {
        assertEquals(BookSortKey.Title, settings.sortKey.first())
    }

    @Test
    fun `filter defaults to All on a fresh store`() = scope.runTest {
        assertEquals(BookFilter.All, settings.filter.first())
    }

    @Test
    fun `setGridMode round-trips every value`() = scope.runTest {
        for (value in GridMode.entries) {
            settings.setGridMode(value)
            assertEquals(value, settings.gridMode.first())
        }
    }

    @Test
    fun `setSortKey round-trips every value`() = scope.runTest {
        for (value in BookSortKey.entries) {
            settings.setSortKey(value)
            assertEquals(value, settings.sortKey.first())
        }
    }

    @Test
    fun `setFilter round-trips every value`() = scope.runTest {
        for (value in BookFilter.entries) {
            settings.setFilter(value)
            assertEquals(value, settings.filter.first())
        }
    }

    @Test
    fun `unknown enum-name strings decode to defaults`() = scope.runTest {
        // Forward-compat / corrupted-store scenario: a future build wrote an
        // enum value the current build doesn't know. Reads should coerce to
        // the default rather than throw.
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("grid_mode")] = "Carousel"
            prefs[stringPreferencesKey("sort_key")] = "PublicationDate"
            prefs[stringPreferencesKey("filter")] = "Favorites"
        }
        assertEquals(GridMode.Grid, settings.gridMode.first())
        assertEquals(BookSortKey.Title, settings.sortKey.first())
        assertEquals(BookFilter.All, settings.filter.first())
    }

    @Test
    fun `setters persist across a recreated settings instance using the same store`() =
        scope.runTest {
            settings.setGridMode(GridMode.List)
            settings.setSortKey(BookSortKey.Author)
            settings.setFilter(BookFilter.Current)

            val reloaded = AndroidLibraryUiSettings(dataStore)
            assertEquals(GridMode.List, reloaded.gridMode.first())
            assertEquals(BookSortKey.Author, reloaded.sortKey.first())
            assertEquals(BookFilter.Current, reloaded.filter.first())
        }
}
