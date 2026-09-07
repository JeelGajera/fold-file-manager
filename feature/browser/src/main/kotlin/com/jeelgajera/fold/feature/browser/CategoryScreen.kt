package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.FoldFileRow
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.util.Formatting

/**
 * Everything in one category, wherever it happens to live.
 *
 * A category is a property of a file, not a folder: documents are saved where
 * the app that made them chose, and there is no directory on the device that
 * contains all of them. So this lists files from the index rather than opening
 * a path -- which is the whole reason the home screen's tiles cannot simply be
 * shortcuts to a directory.
 *
 * Ordered largest first. The tiles answer "where did my storage go", and the
 * listing behind a tile should answer the same question rather than re-sorting
 * by a date the user did not ask about.
 */
@Composable
fun CategoryScreen(
    onOpenFile: (FsPath) -> Unit,
    onRevealIn: (FsPath) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.category.collectAsStateWithLifecycle()
    val colors = FoldTheme.colors

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.dividerStrong, FoldRules.sectionDivider)
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            ) {
                Text(
                    state.label.uppercase(),
                    style = FoldTheme.typography.meta,
                    color = colors.accent,
                    maxLines = 1,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        stringResource(R.string.category_count, Formatting.count(state.files.size)),
                        style = FoldTheme.typography.titleS,
                        color = colors.onBackground,
                    )
                    Text(
                        Formatting.bytes(state.totalBytes),
                        style = FoldTheme.typography.meta,
                        color = colors.onBackground.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }

                // Curation is stated, never silent. The count of what is being
                // held back is shown with the control that brings it back, so
                // the list is never quietly shorter than the tile promised.
                if (state.filteredOut > 0 || state.includeNoise) {
                    Text(
                        text = if (state.includeNoise) {
                            stringResource(R.string.category_hide_app_files)
                        } else {
                            stringResource(
                                R.string.category_show_app_files,
                                Formatting.count(state.filteredOut),
                            )
                        },
                        style = FoldTheme.typography.meta,
                        color = colors.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable(role = Role.Button) {
                                viewModel.setCategoryIncludeNoise(!state.includeNoise)
                            },
                    )
                }
            }
        }

        items(state.files, key = { it.path }) { entry ->
            FoldFileRow(
                name = entry.name,
                // The folder is the useful second line here: the name alone does
                // not say which of four "invoice.pdf" this is.
                meta = "${Formatting.bytes(entry.sizeBytes)} · ${entry.parentPath.substringAfterLast('/')}",
                badge = entry.extension.uppercase().take(4).ifEmpty { "?" },
                trailing = Formatting.date(entry.lastModified),
                onClick = { onOpenFile(FsPath.raw(entry.path)) },
                // Long press goes to the file in its folder, for the times the
                // question is "what else is in there".
                onLongClick = { onRevealIn(FsPath.raw(entry.parentPath)) },
            )
        }

        if (state.files.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.category_empty),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
