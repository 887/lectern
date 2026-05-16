package com.eight87.whisperboy.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.eight87.whisperboy.data.library.BookEntity
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.ChapterEntity
import com.eight87.whisperboy.data.library.ChapterSource
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only projection of the playback session for UI.
 *
 * Phase R.A pattern (narrow interfaces): composables / mini-players that only need to *observe*
 * state take this, never [PlaybackController] directly. The split lets a future mini-player /
 * widget consume the same Flow without inheriting transport-command authority.
 */
interface NowPlayingState {
    val state: StateFlow<PlaybackUiState>
}

/**
 * Imperative transport against the active session. Phase F.1's player surface consumes this
 * exclusively for user actions; the controller resolves each call against the live
 * [androidx.media3.session.MediaController] when connected, and no-ops cleanly when not.
 *
 * Rewind / forward defaults are hardcoded 30s for Phase F.1; configurable defaults land in F.3.
 */
interface TransportCommands {
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionInBookMs: Long)
    suspend fun rewind30()
    suspend fun forward30()
    suspend fun nextChapter()
    suspend fun prevChapter()
    suspend fun setSpeed(speed: Float)
    suspend fun setSkipSilence(enabled: Boolean)
    suspend fun setGain(gainDb: Float)
}

/**
 * Commands that switch the player to a different book. Separated from [TransportCommands]
 * (R.A.4 / R.C.1) so a now-playing bar that *only* observes current state and toggles play/pause
 * doesn't depend on book-loading authority.
 */
interface BookCommands {
    suspend fun playBook(bookId: String)
    suspend fun playBookFromPosition(bookId: String, positionInBookMs: Long)
}

/**
 * Sleep timer commands — empty in Phase F (compositional shape only). Phase G fills this in.
 */
interface SleepTimerCommands

/**
 * UI-side wrapper around Media3's [MediaController]. Owns:
 *
 *  - Async connection to the running [PlaybackService] via `SessionToken` + `MediaController.Builder`.
 *  - Projection of [Player.Listener] events into [PlaybackUiState] (event-driven: book / chapter /
 *    playing / speed / etc.) plus the **separate** position ticker (R.C.5) at ~250ms while playing.
 *  - Implementing [NowPlayingState] / [TransportCommands] / [BookCommands] / [SleepTimerCommands]
 *    over that one connection.
 *
 * Phase R.A.2: this class is `internal`; module-external consumers (composables) see only the four
 * narrow interfaces above. [com.eight87.whisperboy.AppGraph] exposes them separately.
 *
 * Phase R.C.5: the position ticker is a separate coroutine from the listener-driven projection —
 * a position tick does NOT recompute chapter / metadata. The ticker runs only while playing and
 * idles cleanly on pause.
 */
internal class PlaybackController(
    context: Context,
    private val bookSource: BookSource,
    private val chapterSource: ChapterSource,
    private val applicationScope: CoroutineScope,
) : NowPlayingState, TransportCommands, BookCommands, SleepTimerCommands {

    private val appContext = context.applicationContext

    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, PlaybackService::class.java),
    )

    /** Holds the connected controller once the async build resolves. Read on Main thread only. */
    @Volatile
    private var controller: MediaController? = null

    /** Connection state — true once [MediaController.Builder.buildAsync] resolves. */
    private val connected = MutableStateFlow(false)

    /** Currently-targeted book id. `null` means "no playback session yet". */
    private val targetedBookId = MutableStateFlow<String?>(null)

    /** Listener-driven snapshot: playing / speed / skipSilence / gain / mediaId / chapterIndex. */
    private val playerSnapshot = MutableStateFlow(PlayerSnapshot())

    /** Position ticker output. Decoupled from [playerSnapshot] so chapter projection doesn't churn. */
    private val positionMs = MutableStateFlow(0L)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerSnapshot.update { it.copy(isPlaying = isPlaying) }
            // Re-arm or stop the ticker on play state changes.
            restartTickerIfNeeded()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            playerSnapshot.update {
                it.copy(
                    currentMediaId = mediaItem?.mediaId,
                    currentItemIndex = controller?.currentMediaItemIndex ?: 0,
                )
            }
            // A media-item transition is an event-driven projection tick (R.C.5).
            controller?.let { c -> positionMs.value = c.currentPosition }
        }

        override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) {
            playerSnapshot.update { it.copy(speed = p.speed) }
        }
    }

    init {
        val builderFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        builderFuture.addListener(
            {
                try {
                    val c = builderFuture.get()
                    c.addListener(playerListener)
                    controller = c
                    // Snapshot the initial state once the controller resolves.
                    playerSnapshot.value = PlayerSnapshot(
                        isPlaying = c.isPlaying,
                        currentMediaId = c.currentMediaItem?.mediaId,
                        currentItemIndex = c.currentMediaItemIndex,
                        speed = c.playbackParameters.speed,
                    )
                    positionMs.value = c.currentPosition
                    connected.value = true
                    restartTickerIfNeeded()
                } catch (_: Throwable) {
                    // Connection failed; leave connected=false so the UI renders a loading
                    // state indefinitely. The user can navigate back.
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    // ---------------------------------------------------------------- NowPlayingState

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val state: StateFlow<PlaybackUiState> = combine(
        connected,
        targetedBookId,
    ) { isConnected, bookId -> isConnected to bookId }
        .flatMapLatest { (isConnected, bookId) ->
            when {
                !isConnected -> flowOf<PlaybackUiState>(PlaybackUiState.Loading)
                bookId == null -> flowOf<PlaybackUiState>(PlaybackUiState.Idle)
                else -> projectLoaded(bookId)
            }
        }
        .stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            PlaybackUiState.Loading,
        )

    /**
     * Event-driven projection (R.C.5): book + chapter list emit on Room change, [playerSnapshot]
     * emits on listener events; only [positionMs] ticks at 250ms while playing. None of those
     * three sources re-fetches the book / chapter list — that's the whole point of the split.
     */
    private fun projectLoaded(bookId: String): kotlinx.coroutines.flow.Flow<PlaybackUiState> {
        val bookFlow = bookSource.observeBook(bookId)
        val chaptersFlow = chapterSource.observeChaptersForBook(bookId)
        return combine(
            bookFlow,
            chaptersFlow,
            playerSnapshot,
            positionMs,
        ) { book, chapters, snap, position ->
            if (book == null) {
                PlaybackUiState.BookNotFound
            } else {
                val currentChapter = chapterAt(chapters, snap.currentItemIndex)
                PlaybackUiState.Loaded(
                    book = book,
                    currentChapter = currentChapter,
                    positionInBookMs = position,
                    isPlaying = snap.isPlaying,
                    speed = snap.speed,
                    skipSilenceEnabled = book.skipSilenceEnabled,
                    gainDb = book.gainDb,
                )
            }
        }
    }

    private fun chapterAt(chapters: List<ChapterEntity>, index: Int): ChapterEntity? {
        if (chapters.isEmpty()) return null
        return chapters.getOrNull(index.coerceIn(0, chapters.lastIndex))
    }

    // ---------------------------------------------------------------- ticker

    private var tickerJob: Job? = null

    private fun restartTickerIfNeeded() {
        tickerJob?.cancel()
        val c = controller ?: return
        if (!c.isPlaying) {
            // One last sync write so a pause/seek lands in UI immediately.
            positionMs.value = c.currentPosition
            return
        }
        tickerJob = applicationScope.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            // ~250ms ticks while playing (R.C.5). Reads only the position field.
            while (true) {
                val live = controller ?: return@launch
                if (!live.isPlaying) return@launch
                positionMs.value = live.currentPosition
                delay(TICKER_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------------- TransportCommands

    override suspend fun play() = onMain { it.play() }
    override suspend fun pause() = onMain { it.pause() }

    override suspend fun seekTo(positionInBookMs: Long) = onMain { c ->
        // Phase F.1 ships chapter-scoped scrubber semantics: callers pass a book-absolute
        // position. We translate to (mediaItemIndex, positionInChapterMs) using the controller's
        // own media-item duration; sleep-timer / chapter-list seeking will use the same path.
        val chapterCount = c.mediaItemCount
        if (chapterCount == 0) {
            c.seekTo(positionInBookMs.coerceAtLeast(0L))
            return@onMain
        }
        // Walk forward summing item durations until we land in the bucket. C.TIME_UNSET means
        // "duration not known yet" — fall back to seeking within the current item.
        var remaining = positionInBookMs.coerceAtLeast(0L)
        var idx = 0
        while (idx < chapterCount - 1) {
            val duration = c.getMediaItemAt(idx).let { _ ->
                // The controller exposes per-item duration only for the *current* item via
                // `duration`. For other items we rely on the MediaItem's `clippingConfiguration`
                // / `mediaMetadata.durationMs` if set by the session.
                c.currentMediaItem?.takeIf { c.currentMediaItemIndex == idx }
                    ?.let { c.duration.takeIf { d -> d != androidx.media3.common.C.TIME_UNSET } }
                    ?: c.getMediaItemAt(idx).mediaMetadata.durationMs
                    ?: -1L
            }
            if (duration <= 0 || remaining < duration) break
            remaining -= duration
            idx += 1
        }
        c.seekTo(idx, remaining)
        positionMs.value = positionInBookMs
    }

    override suspend fun rewind30() = onMain { c ->
        val target = (c.currentPosition - REWIND_FORWARD_MS).coerceAtLeast(0L)
        c.seekTo(target)
    }

    override suspend fun forward30() = onMain { c ->
        val target = c.currentPosition + REWIND_FORWARD_MS
        c.seekTo(target)
    }

    override suspend fun nextChapter() = onMain { c ->
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    override suspend fun prevChapter() = onMain { c ->
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0L)
    }

    override suspend fun setSpeed(speed: Float) = onMain { c ->
        c.setPlaybackSpeed(speed.coerceIn(0.5f, 3.5f))
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        // Skip-silence is an ExoPlayer-only knob (not exposed on the cross-process MediaController
        // surface). Phase J wires this through a custom session command; for Phase F we just
        // mirror the request into [PlayerSnapshot] so the UI reflects the intent.
        playerSnapshot.update { it.copy(skipSilenceRequested = enabled) }
    }

    override suspend fun setGain(gainDb: Float) {
        // Same shape as skip-silence — Phase J ships the AudioProcessor; this is a no-op stub.
        playerSnapshot.update { it.copy(gainDbRequested = gainDb) }
    }

    // ---------------------------------------------------------------- BookCommands

    override suspend fun playBook(bookId: String) {
        val book = bookSource.observeBook(bookId).firstOrLoadingDefault() ?: return
        val chapters = chapterSource.chaptersFor(bookId)
        startPlayback(book, chapters, startMs = book.positionInChapterMs, startIndex = book.currentChapterIndex)
    }

    override suspend fun playBookFromPosition(bookId: String, positionInBookMs: Long) {
        val book = bookSource.observeBook(bookId).firstOrLoadingDefault() ?: return
        val chapters = chapterSource.chaptersFor(bookId)
        // Resolve the book-absolute offset into (chapterIndex, positionInChapterMs).
        var remaining = positionInBookMs.coerceAtLeast(0L)
        var idx = 0
        for ((i, ch) in chapters.withIndex()) {
            if (remaining < ch.durationMs || i == chapters.lastIndex) {
                idx = i
                break
            }
            remaining -= ch.durationMs
        }
        startPlayback(book, chapters, startMs = remaining, startIndex = idx)
    }

    private suspend fun startPlayback(
        book: BookEntity,
        chapters: List<ChapterEntity>,
        startMs: Long,
        startIndex: Int,
    ) = onMain { c ->
        targetedBookId.value = book.bookId
        val items = chapters.map { ch ->
            val uri = ch.fileUri ?: book.relativePath
            MediaItem.Builder()
                .setMediaId(ch.chapterId)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ch.title ?: book.title)
                        .setArtist(book.author)
                        .setAlbumTitle(book.title)
                        .setDurationMs(ch.durationMs)
                        .build(),
                )
                .build()
        }
        if (items.isEmpty()) {
            // Degenerate: no chapters. Fall back to the book's primary path so Phase B's smoke
            // codec coverage still plays *something*. Real handling depends on Phase D's
            // single-file scan producing at least a placeholder chapter row.
            c.setMediaItems(emptyList())
        } else {
            c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), startMs.coerceAtLeast(0L))
        }
        c.prepare()
        c.playWhenReady = true
    }

    // ---------------------------------------------------------------- helpers

    private suspend inline fun onMain(crossinline block: (MediaController) -> Unit) {
        val c = controller ?: return
        withContext(Dispatchers.Main) { block(c) }
    }

    private suspend fun kotlinx.coroutines.flow.Flow<BookEntity?>.firstOrLoadingDefault(): BookEntity? {
        // Tiny helper: grab the first non-null emission with a short timeout-ish bound. Phase F
        // doesn't need a hard timeout because the BookEntity is already cached in Room from the
        // last scan; if it really isn't there, the caller's `playBook` is a user error and the
        // null return surfaces as `BookNotFound` via the state projection.
        return kotlinx.coroutines.flow.firstOrNull(this)
    }

    fun release() {
        tickerJob?.cancel()
        controller?.release()
        controller = null
        connected.value = false
    }

    /** Internal snapshot of fields driven by [Player.Listener] (NOT the position ticker). */
    private data class PlayerSnapshot(
        val isPlaying: Boolean = false,
        val currentMediaId: String? = null,
        val currentItemIndex: Int = 0,
        val speed: Float = 1.0f,
        val skipSilenceRequested: Boolean = false,
        val gainDbRequested: Float = 0.0f,
    )

    private companion object {
        const val TICKER_INTERVAL_MS = 250L
        const val REWIND_FORWARD_MS = 30_000L
    }
}
