package com.eight87.whisperboy.playback

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.eight87.whisperboy.WhisperboyApplication

/**
 * Foreground service that hosts the audio session.
 *
 * Extends [MediaLibraryService] (rather than [androidx.media3.session.MediaSessionService])
 * because Android Auto / Wear-OS / system media-browse clients require the
 * `MediaLibrarySession` browse-tree surface. Same [androidx.media3.exoplayer.ExoPlayer]
 * underneath; only the session shape differs.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as WhisperboyApplication).graph.playerHolder.player
        session = MediaLibrarySession.Builder(this, player, WhisperboyLibrarySessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            // Do not release the player here — AppGraph owns it across activity lifecycle.
            release()
        }
        session = null
        super.onDestroy()
    }
}
