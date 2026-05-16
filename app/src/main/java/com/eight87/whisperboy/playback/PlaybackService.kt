package com.eight87.whisperboy.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.eight87.whisperboy.R
import com.eight87.whisperboy.WhisperboyApplication

/**
 * Foreground service that hosts the audio session.
 *
 * Extends [MediaLibraryService] (rather than [androidx.media3.session.MediaSessionService])
 * because Android Auto / Wear-OS / system media-browse clients require the
 * `MediaLibrarySession` browse-tree surface. Same [androidx.media3.exoplayer.ExoPlayer]
 * underneath; only the session shape differs.
 *
 * Notification + lock-screen controls are posted via Media3's built-in
 * [DefaultMediaNotificationProvider], pointed at our explicit
 * [PLAYBACK_NOTIFICATION_CHANNEL_ID] channel (registered in [onCreate] at
 * [NotificationManager.IMPORTANCE_LOW] — silent, no vibration, appropriate
 * for an ongoing media session). This keeps the system MediaStyle rendering
 * (action buttons + lock-screen art) without us hand-rolling the notification.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        createPlaybackNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(PLAYBACK_NOTIFICATION_CHANNEL_ID)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .build()
        )
        val player = (application as WhisperboyApplication).graph.playerHolder.player
        session = MediaLibrarySession.Builder(this, player, WhisperboyLibrarySessionCallback())
            .build()
    }

    private fun createPlaybackNotificationChannel() {
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            PLAYBACK_NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_playback_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_playback_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
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

    private companion object {
        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "whisperboy_playback"
        const val PLAYBACK_NOTIFICATION_ID = 1001
    }
}
