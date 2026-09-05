package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.component.FoldAccentButton
import com.jeelgajera.fold.core.design.component.FoldOutlineButton
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.model.FsRoot

/**
 * What FOLD looks like without All Files Access.
 *
 * Not an error screen, and pointedly not styled as one. This is a supported mode:
 * the app works inside the folders the user granted, the copy says so without
 * nagging, and the offer to grant more sits next to the offer to add another
 * folder rather than above it.
 *
 * Getting this right is the practical payoff of the provider abstraction. If
 * Play review refuses the All Files Access declaration, or the policy tightens
 * later, this is the app -- not a dead launch.
 */
@Composable
fun LimitedAccessScreen(
    roots: List<FsRoot>,
    canRequestAllFiles: Boolean,
    onRequestAllFiles: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    stringResource(R.string.limited_kicker),
                    style = FoldTheme.typography.label,
                    color = colors.accentBodyText,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.limited_title),
                    style = FoldTheme.typography.titleM,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
                Text(
                    stringResource(R.string.limited_body),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackground.copy(alpha = 0.72f),
                    modifier = Modifier.widthIn(max = 310.dp),
                )
            }
        }

        items(roots, key = { it.path.value }) { root ->
            AccessRow(
                name = root.label,
                meta = root.path.value,
                state = stringResource(R.string.limited_state_allowed),
                dotColor = colors.accent,
            )
        }

        item {
            // Naming what is *not* reachable is as important as naming what is.
            // A limited-access file manager that only lists what it can see leaves
            // the user guessing about the rest.
            AccessRow(
                name = stringResource(R.string.limited_everything_else),
                meta = stringResource(R.string.limited_everything_else_meta),
                state = stringResource(R.string.limited_state_hidden),
                dotColor = colors.onBackground.copy(alpha = 0.3f),
            )
        }

        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRequestAllFiles) {
                    FoldAccentButton(
                        label = stringResource(R.string.limited_grant_all),
                        onClick = onRequestAllFiles,
                        minHeight = 52.dp,
                    )
                }
                FoldOutlineButton(
                    label = stringResource(R.string.limited_add_folder),
                    onClick = onAddFolder,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.limited_note),
                    style = FoldTheme.typography.bodyS,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AccessRow(
    name: String,
    meta: String,
    state: String,
    dotColor: androidx.compose.ui.graphics.Color,
) {
    val colors = FoldTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(dotColor))
        Column(Modifier.weight(1f)) {
            Text(name, style = FoldTheme.typography.body, color = colors.onBackground, maxLines = 1)
            Text(
                meta,
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Text(
            state,
            style = FoldTheme.typography.labelS,
            color = colors.onBackground.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }
}
