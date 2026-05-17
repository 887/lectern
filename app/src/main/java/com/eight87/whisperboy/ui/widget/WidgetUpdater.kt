package com.eight87.whisperboy.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.eight87.whisperboy.WhisperboyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * main.md Phase M — widget refresh driver.
 *
 * Subscribes to [com.eight87.whisperboy.playback.NowPlayingState.state] and
 * calls `WhisperboyWidget().updateAll(context)` whenever the *meaningful*
 * fields (book, chapter, isPlaying) change. The 250 ms position ticker would
 * otherwise re-render the widget four times a second; we collapse that down
 * to a 1 s sample window and additionally `distinctUntilChangedBy` the fields
 * we actually paint.
 *
 * Lifecycle: started from [WhisperboyWidgetReceiver.onUpdate] /
 * `onReceive`; stopped from `onDisabled` (last widget removed). The collector
 * lives on a private application-scope Supervisor — outside the app graph's
 * own scope so a widget tear-down doesn't cancel any sibling work.
 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @OptIn(FlowPreview::class)
    fun ensureRunning(app: WhisperboyApplication) {
        if (job?.isActive == true) return
        val nowPlaying = app.graph.nowPlayingState
        val appContext: Context = app.applicationContext
        job = scope.launch {
            nowPlaying.state
                .sample(SAMPLE_WINDOW_MS)
                .distinctUntilChangedBy { state ->
                    // Re-render only when something we actually paint changes:
                    // book identity, chapter identity, play/pause flag.
                    val loaded = state as? com.eight87.whisperboy.playback.PlaybackUiState.Loaded
                    Triple(
                        loaded?.book?.bookId,
                        loaded?.currentChapter?.chapterId,
                        loaded?.isPlaying ?: false,
                    )
                }
                .onEach {
                    runCatching { WhisperboyWidget().updateAll(appContext) }
                }
                .collect { /* terminal collector — onEach does the work */ }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Debounce window for widget redraws. Voice's widget uses a similar
     * coarse-grained sample to avoid RemoteViews churn from the position
     * ticker. Position itself isn't painted on the widget today, so even
     * 1 s feels generous — but it keeps us honest if a future iteration
     * does add a progress bar.
     */
    private const val SAMPLE_WINDOW_MS = 1_000L
}
