package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.FoldCallout
import com.jeelgajera.fold.core.design.component.FoldFileRow
import com.jeelgajera.fold.core.design.component.FoldToggleRow
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.util.Formatting

/**
 * The hidden-files toggle.
 *
 * This screen exists mostly to keep a promise: hidden files and the vault must
 * never blur into one another. A dot-prefixed name is a convention, not a
 * protection, and every app with storage access reads those files whether or not
 * FOLD draws them. The screen says so in the body copy and again in a callout,
 * and the word "private" appears nowhere on it.
 */
@Composable
fun HiddenFilesScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.browse.collectAsStateWithLifecycle()
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
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(
                    stringResource(R.string.hidden_title),
                    style = FoldTheme.typography.titleM,
                    color = colors.onBackground,
                )
                Text(
                    stringResource(R.string.hidden_body),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackground.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            FoldToggleRow(
                label = stringResource(R.string.hidden_toggle),
                help = stringResource(R.string.settings_hidden_help),
                checked = state.showHidden,
                onCheckedChange = viewModel::setShowHidden,
            )
        }

        item {
            FoldCallout(
                tag = stringResource(R.string.hidden_callout_tag),
                text = stringResource(R.string.hidden_callout),
            )
        }

        // The listing shows the dot-prefixed entries in the current folder,
        // dimmed while the toggle is off so the effect of turning it on is
        // visible before you commit to it.
        val hidden = state.entries.filter { it.isHidden }
        items(hidden, key = { it.path.value }) { entry ->
            FoldFileRow(
                name = entry.name,
                meta = Formatting.fileMeta(
                    entry.sizeBytes,
                    entry.lastModifiedMillis,
                    entry.isDirectory,
                    entry.childCount,
                ),
                badge = ".",
                onClick = { if (entry.isDirectory) viewModel.open(entry.path) },
            )
        }

        if (hidden.isEmpty()) {
            item {
                Text(
                    stringResource(
                        R.string.hidden_none_here,
                        state.path?.name.orEmpty(),
                    ),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
