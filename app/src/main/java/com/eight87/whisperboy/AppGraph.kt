package com.eight87.whisperboy

import android.content.Context
import com.eight87.whisperboy.playback.PlayerHolder

/**
 * Composition root. The only place in the app that knows concrete types for long-lived singletons.
 *
 * Created once in [WhisperboyApplication.onCreate]; ViewModels / composables / services receive
 * narrow interfaces from here, never the AppGraph itself.
 */
class AppGraph(context: Context) {
    val playerHolder: PlayerHolder = PlayerHolder(context.applicationContext)

    fun release() {
        playerHolder.release()
    }
}
