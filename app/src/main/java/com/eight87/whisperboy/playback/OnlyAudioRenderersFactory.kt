package com.eight87.whisperboy.playback

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * RenderersFactory that omits the video pipeline entirely — whisperboy is audio-only.
 *
 * Smaller APK (no video decoder linkage) and no surface management overhead. Voice analog:
 * `voice.core.playback.player.OnlyAudioRenderersFactory`.
 */
class OnlyAudioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        // Intentionally empty — no video.
    }
}
