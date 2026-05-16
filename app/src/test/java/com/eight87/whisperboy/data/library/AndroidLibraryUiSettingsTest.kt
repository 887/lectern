package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * JVM-only tests for [AndroidLibraryUiSettings].
 *
 * `PreferenceDataStoreFactory.create(produceFile = ...)` works without an Android `Context` —
 * the file path is all DataStore needs. A `TestScope` keeps the DataStore's internal coroutines
 * deterministic; a `TemporaryFolder` gives each test its own file.
 */
class AndroidLibraryUiSettingsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: AndroidLibraryUiSettings

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.root, "library_ui.preferences_pb") },
        )
        settings = AndroidLibraryUiSettings(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
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
        // Write garbage directly to simulate a forward-compat scenario (a future enum value
        // saved by a newer build, then loaded by an older build) or a corrupted store.
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

            // A second facet instance over the same DataStore should observe the writes.
            val reloaded = AndroidLibraryUiSettings(dataStore)
            assertEquals(GridMode.List, reloaded.gridMode.first())
            assertEquals(BookSortKey.Author, reloaded.sortKey.first())
            assertEquals(BookFilter.Current, reloaded.filter.first())
        }
}
