package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Robolectric-backed round-trip tests for [AndroidLibraryFingerprintStore].
 *
 * Mirrors the pattern in `AndroidLibraryUiSettingsTest` — a transient
 * `DataStore<Preferences>` on a per-test tmp file under the shadow
 * `Application.filesDir`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryFingerprintStoreTest {

    private lateinit var scope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: AndroidLibraryFingerprintStore
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + Job())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storeFile = File(context.filesDir, "library_fp_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { storeFile },
        )
        store = AndroidLibraryFingerprintStore(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        storeFile.delete()
    }

    @Test
    fun `get returns null for an unseen tree URI`() = scope.runTest {
        assertNull(store.get("content://com.android.externalstorage.documents/tree/primary%3AAudiobooks"))
    }

    @Test
    fun `set then get round-trips a single fingerprint`() = scope.runTest {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks"
        val fp = LibraryFingerprint(documentCount = 42, maxMtime = 1_700_000_000_000L)
        store.set(uri, fp)
        assertEquals(fp, store.get(uri))
    }

    @Test
    fun `multiple URIs do not cross-contaminate`() = scope.runTest {
        val a = "content://provider/tree/A"
        val b = "content://provider/tree/B"
        val fpA = LibraryFingerprint(documentCount = 10, maxMtime = 111L)
        val fpB = LibraryFingerprint(documentCount = 99, maxMtime = 999L)

        store.set(a, fpA)
        store.set(b, fpB)

        assertEquals(fpA, store.get(a))
        assertEquals(fpB, store.get(b))
    }

    @Test
    fun `update overwrites in place rather than appending`() = scope.runTest {
        val uri = "content://provider/tree/A"
        store.set(uri, LibraryFingerprint(documentCount = 5, maxMtime = 100L))
        store.set(uri, LibraryFingerprint(documentCount = 7, maxMtime = 200L))

        val got = store.get(uri)
        assertEquals(LibraryFingerprint(documentCount = 7, maxMtime = 200L), got)
    }

    @Test
    fun `fingerprints survive a recreated store instance backed by the same file`() = scope.runTest {
        val uri = "content://provider/tree/A"
        val fp = LibraryFingerprint(documentCount = 3, maxMtime = 555L)
        store.set(uri, fp)

        val reloaded = AndroidLibraryFingerprintStore(dataStore)
        assertEquals(fp, reloaded.get(uri))
    }
}
