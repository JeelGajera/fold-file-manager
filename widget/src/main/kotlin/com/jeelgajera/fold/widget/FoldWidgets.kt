package com.jeelgajera.fold.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jeelgajera.fold.core.storage.stats.VolumeStats
import com.jeelgajera.fold.core.storage.util.Formatting
import androidx.glance.action.ActionParameters

/**
 * FOLD's two home-screen widgets.
 *
 * Both are drawn from the same design language as the app -- dot-matrix meter,
 * Doto numerals, 0dp radius, one accent -- but neither shows a file name. A
 * widget sits on a screen anyone can see over your shoulder, so it reports
 * aggregates and state, never contents. The vault is not represented at all.
 *
 * Glance cannot use FOLD's Compose theme (it is a different composition target
 * that compiles to RemoteViews), so the handful of colours it needs are declared
 * here against the same token values rather than approximated.
 */
private object WidgetColors {
    val background = Color(0xFF100F0F)
    val ink = Color(0xFFF3F2F2)
    val inkMuted = Color(0x9EF3F2F2)
    val accent = Color(0xFFEC3013)
    val accentInk = Color(0xFF0C0B0B)
    val track = Color(0x24F3F2F2)
}

/**
 * The storage meter, 4x2.
 *
 * Deliberately not interactive beyond opening the app: a widget that can delete
 * things is a widget that deletes things by accident in a pocket.
 */
class StorageWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (used, total) = VolumeStats.primaryVolume()
        val percent = if (total > 0) (used * 100 / total).toInt() else 0
        val (headline, unit) = Formatting.bytesSplit(used)

        provideContent {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(WidgetColors.background)
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Row(
                    GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.Bottom,
                ) {
                    Text(
                        headline,
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.ink),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        unit,
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.inkMuted),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        "$percent% USED",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.inkMuted),
                            fontSize = 11.sp,
                        ),
                    )
                }

                Spacer(GlanceModifier.height(12.dp))

                // The dot meter, rebuilt in Glance's layout primitives. Twenty-four
                // cells rather than forty: a home-screen widget is read at a glance
                // and forty cells at this width become a smear.
                Row(GlanceModifier.fillMaxWidth().height(12.dp)) {
                    repeat(WIDGET_CELLS) { index ->
                        val filled = index * 100 / WIDGET_CELLS < percent
                        MeterCell(
                            filled = filled,
                            isLast = index == WIDGET_CELLS - 1,
                        )
                    }
                }
            }
        }
    }

    /**
     * One cell of the meter.
     *
     * Declared on [RowScope] because `defaultWeight` is a member of that scope
     * rather than a free function, so the cell can only be laid out inside the
     * Row it divides.
     */
    @Composable
    private fun RowScope.MeterCell(filled: Boolean, isLast: Boolean) {
        Spacer(
            GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(if (filled) WidgetColors.accent else WidgetColors.track)
        )
        if (!isLast) Spacer(GlanceModifier.width(2.dp))
    }

    private companion object {
        const val WIDGET_CELLS = 24
    }
}

/**
 * The share-over-Wi-Fi toggle, 2x2.
 *
 * The state is carried by an inversion -- the whole tile fills red while sharing
 * -- plus the word SHARING and the address. Red alone never says it, which
 * matters more here than anywhere: this is the control that tells someone their
 * phone is serving files, and it may be the only place they see it.
 */
class ServerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Widgets are refreshed by the host, not by a live flow, so this
            // reads the last published state rather than subscribing. The
            // foreground service updates the widget when the server starts or
            // stops, which is when this actually needs to change.
            val running = ServerWidgetState.isRunning(context)
            val address = ServerWidgetState.address(context)

            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(if (running) WidgetColors.accent else WidgetColors.background)
                    .padding(16.dp)
                    .clickable(actionRunCallback<ToggleServerAction>()),
                verticalAlignment = Alignment.Vertical.Top,
            ) {
                val ink = if (running) WidgetColors.accentInk else WidgetColors.ink

                Spacer(GlanceModifier.defaultWeight())
                Text(
                    if (running) "SHARING" else "NOT SHARING",
                    style = TextStyle(
                        color = ColorProvider(ink),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    address ?: "TAP TO START",
                    style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

/**
 * Toggling the server from the home screen.
 *
 * Starting is allowed from here; the foreground service and its notification
 * appear immediately, so the state is never invisible. Stopping is allowed too,
 * because the fastest way to stop sharing should be the same tap that started it.
 */
class ToggleServerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        ServerWidgetState.toggle(context)
    }
}

class StorageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StorageWidget()
}

class ServerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServerWidget()
}
