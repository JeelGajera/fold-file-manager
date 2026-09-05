package com.jeelgajera.fold.feature.glyph.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.DotGrid
import com.jeelgajera.fold.core.design.component.FoldCallout
import com.jeelgajera.fold.core.design.component.FoldTabStrip
import com.jeelgajera.fold.core.design.component.FoldToggleRow
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.prefs.FoldSettings
import com.jeelgajera.fold.core.storage.prefs.GlyphMode
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.feature.glyph.GlyphDetection
import com.jeelgajera.fold.feature.glyph.GlyphEvent
import com.jeelgajera.fold.feature.glyph.GlyphHardware
import com.jeelgajera.fold.feature.glyph.GlyphSequences
import com.jeelgajera.fold.feature.glyph.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlyphSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val preferences: StateFlow<FoldSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldSettings())

    fun setMode(mode: GlyphMode) = viewModelScope.launch { settings.setGlyphMode(mode) }

    fun toggleEvent(event: GlyphEvent, enabled: Boolean) =
        viewModelScope.launch { settings.toggleGlyphEvent(event.name, enabled) }
}

/**
 * Glyph settings, and the previews of what each sequence looks like.
 *
 * The previews render the same [GlyphSequences] functions the hardware is driven
 * from, so what you see here is what the back of the phone will do -- not an
 * illustration of it.
 *
 * On a device with no glyph hardware this screen is not reachable at all: the
 * drawer hides the entry. It exists here in a "no hardware" state only so the
 * behaviour is inspectable during development.
 */
@Composable
fun GlyphSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: GlyphSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = FoldTheme.colors

    val hardware = GlyphDetection.detect(context)
    val potential = GlyphDetection.potentialHardware(context)

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.divider)
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            ) {
                Text(
                    stringResource(R.string.glyph_title),
                    style = FoldTheme.typography.titleM,
                    color = colors.onBackground,
                )
                Text(
                    stringResource(R.string.glyph_body),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            FoldTabStrip(
                options = listOf(
                    stringResource(R.string.glyph_mode_strip),
                    stringResource(R.string.glyph_mode_matrix),
                    stringResource(R.string.glyph_mode_off),
                ),
                selectedIndex = when (preferences.glyphMode) {
                    GlyphMode.STRIP -> 0
                    GlyphMode.MATRIX -> 1
                    GlyphMode.OFF -> 2
                },
                onSelect = {
                    viewModel.setMode(
                        when (it) {
                            0 -> GlyphMode.STRIP
                            1 -> GlyphMode.MATRIX
                            else -> GlyphMode.OFF
                        }
                    )
                },
                height = 48.dp,
            )
        }

        // The three honest states this screen can be in.
        when {
            hardware == GlyphHardware.NONE && potential == GlyphHardware.NONE -> item {
                FoldCallout(
                    tag = stringResource(R.string.glyph_none_tag),
                    text = stringResource(R.string.glyph_none),
                )
            }

            hardware == GlyphHardware.NONE && GlyphDetection.SDK_INTEGRATION_PENDING -> item {
                FoldCallout(
                    tag = stringResource(R.string.glyph_sdk_pending_tag),
                    text = stringResource(R.string.glyph_sdk_pending),
                )
            }
        }

        item {
            when (preferences.glyphMode) {
                GlyphMode.STRIP -> StripPreview()
                GlyphMode.MATRIX -> MatrixPreview()
                GlyphMode.OFF -> Unit
            }
        }

        items(GlyphEvent.entries.toList(), key = { it.name }) { event ->
            FoldToggleRow(
                label = stringResource(event.labelRes()),
                help = stringResource(event.specRes()),
                helpIsMeta = true,
                checked = event.name in preferences.glyphEvents,
                small = true,
                onCheckedChange = { viewModel.toggleEvent(event, it) },
            )
        }
    }
}

/**
 * The strip preview.
 *
 * Under reduced motion the animation is replaced by a single held frame, which
 * the token file's reduced-motion contract asks for by name.
 */
@Composable
private fun StripPreview() {
    val colors = FoldTheme.colors
    val reduced = FoldTheme.reducedMotion

    val progress = if (reduced) {
        0.46f
    } else {
        val transition = rememberInfiniteTransition(label = "GlyphStrip")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(6_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "GlyphStripProgress",
        )
        animated
    }

    val zones = GlyphSequences.stripProgress(progress, tick = (progress * 20).toInt())

    Column(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Text(
            stringResource(R.string.glyph_preview_strip),
            style = FoldTheme.typography.labelS,
            color = colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            zones.forEach { brightness ->
                Box(
                    Modifier
                        .weight(1f)
                        // Height and opacity both track brightness, so the preview
                        // survives greyscale the way the hardware does.
                        .height((20 + brightness * 0.6f).dp)
                        .alpha((brightness / 100f).coerceIn(0.18f, 1f))
                        .background(colors.accent),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.glyph_zones),
                style = FoldTheme.typography.metaS,
                color = colors.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
            )
            Text(
                stringResource(R.string.glyph_filled, (progress * 100).toInt()),
                style = FoldTheme.typography.metaS,
                color = colors.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MatrixPreview() {
    val colors = FoldTheme.colors
    val reduced = FoldTheme.reducedMotion

    val frame = if (reduced) {
        6
    } else {
        val transition = rememberInfiniteTransition(label = "GlyphMatrix")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = GlyphSequences.MATRIX_FRAMES.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(2_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "GlyphMatrixFrame",
        )
        animated.toInt()
    }

    val cells = GlyphSequences.matrixRing(frame)

    Column(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Text(
            stringResource(R.string.glyph_preview_matrix),
            style = FoldTheme.typography.labelS,
            color = colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.onBackground.copy(alpha = 0.04f))
                .padding(10.dp),
        ) {
            DotGrid(
                columns = GlyphSequences.MATRIX_SIZE,
                rows = GlyphSequences.MATRIX_SIZE,
                color = colors.accent,
            ) { x, y ->
                cells[y * GlyphSequences.MATRIX_SIZE + x] / 255f
            }
        }
        Text(
            stringResource(R.string.glyph_frame, frame + 1),
            style = FoldTheme.typography.metaS,
            color = colors.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
        )
    }
}

private fun GlyphEvent.labelRes(): Int = when (this) {
    GlyphEvent.TRANSFER_PROGRESS -> R.string.glyph_event_progress
    GlyphEvent.TRANSFER_COMPLETE -> R.string.glyph_event_complete
    GlyphEvent.INCOMING_CONNECTION -> R.string.glyph_event_incoming
    GlyphEvent.VAULT_UNLOCK_FAILURE -> R.string.glyph_event_vault
}

private fun GlyphEvent.specRes(): Int = when (this) {
    GlyphEvent.TRANSFER_PROGRESS -> R.string.glyph_event_progress_spec
    GlyphEvent.TRANSFER_COMPLETE -> R.string.glyph_event_complete_spec
    GlyphEvent.INCOMING_CONNECTION -> R.string.glyph_event_incoming_spec
    GlyphEvent.VAULT_UNLOCK_FAILURE -> R.string.glyph_event_vault_spec
}
