package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Robolectric-backed tests for [AndroidPersistedUriPermissionStore]'s [observeRoots] read
 * path and [removeRoot] write path, exercised against a real DataStore.
 *
 * The [addRoot] write side calls `ContentResolver.takePersistableUriPermission`, which
 * throws `SecurityException` in Robolectric without a real persisted SAF grant — that side
 * requires a custom `ShadowContentResolver` install, out of scope here. We seed roots by
 * writing the canonical preference shape directly, which exercises the same observable
 * surface that downstream consumers see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPersistedUriPermissionStoreTest {

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: AndroidPersistedUriPermissionStore
    private lateinit var storeFile: File

    private val keyUris = stringSetPreferencesKey("library_root_uris")
    private fun typeKey(uri: String) = stringPreferencesKey("$uri::type")
    private fun nameKey(uri: String) = stringPreferencesKey("$uri::name")

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storeFile = File(context.filesDir, "library_roots_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { storeFile },
        )
        store = AndroidPersistedUriPermissionStore(context, dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        storeFile.delete()
    }

    @Test
    fun `observeRoots emits empty list on a fresh store`() = scope.runTest {
        assertEquals(emptyList<LibraryRoot>(), store.observeRoots().first())
    }

    @Test
    fun `a single seeded root round-trips through observeRoots`() = scope.runTest {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks"
        dataStore.edit { prefs ->
            prefs[keyUris] = setOf(uri)
            prefs[typeKey(uri)] = FolderType.id(FolderType.Root)
            prefs[nameKey(uri)] = "Audiobooks"
        }
        val roots = store.observeRoots().first()
        assertEquals(1, roots.size)
        val root = roots.single()
        assertEquals("Audiobooks", root.displayName)
        assertTrue(root.folderType is FolderType.Root)
        assertEquals(uri, root.treeUri.toString())
    }

    @Test
    fun `multiple seeded roots with mixed FolderTypes round-trip`() = scope.runTest {
        val uriA = "content://provider/tree/A"
        val uriB = "content://provider/tree/B"
        val uriC = "content://provider/tree/C"
        val uriD = "content://provider/tree/D"
        dataStore.edit { prefs ->
            prefs[keyUris] = setOf(uriA, uriB, uriC, uriD)
            prefs[typeKey(uriA)] = FolderType.id(FolderType.Root)
            prefs[nameKey(uriA)] = "A"
            prefs[typeKey(uriB)] = FolderType.id(FolderType.SingleFolder)
            prefs[nameKey(uriB)] = "B"
            prefs[typeKey(uriC)] = FolderType.id(FolderType.Author)
            prefs[nameKey(uriC)] = "C"
            prefs[typeKey(uriD)] = FolderType.id(FolderType.SingleFile)
            prefs[nameKey(uriD)] = "D"
        }
        val byName = store.observeRoots().first().associateBy { it.displayName }
        assertEquals(4, byName.size)
        assertTrue(byName.getValue("A").folderType is FolderType.Root)
        assertTrue(byName.getValue("B").folderType is FolderType.SingleFolder)
        assertTrue(byName.getValue("C").folderType is FolderType.Author)
        assertTrue(byName.getValue("D").folderType is FolderType.SingleFile)
    }

    @Test
    fun `removeRoot drops the entry from the DataStore set`() = scope.runTest {
        val uri = "content://provider/tree/A"
        dataStore.edit { prefs ->
            prefs[keyUris] = setOf(uri)
            prefs[typeKey(uri)] = FolderType.id(FolderType.Root)
            prefs[nameKey(uri)] = "A"
        }
        assertEquals(1, store.observeRoots().first().size)
        store.removeRoot(android.net.Uri.parse(uri))
        assertEquals(0, store.observeRoots().first().size)
    }

    @Test
    fun `an entry missing its FolderType is filtered out`() = scope.runTest {
        // Forward-compat / partial-write scenario — a URI in the set but no type means we
        // should drop it on read rather than crash.
        val uri = "content://provider/tree/orphan"
        dataStore.edit { prefs ->
            prefs[keyUris] = setOf(uri)
            // no type key, no name key
        }
        assertEquals(emptyList<LibraryRoot>(), store.observeRoots().first())
    }

    @Test
    fun `an unknown FolderType id is filtered out`() = scope.runTest {
        val uri = "content://provider/tree/A"
        dataStore.edit { prefs ->
            prefs[keyUris] = setOf(uri)
            prefs[typeKey(uri)] = "playlist" // not a known FolderType id
            prefs[nameKey(uri)] = "A"
        }
        assertEquals(emptyList<LibraryRoot>(), store.observeRoots().first())
    }
}
