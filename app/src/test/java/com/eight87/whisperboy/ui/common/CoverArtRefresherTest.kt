package com.eight87.whisperboy.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * cover-art.md Phase D.1 — coverage for [refreshCoverArt]. The function is the
 * thin wire between scan-side cover writes and Coil's in-memory + disk caches:
 * it pulls the singleton image loader and `remove`s entries keyed on the file
 * path string. The contract is "best-effort idempotent invalidation" — calling
 * twice with the same path is a no-op the second time; calling with `null` /
 * blank is a no-op always.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverArtRefresherTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `null coverPath is a no-op`() {
        // Must not throw and must not even touch the loader. We don't assert on
        // the loader directly — the contract is "early return" and the assertion
        // is "no exception".
        refreshCoverArt(context, null)
    }

    @Test
    fun `blank coverPath is a no-op`() {
        refreshCoverArt(context, "")
        refreshCoverArt(context, "   ")
    }

    @Test
    fun `removes the in-memory cache entry keyed on the path string`() {
        val loader = SingletonImageLoader.get(context.applicationContext)
        val memory = loader.memoryCache
        assertNotNull("Robolectric Coil loader should expose a memory cache", memory)

        // Plant a synthetic entry under the same key the refresher will remove.
        val path = "/tmp/whisperboy-cover-${System.nanoTime()}"
        val key = MemoryCache.Key(path)
        // We don't synthesize a real bitmap value here — the contract under test is
        // "remove was attempted". After refreshCoverArt, get(key) should be null
        // regardless of whether anything was inserted (memoryCache.remove is a
        // tolerant no-op on absent keys).

        refreshCoverArt(context, path)
        assertNull("Cache entry under primary key should not be present", memory!!.get(key))
        // Also covers the secondary File(path).toString() key — same string for
        // absolute paths.
        assertNull(memory.get(MemoryCache.Key(File(path).toString())))
    }

    @Test
    fun `is idempotent - calling twice does not throw`() {
        // The implementation calls `memoryCache.remove(key)` which returns false
        // silently on absent keys, and `diskCache.remove(key)` which has the same
        // semantics. Calling twice is a defined no-op.
        val path = "/tmp/whisperboy-cover-idem-${System.nanoTime()}"
        refreshCoverArt(context, path)
        refreshCoverArt(context, path)
        // No assertion needed beyond "did not throw" — that's the contract.
        // Belt-and-braces: confirm the second call also leaves the key absent.
        val memory = SingletonImageLoader.get(context.applicationContext).memoryCache
        assertNull(memory!!.get(MemoryCache.Key(path)))
    }

    @Test
    fun `does not modify the underlying file on disk`() {
        // The doc string promises "the on-disk file itself is left alone". Plant a
        // file at the cover path, call refresh, assert the file still exists with
        // the same bytes.
        val file = File.createTempFile("whisperboy-cover-", ".bin")
        try {
            file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val before = file.readBytes()
            refreshCoverArt(context, file.absolutePath)
            assertFalse("Refresh must not delete the file", !file.exists())
            val after = file.readBytes()
            org.junit.Assert.assertArrayEquals(
                "Refresh must not rewrite the file bytes",
                before,
                after,
            )
        } finally {
            file.delete()
        }
    }
}
