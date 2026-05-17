package com.eight87.whisperboy.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.eight87.whisperboy.WhisperboyApplication
import com.eight87.whisperboy.playback.PlaybackUiState

/**
 * main.md Phase M — Glance [ActionCallback]s for the widget's transport row.
 *
 * Each one resolves the current [com.eight87.whisperboy.playback.PlaybackUiState]
 * via [WhisperboyApplication.graph] and dispatches through the existing narrow
 * [com.eight87.whisperboy.playback.TransportCommands] surface. No new
 * authority — the widget is just another caller of the same transport API
 * the player screen and the now-playing bar use.
 *
 * Callbacks no-op cleanly when nothing is playing (the widget will render the
 * "Nothing playing" body in that case, so the transport buttons are not
 * shown — but a stale tap before the next redraw is harmless).
 */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val graph = (context.applicationContext as WhisperboyApplication).graph
        val loaded = graph.nowPlayingState.state.value as? PlaybackUiState.Loaded ?: return
        if (loaded.isPlaying) graph.transportCommands.pause() else graph.transportCommands.play()
    }
}

class NextChapterAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val graph = (context.applicationContext as WhisperboyApplication).graph
        graph.transportCommands.nextChapter()
    }
}

class PrevChapterAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val graph = (context.applicationContext as WhisperboyApplication).graph
        graph.transportCommands.prevChapter()
    }
}
