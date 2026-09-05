package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * The single row that renders a file everywhere in FOLD: recents, a folder
 * listing, search results, the vault, the hidden-files list.
 *
 * Selection is carried three ways at once -- a tinted row, an accent-filled
 * badge, and the badge's mark flipping from the extension to a check -- so the
 * state reads without relying on the red.
 */
@Composable
fun FoldFileRow(
    name: String,
    meta: String,
    badge: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    selected: Boolean = false,
    badgeAccent: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val colors = FoldTheme.colors
    val selectionState = if (selected) "Selected" else "Not selected"

    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .bottomRule(colors.divider)
            .defaultMinSize(minHeight = FoldSizing.fileRowMinHeight)
            .padding(horizontal = FoldSpacing.screenGutter, vertical = 10.dp)
            .semantics { stateDescription = selectionState },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoldSpacing.s3),
    ) {
        TypeBadge(
            mark = if (selected) "✓" else badge,
            borderColor = if (selected) colors.accent else colors.onBackground.copy(alpha = 0.35f),
            fillColor = if (selected) colors.accent else Color.Transparent,
            textColor = when {
                selected -> colors.accentInk
                badgeAccent -> colors.accent
                else -> colors.onBackground
            },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = FoldTheme.typography.body,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = FoldTheme.typography.meta,
                color = colors.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}
