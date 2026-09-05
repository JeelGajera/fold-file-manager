package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.FoldFileRow
import com.jeelgajera.fold.core.design.component.FoldIconButton
import com.jeelgajera.fold.core.design.component.FoldOutlineButton
import com.jeelgajera.fold.core.design.component.FoldSortChip
import com.jeelgajera.fold.core.design.component.TypeBadge
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.component.endRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.FsSort
import com.jeelgajera.fold.core.storage.util.Formatting

/**
 * The folder listing.
 *
 * Selection turns the header bar red and inverts its ink, which is the app's
 * one destructive-adjacent mode. The count in Doto is what actually names the
 * state; the red is the second signal, not the only one.
 */
@Composable
fun BrowseScreen(
    onOpenFile: (FsEntry) -> Unit,
    onShareSelection: (List<FsPath>) -> Unit,
    onOpenHidden: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.browse.collectAsStateWithLifecycle()
    val colors = FoldTheme.colors

    Column(modifier.fillMaxWidth()) {
        Breadcrumbs(
            segments = state.crumbs,
            gridView = state.gridView,
            onToggleView = viewModel::toggleView,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .bottomRule(colors.dividerStrong, FoldRules.sectionDivider)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(
                    R.string.browse_folder_stats,
                    state.entries.size,
                    Formatting.bytes(state.totalBytes),
                ),
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            FoldSortChip(label = sortLabel(state.sort), onCycle = viewModel::cycleSort)
            FoldOutlineButton(
                label = stringResource(R.string.browse_hidden),
                onClick = onOpenHidden,
                minHeight = FoldSizing.scopeChipHeight,
                borderColor = colors.onBackground.copy(alpha = 0.14f),
                contentColor = colors.onBackgroundMuted,
            )
        }

        if (state.hasSelection) {
            SelectionBar(
                count = state.selection.size,
                onShare = { onShareSelection(viewModel.selectedPaths()) },
                onDelete = { viewModel.delete(viewModel.selectedPaths()) },
                onClear = viewModel::clearSelection,
            )
        }

        state.error?.let { message ->
            Text(
                message,
                style = FoldTheme.typography.bodyS,
                color = colors.accentBodyText,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (state.gridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
            ) {
                items(state.entries, key = { it.path.value }) { entry ->
                    GridTile(
                        entry = entry,
                        selected = entry.path.value in state.selection,
                        onClick = { if (entry.isDirectory) viewModel.open(entry.path) else onOpenFile(entry) },
                        onLongClick = { viewModel.toggleSelection(entry) },
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance)) {
                items(state.entries, key = { it.path.value }) { entry ->
                    FoldFileRow(
                        name = entry.name,
                        meta = Formatting.fileMeta(
                            entry.sizeBytes,
                            entry.lastModifiedMillis,
                            entry.isDirectory,
                            entry.childCount,
                        ),
                        badge = if (entry.isDirectory) "DIR" else entry.badge(),
                        badgeAccent = entry.isDirectory,
                        trailing = when (state.sort) {
                            FsSort.SIZE -> Formatting.bytes(entry.sizeBytes)
                            else -> Formatting.date(entry.lastModifiedMillis)
                        },
                        selected = entry.path.value in state.selection,
                        onClick = {
                            when {
                                state.hasSelection -> viewModel.toggleSelection(entry)
                                entry.isDirectory -> viewModel.open(entry.path)
                                else -> onOpenFile(entry)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(
    segments: List<String>,
    gridView: Boolean,
    onToggleView: () -> Unit,
) {
    val colors = FoldTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .height(44.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { index, segment ->
                Text(
                    segment,
                    style = FoldTheme.typography.meta.copy(fontSize = 12.sp),
                    color = if (index == segments.lastIndex) {
                        colors.onBackground
                    } else {
                        colors.onBackground.copy(alpha = 0.5f)
                    },
                    maxLines = 1,
                )
                if (index < segments.lastIndex) {
                    Text(
                        "/",
                        style = FoldTheme.typography.meta,
                        color = colors.onBackground.copy(alpha = 0.3f),
                    )
                }
            }
        }

        FoldIconButton(
            contentDescription = stringResource(R.string.browse_toggle_view),
            onClick = onToggleView,
            size = 44.dp,
        ) {
            // Four cells; in list mode two of them dim, so the control shows the
            // mode you would switch *to* as well as the one you are in.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) { column ->
                            val on = gridView || (row + column) % 2 == 0
                            Box(
                                Modifier
                                    .height(6.dp)
                                    .defaultMinSize(minWidth = 6.dp)
                                    .background(
                                        if (on) colors.onBackground
                                        else colors.onBackground.copy(alpha = 0.3f)
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = FoldTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.accent)
            .defaultMinSize(minHeight = 52.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.browse_selected, count),
            style = FoldTheme.typography.meta.copy(fontSize = 15.sp),
            color = colors.accentInk,
            maxLines = 1,
        )
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
        ) {
            SelectionAction(stringResource(R.string.browse_action_share), onShare)
            SelectionAction(stringResource(R.string.browse_action_delete), onDelete)
            SelectionAction(stringResource(R.string.browse_action_clear), onClear)
        }
    }
}

@Composable
private fun SelectionAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = FoldTheme.typography.label,
            color = FoldTheme.colors.accentInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun GridTile(
    entry: FsEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = FoldTheme.colors
    Column(
        Modifier
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .endRule(colors.divider)
            .bottomRule(colors.divider)
            .defaultMinSize(minHeight = FoldSizing.gridTileMinHeight)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TypeBadge(
            mark = if (selected) "✓" else if (entry.isDirectory) "DIR" else entry.badge(),
            size = FoldSizing.typeBadgeGrid,
            borderColor = if (selected) colors.accent else colors.onBackground.copy(alpha = 0.35f),
            fillColor = if (selected) colors.accent else Color.Transparent,
            textColor = when {
                selected -> colors.accentInk
                entry.isDirectory -> colors.accent
                else -> colors.onBackground
            },
        )
        Column(Modifier.padding(top = 10.dp)) {
            Text(
                entry.name,
                style = FoldTheme.typography.bodyS.copy(fontSize = 11.sp),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.isDirectory) {
                    entry.childCount?.let { stringResource(R.string.home_item_count, Formatting.count(it)) }
                        ?: stringResource(R.string.browse_folder)
                } else {
                    Formatting.bytes(entry.sizeBytes)
                },
                style = FoldTheme.typography.metaS,
                color = colors.onBackgroundMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * The badge for an entry.
 *
 * A file whose type FOLD cannot name gets `?`, and stays in the list. That is the
 * whole difference between this app and one that reads a media index.
 */
private fun FsEntry.badge(): String = extension.uppercase().take(4).ifEmpty { "?" }

@Composable
private fun sortLabel(sort: FsSort): String = stringResource(
    when (sort) {
        FsSort.DATE -> R.string.browse_sort_date
        FsSort.SIZE -> R.string.browse_sort_size
        FsSort.NAME -> R.string.browse_sort_name
    }
)
