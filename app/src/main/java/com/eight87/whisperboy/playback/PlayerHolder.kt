package com.eight87.whisperboy.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Owns the singleton [ExoPlayer] for the app. Built once in [com.eight87.whisperboy.AppGraph],
 * released on app exit.
 *
 * Key configuration:
 * - Audio attributes: USAGE_MEDIA + CONTENT_TYPE_SPEECH (audiobook-correct; affects routing
 *   and equalisation on devices that distinguish music from voice content).
 * - `handleAudioFocus = true` — Media3 manages focus requests / ducking / pause-on-loss
 *   internally; no manual [android.media.AudioFocusRequest] needed.
 * - `setHandleAudioBecomingNoisy(true)` — auto-pause on headphone unplug.
 * - `setWakeMode(WAKE_MODE_LOCAL)` — keep CPU alive during local playback (no network wake).
 * - Audio-only renderers via [OnlyAudioRenderersFactory] — no video pipeline.
 */
class PlayerHolder(context: Context) {

    val player: Player = ExoPlayer.Builder(context, OnlyAudioRenderersFactory(context))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    fun release() {
        player.release()
    }
}
