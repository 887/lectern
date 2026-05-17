package com.eight87.whisperboy.data.library

import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [CoverStore] — write atomicity is well-covered by the existing
 * scan integration path; the surface added here is the orphan-cover GC pass wired into
 * [AndroidLibraryRescanCoordinator] after a successful scan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverStoreTest {

    private lateinit var store: CoverStore
    private lateinit var coversDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = CoverStore(context)
        coversDir = File(context.filesDir, "covers")
    }

    @After
    fun tearDown() {
        coversDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `listBookIdsOnDisk returns the basenames of every cover written`() = runTest {
        store.writeCover("b1", byteArrayOf(1, 2, 3))
        store.writeCover("b2", byteArrayOf(4, 5, 6))
        store.writeCover("b3", byteArrayOf(7, 8, 9))
        assertEquals(setOf("b1", "b2", "b3"), store.listBookIdsOnDisk())
    }

    @Test
    fun `gcOrphans deletes covers whose basenames are not in the active set`() = runTest {
        store.writeCover("b1", byteArrayOf(1))
        store.writeCover("b2", byteArrayOf(2))
        store.writeCover("b3", byteArrayOf(3))

        val deleted = store.gcOrphans(activeBookIds = setOf("b1"))

        assertEquals(2, deleted)
        assertEquals(setOf("b1"), store.listBookIdsOnDisk())
        assertTrue(File(coversDir, "b1").exists())
        assertFalse(File(coversDir, "b2").exists())
        assertFalse(File(coversDir, "b3").exists())
    }

    @Test
    fun `gcOrphans with empty active set wipes every cover`() = runTest {
        store.writeCover("b1", byteArrayOf(1))
        store.writeCover("b2", byteArrayOf(2))
        val deleted = store.gcOrphans(activeBookIds = emptySet())
        assertEquals(2, deleted)
        assertTrue(store.listBookIdsOnDisk().isEmpty())
    }

    @Test
    fun `gcOrphans with all-active set deletes nothing`() = runTest {
        store.writeCover("b1", byteArrayOf(1))
        store.writeCover("b2", byteArrayOf(2))
        val deleted = store.gcOrphans(activeBookIds = setOf("b1", "b2"))
        assertEquals(0, deleted)
        assertEquals(setOf("b1", "b2"), store.listBookIdsOnDisk())
    }

    @Test
    fun `gcOrphans reaps stray tmp files left by a crashed writeCover`() = runTest {
        store.writeCover("b1", byteArrayOf(1))
        // Simulate a tmp file left behind by a process that died between writeBytes and rename.
        File(coversDir, "b2.tmp").writeBytes(byteArrayOf(2))
        assertTrue(File(coversDir, "b2.tmp").exists())

        val deleted = store.gcOrphans(activeBookIds = setOf("b1"))

        assertEquals(1, deleted)
        assertFalse(File(coversDir, "b2.tmp").exists())
        assertTrue(File(coversDir, "b1").exists())
    }

    @Test
    fun `listBookIdsOnDisk excludes tmp files`() = runTest {
        store.writeCover("b1", byteArrayOf(1))
        File(coversDir, "b2.tmp").writeBytes(byteArrayOf(2))
        assertEquals(setOf("b1"), store.listBookIdsOnDisk())
    }
}
