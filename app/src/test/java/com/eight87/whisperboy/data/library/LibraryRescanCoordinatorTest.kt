package com.eight87.whisperboy.data.library

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.eight87.whisperboy.data.library.parser.ChapterParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robolectric-backed tests for [AndroidLibraryRescanCoordinator].
 *
 * Verifies the state-machine plumbing — `Idle` ↔ `Running`, conflated-channel coalescing,
 * and the P.4 `health` StateFlow surface. The real coordinator's read-side scan path
 * (`SafLibraryScanner.computeFingerprint` + `DocumentFile.fromTreeUri`) jumps off the
 * TestDispatcher via `withContext(Dispatchers.IO)`, so `advanceUntilIdle()` cannot wait
 * for the full classification cycle deterministically. Tests here therefore exercise the
 * paths that stay on the test dispatcher: empty-roots scans, idempotent state transitions,
 * conflation. End-to-end health-population coverage lives in `scripts/library-smoke-test.sh`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRescanCoordinatorTest {

    private lateinit var dispatcher: TestDispatcher
    private lateinit var scope: TestScope
    private lateinit var appScope: CoroutineScope
    private lateinit var rootsStore: FakePersistedUriPermissionStore
    private lateinit var fakeScanner: FakeLibraryScanner
    private lateinit var fingerprintStore: InMemoryFingerprintStore
    private lateinit var scanWriter: RecordingScanWriter
    private lateinit var enrichment: LibraryScannerEnrichment
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var safScanner: SafLibraryScanner

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        appScope = CoroutineScope(dispatcher + Job())
        rootsStore = FakePersistedUriPermissionStore()
        fakeScanner = FakeLibraryScanner()
        fingerprintStore = InMemoryFingerprintStore()
        scanWriter = RecordingScanWriter()
        enrichment = LibraryScannerEnrichment(
            mediaAnalyzer = NoopMediaAnalyzer,
            chapterParser = null as ChapterParser?,
            coverExtractorDispatcher = null,
        )
        val ownerHolder = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        lifecycleRegistry = ownerHolder.registry
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        safScanner = SafLibraryScanner(context)
    }

    @After
    fun tearDown() {
        appScope.cancel()
    }

    private fun newCoordinator(): AndroidLibraryRescanCoordinator {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AndroidLibraryRescanCoordinator(
            context = context,
            persistedUriPermissionStore = rootsStore,
            libraryScanner = fakeScanner,
            libraryScannerEnrichment = enrichment,
            scanWriter = scanWriter,
            fingerprintStore = fingerprintStore,
            safLibraryScanner = safScanner,
            applicationScope = appScope,
            lifecycle = lifecycleRegistry,
            foregroundDebounceMs = 30_000L,
            clock = { 0L },
        )
    }

    @Test
    fun `initial state is Idle`() = scope.runTest {
        val coordinator = newCoordinator()
        assertEquals(RescanState.Idle, coordinator.state.value)
    }

    @Test
    fun `health StateFlow starts empty`() = scope.runTest {
        val coordinator = newCoordinator()
        assertEquals(LibraryHealth(), coordinator.health.value)
        assertTrue(coordinator.health.value.unreadableRoots.isEmpty())
    }

    @Test
    fun `requestRescan with empty roots transitions through Running back to Idle`() = scope.runTest {
        val coordinator = newCoordinator()
        advanceUntilIdle()
        coordinator.requestRescan()
        advanceUntilIdle()
        assertEquals(RescanState.Idle, coordinator.state.value)
        // With no roots the underlying structural scanner is never invoked.
        assertEquals(0, fakeScanner.scanCallCount.get())
        assertEquals(0, scanWriter.applyScanCount)
    }

    @Test
    fun `requestRescan with no roots and force-true still no-ops cleanly`() = scope.runTest {
        val coordinator = newCoordinator()
        advanceUntilIdle()
        coordinator.requestRescan(force = true)
        advanceUntilIdle()
        assertEquals(RescanState.Idle, coordinator.state.value)
        assertEquals(0, scanWriter.applyScanCount)
        assertEquals(0, fingerprintStore.writeCount)
    }

    @Test
    fun `repeated requestRescan calls coalesce via conflated channel`() = scope.runTest {
        val coordinator = newCoordinator()
        advanceUntilIdle()
        // Fire a flurry — the conflated channel keeps at most the latest pending entry.
        repeat(10) { coordinator.requestRescan() }
        advanceUntilIdle()
        assertEquals(RescanState.Idle, coordinator.state.value)
        assertEquals(0, fakeScanner.scanCallCount.get())
    }

    @Test
    fun `requestRescan does not crash when state machine has been idle for a while`() = scope.runTest {
        val coordinator = newCoordinator()
        advanceUntilIdle()
        coordinator.requestRescan()
        advanceUntilIdle()
        coordinator.requestRescan()
        advanceUntilIdle()
        coordinator.requestRescan(force = true)
        advanceUntilIdle()
        assertEquals(RescanState.Idle, coordinator.state.value)
    }
}

// --- fakes ----------------------------------------------------------------

private class FakePersistedUriPermissionStore : PersistedUriPermissionStore {
    private val state = MutableStateFlow<List<LibraryRoot>>(emptyList())
    override fun observeRoots(): StateFlow<List<LibraryRoot>> = state
    override suspend fun addRoot(treeUri: Uri, folderType: FolderType) {
        state.value = state.value + LibraryRoot(treeUri, folderType, treeUri.toString())
    }
    override suspend fun removeRoot(treeUri: Uri) {
        state.value = state.value.filterNot { it.treeUri == treeUri }
    }
}

private class FakeLibraryScanner : LibraryScanner {
    val scanCallCount = AtomicInteger(0)
    var snapshot: ScanSnapshot = ScanSnapshot(emptyList())
    override suspend fun scan(roots: List<LibraryRoot>): ScanSnapshot {
        scanCallCount.incrementAndGet()
        return snapshot
    }
}

private class InMemoryFingerprintStore : LibraryFingerprintStore {
    private val map = mutableMapOf<String, LibraryFingerprint>()
    var writeCount: Int = 0
        private set
    override suspend fun get(treeUri: String): LibraryFingerprint? = map[treeUri]
    override suspend fun set(treeUri: String, fp: LibraryFingerprint) {
        map[treeUri] = fp
        writeCount += 1
    }
}

private class RecordingScanWriter : ScanWriter {
    var applyScanCount: Int = 0
        private set
    override suspend fun applyScan(snapshot: ScanSnapshot) {
        applyScanCount += 1
    }
}

private object NoopMediaAnalyzer : MediaAnalyzer {
    override suspend fun extract(fileUri: Uri): FileMetadata? = null
}
