package com.eight87.whisperboy.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.eight87.whisperboy.WhisperboyApplication

/**
 * Debug-only smoke entry point. Receives a `path` extra, hands it to the [Player], logs
 * `Player.STATE_*` transitions to logcat under the [TAG] so `scripts/smoke-test.sh` can
 * grep for STATE_READY.
 *
 * Will be moved to a debug-variant sourceset once Phase O introduces signing + variants.
 */
class SmokePlayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val app = context.applicationContext as WhisperboyApplication
        val player = app.graph.playerHolder.player

        Log.i(TAG, "smoke: requested $path")

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "STATE_$state"
                }
                Log.i(TAG, "smoke: $path → $name")
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    player.removeListener(this)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "smoke: $path → ERROR ${error.errorCodeName}", error)
                player.removeListener(this)
            }
        }

        // Player ops must run on the app's main thread.
        val attach = Runnable {
            player.addListener(listener)
            player.setMediaItem(MediaItem.fromUri(path))
            player.prepare()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            attach.run()
        } else {
            Handler(Looper.getMainLooper()).post(attach)
        }
    }

    companion object {
        const val TAG = "whisperboy.smoke"
        const val EXTRA_PATH = "path"
        const val ACTION_SMOKE_PLAY = "com.eight87.whisperboy.action.SMOKE_PLAY"
    }
}
