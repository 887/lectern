package com.eight87.whisperboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.eight87.whisperboy.data.library.AndroidLibraryRescanCoordinator
import com.eight87.whisperboy.data.library.AndroidLibraryUiSettings
import com.eight87.whisperboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.whisperboy.data.library.LibraryUiSettings
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.BookmarkSource
import com.eight87.whisperboy.data.library.ChapterSource
import com.eight87.whisperboy.data.library.CoverStore
import com.eight87.whisperboy.data.library.LibraryDatabase
import com.eight87.whisperboy.data.library.LibraryRepository
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.LibraryScanner
import com.eight87.whisperboy.data.library.LibraryScannerEnrichment
import com.eight87.whisperboy.data.library.Media3MediaAnalyzer
import com.eight87.whisperboy.data.library.MediaAnalyzer
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.data.library.SafLibraryScanner
import com.eight87.whisperboy.data.library.ScanWriter
import com.eight87.whisperboy.playback.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root. The only place in the app that knows concrete types for long-lived singletons.
 *
 * Created once in [WhisperboyApplication.onCreate]; ViewModels / composables / services receive
 * narrow interfaces from here, never the AppGraph itself.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val playerHolder: PlayerHolder = PlayerHolder(appContext)

    private val libraryRootsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("library_roots") }
    )

    val persistedUriPermissionStore: PersistedUriPermissionStore =
        AndroidPersistedUriPermissionStore(appContext, libraryRootsDataStore)

    /**
     * Phase E.3 follow-up — persisted library-screen UI prefs (grid mode / sort key / filter).
     * Backed by its own `library_ui` Preferences file so it stays decoupled from the
     * `library_roots` store above; deleting one (e.g. on a settings reset) doesn't nuke the other.
     */
    private val libraryUiDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("library_ui") }
    )

    val libraryUiSettings: LibraryUiSettings =
        AndroidLibraryUiSettings(libraryUiDataStore)

    /**
     * Library cache database. Eagerly constructed so a misconfigured schema fails fast at app
     * start rather than on first scan. Phase D.4's `LibraryRepository` wraps the DAOs behind
     * the narrow `BookSource` / `ChapterSource` / `BookmarkSource` interfaces.
     */
    val libraryDatabase: LibraryDatabase = Room.databaseBuilder(
        appContext,
        LibraryDatabase::class.java,
        "library.db",
    ).build()

    /**
     * Phase D.2's SAF tree walker. Composables / future settings / Phase D.5 rescan triggers
     * call this via the [LibraryScanner] interface, never the concrete impl. Phase D.4's
     * `LibraryRepository` will own the scan→write pipeline.
     */
    val libraryScanner: LibraryScanner = SafLibraryScanner(appContext)

    /**
     * Phase D.3's per-file metadata extractor. Exposed via the narrow [MediaAnalyzer] interface
     * (R.A pattern); the concrete `Media3MediaAnalyzer` stays internal.
     */
    val mediaAnalyzer: MediaAnalyzer = Media3MediaAnalyzer(appContext)

    /**
     * Phase D.3's enrichment pass. Glues D.2's structural [com.eight87.whisperboy.data.library.ScanSnapshot]
     * to per-file metadata (durations / titles / authors / cumulative positions). Phase D.4's
     * `applyScan` will consume the enriched output.
     */
    val libraryScannerEnrichment: LibraryScannerEnrichment = LibraryScannerEnrichment(
        mediaAnalyzer = mediaAnalyzer,
    )

    /** Phase D.4 — atomic cover-bytes-to-disk store at `<filesDir>/covers/<bookId>`. */
    val coverStore: CoverStore = CoverStore(appContext)

    /**
     * Phase D.4's [LibraryRepository] — the concrete implementation behind every narrow data
     * interface in `data/library/`. Held privately; only the narrow handles below are
     * exposed to the rest of the app, per R.A.2.
     */
    private val libraryRepository: LibraryRepository = LibraryRepository(
        database = libraryDatabase,
        coverStore = coverStore,
    )

    /** Read-only book catalog (active books only). Composables consume this, not the repo. */
    val bookSource: BookSource = libraryRepository

    /** Read-only chapter access scoped to a book. */
    val chapterSource: ChapterSource = libraryRepository

    /** Bookmark CRUD scoped to a book. */
    val bookmarkSource: BookmarkSource = libraryRepository

    /**
     * Write side of the scan pipeline. Phase D.5's rescan triggers and Phase E's
     * library-loaded entrypoint call this; nothing else. Cover writes happen inside the
     * implementation, atomically before the Room transaction commits the rows.
     */
    val scanWriter: ScanWriter = libraryRepository

    /**
     * Application-scoped coroutine scope for long-lived collectors (Phase D.5's rescan
     * coordinator; future Phase F's playback session listener; etc.). `SupervisorJob` so
     * one collector's failure doesn't cancel its siblings; `Dispatchers.IO` because every
     * known consumer is IO-bound (SAF reads, Room writes, file IO). Composables use
     * `viewModelScope` / `rememberCoroutineScope` instead.
     */
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Phase D.5 — coordinates rescan triggers. Three trigger paths wire in inside the
     * concrete impl: manual, foreground (debounced 30s via `ProcessLifecycleOwner`), and
     * root-set-change (driven by `persistedUriPermissionStore.observeRoots()`). Composables
     * see only the narrow [LibraryRescanCoordinator] interface — `requestRescan()` plus the
     * `state: StateFlow<RescanState>`.
     */
    val libraryRescanCoordinator: LibraryRescanCoordinator = AndroidLibraryRescanCoordinator(
        persistedUriPermissionStore = persistedUriPermissionStore,
        libraryScanner = libraryScanner,
        libraryScannerEnrichment = libraryScannerEnrichment,
        scanWriter = scanWriter,
        applicationScope = applicationScope,
    )

    fun release() {
        playerHolder.release()
    }
}
