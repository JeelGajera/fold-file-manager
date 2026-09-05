package com.jeelgajera.fold.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

/**
 * The small piece of state the server widget needs, and the seam that keeps
 * `:widget` from depending on `:feature:transfer`.
 *
 * The dependency would only go one way if it existed at all -- the widget would
 * pull in Ktor, the foreground service and the whole transfer graph to render two
 * lines of text. Instead the transfer layer *publishes* here when the server
 * starts or stops, and the widget reads it back. Two shared preferences and an
 * intent action, which is the right size for the problem.
 */
object ServerWidgetState {

    private const val PREFS = "fold-widget"
    private const val KEY_RUNNING = "server_running"
    private const val KEY_ADDRESS = "server_address"

    /** Broadcast the app listens for to start or stop the server from the widget. */
    const val ACTION_TOGGLE = "com.jeelgajera.fold.widget.TOGGLE_SERVER"

    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RUNNING, false)

    fun address(context: Context): String? =
        prefs(context).getString(KEY_ADDRESS, null)

    /**
     * Called by the transfer layer when the server's state changes.
     *
     * Also refreshes every placed widget, so the home screen never shows
     * "SHARING" for a server that stopped ten minutes ago -- which would be worse
     * than showing nothing.
     */
    suspend fun publish(context: Context, running: Boolean, address: String?) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_ADDRESS, address)
            .apply()

        runCatching { ServerWidget().updateAll(context) }
    }

    /** Sends the toggle intent. The app decides what to do with it. */
    fun toggle(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_TOGGLE).setPackage(context.packageName)
        )
    }

    /** True when at least one server widget is on a home screen. */
    suspend fun hasPlacedWidgets(context: Context): Boolean =
        runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(ServerWidget::class.java).isNotEmpty()
        }.getOrDefault(false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
