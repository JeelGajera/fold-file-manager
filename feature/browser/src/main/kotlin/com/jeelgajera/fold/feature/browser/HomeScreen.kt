package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.DotCount
import com.jeelgajera.fold.core.design.component.DotMeter
import com.jeelgajera.fold.core.design.component.FoldFileRow
import com.jeelgajera.fold.core.design.component.MeterBand
import com.jeelgajera.fold.core.design.component.SectionHeading
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.component.endRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.mime.FileCategory
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.util.Formatting

/**
 * The home screen: how full the phone is, what is on it, and what changed last.
 *
 * The storage meter draws the volume's own used/total figures, and the coloured
 * bands underneath draw what FOLD indexed. Those two numbers do not add up -- the
 * operating system and other apps' private data are in the first and not the
 * second -- and the screen does not pretend otherwise. The legend is labelled
 * with what FOLD can see, and the difference is never invented into an "Other"
 * slice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenCategory: (FileCategory) -> Unit,
    onOpenPath: (FsPath) -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val colors = FoldTheme.colors

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
    ) {
        item {
            val snapshot = state.storage
            val (headline, unit) = Formatting.bytesSplit(snapshot?.usedBytes ?: 0)

            Column(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.dividerStrong, FoldRules.sectionDivider)
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(headline, style = FoldTheme.typography.displayXL, color = colors.onBackground)
                        Text(
                            unit,
                            style = FoldTheme.typography.titleS.copy(fontSize = 15.sp),
                            color = colors.onBackgroundMuted,
                        )
                    }
                    Text(
                        stringResource(R.string.home_of_total, Formatting.bytes(snapshot?.totalBytes ?: 0)),
                        style = FoldTheme.typography.meta,
                        color = colors.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }

                val bands = state.categories.meterBands(snapshot?.usedBytes ?: 0)
                DotMeter(
                    bands = bands,
                    contentDescription = stringResource(
                        R.string.home_meter_description,
                        Formatting.bytes(snapshot?.usedBytes ?: 0),
                        Formatting.bytes(snapshot?.totalBytes ?: 0),
                    ),
                )

                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    bands.forEach { band ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(band.color))
                            Text(
                                band.label,
                                style = FoldTheme.typography.bodyS.copy(fontSize = 11.sp),
                                color = colors.onBackground.copy(alpha = 0.72f),
                                maxLines = 1,
                            )
                            Text(
                                band.readout,
                                style = FoldTheme.typography.meta,
                                color = colors.onBackground,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // The category grid. Three columns of equal cells divided by hairlines --
        // the same grammar as the share tabs, so the app reads as one system.
        items(state.categories.chunked(3)) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.divider),
            ) {
                row.forEach { tile ->
                    CategoryCell(
                        tile = tile,
                        largestBytes = state.categories.maxOfOrNull { it.bytes } ?: 0,
                        onClick = { onOpenCategory(tile.category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps the last row's cells the same width as every other row's.
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }

        item {
            SectionHeading(
                text = stringResource(R.string.home_recent),
                trailing = {
                    Text(
                        text = stringResource(R.string.home_all_files),
                        style = FoldTheme.typography.label,
                        color = colors.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onBrowseAll)
                            .padding(vertical = 6.dp),
                    )
                },
            )
        }

        items(state.recents, key = { it.path }) { entry ->
            FoldFileRow(
                name = entry.name,
                meta = "${Formatting.bytes(entry.sizeBytes)} · ${entry.parentPath.substringAfterLast('/')}",
                badge = entry.extension.uppercase().take(4).ifEmpty { "?" },
                badgeAccent = entry.mimeType == "text/markdown",
                onClick = { onOpenPath(FsPath.raw(entry.parentPath)) },
            )
        }

        if (state.recents.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_empty_index),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(
    tile: CategoryTile,
    largestBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors
    Column(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .endRule(colors.divider)
            .defaultMinSize(minHeight = 88.dp)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        DotCount(filled = tile.dots(largestBytes))
        Column(Modifier.padding(top = 14.dp)) {
            Text(
                tile.label,
                style = FoldTheme.typography.bodyS.copy(fontSize = 12.sp),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.home_item_count, Formatting.count(tile.count)),
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Turns the category totals into meter bands.
 *
 * Bands are sized against the *volume's* used bytes rather than against the
 * indexed total, so the meter's filled portion honestly reflects how much of the
 * phone FOLD can actually account for. Everything it cannot see stays as unfilled
 * cells rather than being attributed to a category.
 */
@Composable
private fun List<CategoryTile>.meterBands(usedBytes: Long): List<MeterBand> {
    val colors = FoldTheme.colors
    if (usedBytes <= 0) return emptyList()

    val shades = listOf(
        colors.accent,
        colors.onBackground.copy(alpha = 0.85f),
        colors.onBackground.copy(alpha = 0.45f),
        colors.onBackground.copy(alpha = 0.25f),
    )

    return sortedByDescending { it.bytes }
        .filter { it.bytes > 0 }
        .take(4)
        .mapIndexed { index, tile ->
            MeterBand(
                weight = (tile.bytes.toFloat() / usedBytes).coerceIn(0f, 1f),
                color = shades[index % shades.size],
                label = tile.label,
                readout = Formatting.bytes(tile.bytes),
            )
        }
}
