package com.eight87.whisperboy

import android.app.Application

class WhisperboyApplication : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
