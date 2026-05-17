package com.eight87.whisperboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.eight87.whisperboy.data.coverart.CoverApi
import com.eight87.whisperboy.data.coverart.CoverApiModule
import com.eight87.whisperboy.data.library.AndroidLibraryFingerprintStore
import com.eight87.whisperboy.data.library.AndroidLibraryRescanCoordinator
import com.eight87.whisperboy.data.library.AndroidLibraryScanFilterSettings
import com.eight87.whisperboy.data.library.AndroidLibraryUiSettings
import com.eight87.whisperboy.data.library.LibraryScanFilterSettings
import com.eight87.whisperboy.data.library.LibraryFingerprintStore
import com.eight87.whisperboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.whisperboy.data.library.LibraryUiSettings
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.BookmarkSource
import com.eight87.whisperboy.data.library.ChapterSource
import com.eight87.whisperboy.data.library.CoverStore
import com.eight87.whisperboy.data.library.LibraryDatabase
import com.eight87.whisperboy.data.library.LibraryRepository
import com.eight87.whisperboy.data.library.MIGRATION_1_2
import com.eight87.whisperboy.data.library.MIGRATION_2_3
import com.eight87.whisperboy.data.library.MIGRATION_3_4
import com.eight87.whisperboy.data.library.MIGRATION_4_5
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.LibraryScanner
import com.eight87.whisperboy.data.library.LibraryScannerEnrichment
import com.eight87.whisperboy.data.library.Media3MediaAnalyzer
import com.eight87.whisperboy.data.library.MediaAnalyzer
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.data.library.SafLibraryScanner
import com.eight87.whisperboy.data.library.ScanWriter
import com.eight87.whisperboy.data.library.parser.ChapterParser
import com.eight87.whisperboy.data.library.parser.CoverExtractorDispatcher
import com.eight87.whisperboy.data.onboarding.AndroidOnboardingSettings
import com.eight87.whisperboy.data.onboarding.OnboardingSettings
import com.eight87.whisperboy.data.playback.AndroidPlaybackSettings
import com.eight87.whisperboy.data.playback.AndroidSleepTimerSettings
import com.eight87.whisperboy.data.playback.PlaybackSettings
import com.eight87.whisperboy.data.playback.SleepTimerSettings
import com.eight87.whisperboy.data.theme.AndroidThemeSettings
import com.eight87.whisperboy.data.theme.ThemeSettings
import com.eight87.whisperboy.playback.AndroidSleepTimer
import com.eight87.whisperboy.playback.BookCommands
import com.eight87.whisperboy.playback.NowPlayingState
import com.eight87.whisperboy.playback.PlaybackController
import com.eight87.whisperboy.playback.PlayerHolder
import com.eight87.whisperboy.playback.SleepTimerCommands
import com.eight87.whisperboy.playback.TransportCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Composition root. The only place in the app that knows concrete types for long-lived singletons.
 *
 * Created once in [WhisperboyApplication.onCreate]; ViewModels / composables / services receive
 * narrow interfaces from here, never the AppGraph itself.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val playerHolder: PlayerHolder = PlayerHolder(appContext)

    /**
     * Create a Preferences DataStore for [name], with a [ReplaceFileCorruptionHandler]
     * that resets the file to [emptyPreferences] on `CorruptionException`. Without this,
     * a corrupted prefs file throws `IOException` through every `data.map { … }` Flow
     * collector and ultimately into Compose recomposition → crash. We trade
     * "user loses settings for that one facet" for "app stays alive" — same
     * call Voice makes on every facet. DRYs up the eight DataStore sites below.
     */
    private fun createPrefs(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile(name) },
        )

    private val libraryRootsDataStore: DataStore<Preferences> = createPrefs("library_roots")

    val persistedUriPermissionStore: PersistedUriPermissionStore =
        AndroidPersistedUriPermissionStore(appContext, libraryRootsDataStore)

    /**
     * Phase E.3 follow-up — persisted library-screen UI prefs (grid mode / sort key / filter).
     * Backed by its own `library_ui` Preferences file so it stays decoupled from the
     * `library_roots` store above; deleting one (e.g. on a settings reset) doesn't nuke the other.
     */
    private val libraryUiDataStore: DataStore<Preferences> = createPrefs("library_ui")

    val libraryUiSettings: LibraryUiSettings =
        AndroidLibraryUiSettings(libraryUiDataStore)

    /**
     * Phase K.4 sub-screen — persisted scan-filter prefs (set of DISABLED audio extensions).
     * Own `scan_filters` DataStore-Preferences file per R.B store-split.
     */
    private val scanFiltersDataStore: DataStore<Preferences> = createPrefs("scan_filters")

    val libraryScanFilterSettings: LibraryScanFilterSettings =
        AndroidLibraryScanFilterSettings(scanFiltersDataStore)

    /**
     * Phase F.3 + F.4 — persisted player tunables (rewind / forward / auto-rewind seconds). Its
     * own DataStore file (`playback_settings`) so a future "reset playback prefs" affordance can
     * delete this without touching the library UI store. R.B (store split) pattern.
     */
    private val playbackSettingsDataStore: DataStore<Preferences> = createPrefs("playback_settings")

    val playbackSettings: PlaybackSettings =
        AndroidPlaybackSettings(playbackSettingsDataStore)

    /**
     * Phase K.5 — persisted theme preferences (mode + dynamic-color). Own
     * `theme_settings` DataStore file so a future "reset theme" affordance
     * can delete this alone without touching library / playback prefs
     * (R.B store-split pattern).
     */
    private val themeSettingsDataStore: DataStore<Preferences> = createPrefs("theme_settings")

    val themeSettings: ThemeSettings =
        AndroidThemeSettings(themeSettingsDataStore)

    /**
     * Phase L — persisted "onboarding completed" flag. Own DataStore file
     * (`onboarding`) so a future "reset onboarding" debug affordance can `clear()`
     * this without touching any of the other persisted prefs.
     */
    private val onboardingDataStore: DataStore<Preferences> = createPrefs("onboarding")

    val onboardingSettings: OnboardingSettings =
        AndroidOnboardingSettings(onboardingDataStore)

    /**
     * Phase G — sleep-timer-specific tunables (default duration, fade-out, shake-to-resume,
     * auto-arm window). Own DataStore file so a future "reset sleep timer" affordance in K.3
     * can delete this without touching `playback_settings` (R.B store-split pattern).
     */
    private val sleepTimerSettingsDataStore: DataStore<Preferences> = createPrefs("sleep_timer_settings")

    val sleepTimerSettings: SleepTimerSettings =
        AndroidSleepTimerSettings(sleepTimerSettingsDataStore)

    /**
     * Library cache database. Eagerly constructed so a misconfigured schema fails fast at app
     * start rather than on first scan. Phase D.4's `LibraryRepository` wraps the DAOs behind
     * the narrow `BookSource` / `ChapterSource` / `BookmarkSource` interfaces.
     */
    val libraryDatabase: LibraryDatabase = Room.databaseBuilder(
        appContext,
        LibraryDatabase::class.java,
        "library.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()

    /**
     * Phase D.2's SAF tree walker. Composables / future settings / Phase D.5 rescan triggers
     * call this via the [LibraryScanner] interface, never the concrete impl. Phase D.4's
     * `LibraryRepository` will own the scan→write pipeline.
     */
    /**
     * Phase K.4 sub-screen — cached snapshot of the user's disabled-extensions set, kept up
     * to date by a collector on [applicationScope]. The scanner's per-scan `() -> Set<String>`
     * provider reads this `@Volatile` field rather than blocking on the Flow, so the scan
     * dispatcher coroutine stays IO-bound.
     */
    @Volatile
    private var disabledExtensionsSnapshot: Set<String> = emptySet()

    val libraryScanner: LibraryScanner = SafLibraryScanner(
        context = appContext,
        disabledExtensionsProvider = { disabledExtensionsSnapshot },
    )

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
    /**
     * Phase I.7 — embedded-chapter parser. Routes M4B / Matroska / Vorbis URIs to the right
     * parser. Returned from the singleton graph so [LibraryScannerEnrichment] can call into
     * it during the per-book enrichment pass without re-allocating.
     */
    val chapterParser: ChapterParser = ChapterParser(appContext)

    /**
     * Cover-art Phase A.3 — container-aware embedded cover extractors (MP4 `covr`, Matroska
     * `AttachedFile`, MP3 `APIC`). Used by [LibraryScannerEnrichment] as a fallback when
     * Media3's [MediaAnalyzer] / [android.media.MediaMetadataRetriever] returns no embedded
     * cover for any of a book's first 5 chapter files.
     */
    val coverExtractorDispatcher: CoverExtractorDispatcher = CoverExtractorDispatcher(appContext)

    val libraryScannerEnrichment: LibraryScannerEnrichment = LibraryScannerEnrichment(
        mediaAnalyzer = mediaAnalyzer,
        chapterParser = chapterParser,
        coverExtractorDispatcher = coverExtractorDispatcher,
    )

    /** Phase D.4 — atomic cover-bytes-to-disk store at `<filesDir>/covers/<bookId>`. */
    val coverStore: CoverStore = CoverStore(appContext)

    /**
     * cover-art Phase B — shared OkHttp client for DuckDuckGo image search + full-image
     * downloads. Exposed because [com.eight87.whisperboy.ui.coverart.SelectCoverFromInternet]
     * uses it directly to fetch the full-resolution image bytes after the user picks a
     * thumbnail (Retrofit only handles the JSON page; bytes come straight from OkHttp).
     */
    val okHttpClient: okhttp3.OkHttpClient = CoverApiModule.provideOkHttpClient()

    /** cover-art Phase B — narrow Retrofit-backed DuckDuckGo image-search client. */
    val coverApi: CoverApi = CoverApiModule.provideCoverApi(okHttpClient)

    /**
     * Phase D.4's [LibraryRepository] — the concrete implementation behind every narrow data
     * interface in `data/library/`. Held privately; only the narrow handles below are
     * exposed to the rest of the app, per R.A.2.
     */
    private val libraryRepository: LibraryRepository = LibraryRepository(
        database = libraryDatabase,
        coverStore = coverStore,
        playbackSettings = playbackSettings,
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
    /**
     * Phase P.8 — per-root SAF fingerprint cache. Own DataStore file
     * (`library_fingerprints`) so a future "Reset fingerprints" debug
     * affordance can `clear()` this without touching any other prefs.
     */
    private val libraryFingerprintsDataStore: DataStore<Preferences> = createPrefs("library_fingerprints")

    val libraryFingerprintStore: LibraryFingerprintStore =
        AndroidLibraryFingerprintStore(libraryFingerprintsDataStore)

    /**
     * The concrete [SafLibraryScanner] above is exposed via the [LibraryScanner] interface for
     * the standard scan path, but the coordinator also needs the concrete type for the P.8
     * `computeFingerprint` probe. Smart-casting the interface would couple consumers, so we
     * pass the concrete reference explicitly here.
     */
    private val safLibraryScannerConcrete: SafLibraryScanner = libraryScanner as SafLibraryScanner

    val libraryRescanCoordinator: LibraryRescanCoordinator = AndroidLibraryRescanCoordinator(
        context = appContext,
        persistedUriPermissionStore = persistedUriPermissionStore,
        libraryScanner = libraryScanner,
        libraryScannerEnrichment = libraryScannerEnrichment,
        scanWriter = scanWriter,
        fingerprintStore = libraryFingerprintStore,
        safLibraryScanner = safLibraryScannerConcrete,
        applicationScope = applicationScope,
        bookSource = bookSource,
        coverStore = coverStore,
    )

    /**
     * Phase F.2 — UI-side wrapper around Media3's `MediaController`. Connects asynchronously to
     * the running `PlaybackService` on first construction; exposes the four narrow interfaces
     * (R.A.2 + R.C.1) the player surface consumes. The concrete class stays `internal`.
     */
    private val playbackController: PlaybackController = PlaybackController(
        context = appContext,
        bookSource = bookSource,
        chapterSource = chapterSource,
        playbackSettings = playbackSettings,
        applicationScope = applicationScope,
    )

    val nowPlayingState: NowPlayingState = playbackController
    val transportCommands: TransportCommands = playbackController
    val bookCommands: BookCommands = playbackController

    /**
     * Phase G — sleep timer service. Takes the narrow [com.eight87.whisperboy.playback.PlayerHandle]
     * (volume / pause / position reads) implemented by [PlaybackController], plus the
     * [BookmarkSource] (auto-bookmark on fire, G.5) and [SleepTimerSettings] (G.1 + G.6 knobs).
     * Concrete class stays `internal`; module-external code sees only [SleepTimerCommands].
     */
    private val androidSleepTimer: AndroidSleepTimer = AndroidSleepTimer(
        context = appContext,
        playerHandle = playbackController,
        bookmarkSource = bookmarkSource,
        sleepTimerSettings = sleepTimerSettings,
        applicationScope = applicationScope,
    )

    val sleepTimerCommands: SleepTimerCommands = androidSleepTimer

    init {
        // Phase K.4 sub-screen — keep [disabledExtensionsSnapshot] up to date so the
        // scanner's `() -> Set<String>` provider has a fresh value on every scan without
        // having to block on the Flow. Single collector on [applicationScope].
        applicationScope.launch {
            libraryScanFilterSettings.disabledExtensions.collect { disabled ->
                disabledExtensionsSnapshot = disabled
            }
        }
    }

    /**
     * Phase P.7 — flush hook for [com.eight87.whisperboy.playback.PlaybackService.onDestroy].
     * The service doesn't hold a direct reference to [PlaybackController]; it reaches through
     * the graph to ask for a position save before the underlying session releases. Cheap
     * (single UPDATE row), no-op when nothing is playing.
     */
    fun flushPlaybackPosition() {
        playbackController.saveCurrentPosition()
    }

    fun release() {
        playbackController.release()
        playerHolder.release()
    }
}
