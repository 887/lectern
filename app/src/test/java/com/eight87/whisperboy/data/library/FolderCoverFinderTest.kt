package com.eight87.whisperboy.data.library

import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Tests for [FolderCoverFinder] against a real on-disk fixture wrapped in
 * `DocumentFile.fromFile`. SAF's `RawDocumentFile` honours `listFiles` / `isFile` / `name` /
 * `length` against the underlying [File] tree, which is everything [CachedDocumentFile]
 * caches — so the cover-finder logic exercises end-to-end without a content-provider shadow.
 *
 * Match rule per impl: case-insensitive filename match against a fixed set
 * (`cover.{jpg,jpeg,png,webp}` / `folder.{jpg,jpeg,png,webp}` / `albumart.{jpg,png}`).
 * First match in `children` iteration order wins — the impl deliberately does NOT prefer
 * `cover.*` over `folder.*`; the user's filesystem ordering decides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderCoverFinderTest {

    private lateinit var tempDir: File
    private val finder = FolderCoverFinder()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        tempDir = File(context.cacheDir, "folder-cover-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun cached(): CachedDocumentFile =
        CachedDocumentFile(DocumentFile.fromFile(tempDir))

    private fun touch(name: String, bytes: ByteArray = byteArrayOf(0)): File =
        File(tempDir, name).apply { writeBytes(bytes) }

    @Test
    fun `cover-jpg sidecar is found`() {
        touch("cover.jpg", "JPEG".toByteArray())
        touch("01.mp3")
        val cover = finder.findCover(cached())
        assertNotNull(cover)
        assertEquals("cover.jpg", cover!!.name)
    }

    @Test
    fun `folder-png sidecar is found when no cover-star is present`() {
        touch("folder.png")
        touch("01.mp3")
        val cover = finder.findCover(cached())
        assertNotNull(cover)
        assertEquals("folder.png", cover!!.name)
    }

    @Test
    fun `albumart-jpg sidecar is found`() {
        touch("albumart.jpg")
        touch("01.mp3")
        val cover = finder.findCover(cached())
        assertNotNull(cover)
        assertEquals("albumart.jpg", cover!!.name)
    }

    @Test
    fun `match is case-insensitive on filename`() {
        touch("Cover.JPG")
        touch("01.mp3")
        val cover = finder.findCover(cached())
        assertNotNull(cover)
        assertEquals("Cover.JPG", cover!!.name)
    }

    @Test
    fun `no recognised sidecar returns null`() {
        touch("artwork.gif") // not in the allow-list
        touch("readme.txt")
        touch("01.mp3")
        assertNull(finder.findCover(cached()))
    }

    @Test
    fun `empty folder returns null`() {
        assertNull(finder.findCover(cached()))
    }

    @Test
    fun `subdirectory named cover-jpg is ignored — files only`() {
        File(tempDir, "cover.jpg").mkdirs()
        touch("01.mp3")
        // A *directory* named cover.jpg fails the isFile guard inside the finder.
        assertNull(finder.findCover(cached()))
    }
}
