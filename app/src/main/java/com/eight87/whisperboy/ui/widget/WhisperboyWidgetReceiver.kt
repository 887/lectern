package com.eight87.whisperboy.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.eight87.whisperboy.WhisperboyApplication

/**
 * main.md Phase M — receiver glue.
 *
 * Glance owns its own internal `AppWidgetProvider` plumbing; we just hand it
 * the [WhisperboyWidget] instance via [glanceAppWidget]. On the first
 * `onUpdate` after process start we also kick the [WidgetUpdater] so the
 * playback-state collector is live for as long as the widget is on screen.
 * Tearing it down on the last `onDisabled` releases the application-scope
 * coroutine job.
 */
class WhisperboyWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WhisperboyWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val app = context.applicationContext as? WhisperboyApplication ?: return
        WidgetUpdater.ensureRunning(app)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Some launchers re-bind without firing onUpdate (boot complete, locale
        // change) but still send broadcasts here. Idempotent ensure-running.
        val app = context.applicationContext as? WhisperboyApplication ?: return
        WidgetUpdater.ensureRunning(app)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed — stop the collector.
        WidgetUpdater.stop()
    }
}
