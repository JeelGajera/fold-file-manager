package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * A correction, not a decoration.
 *
 * FOLD uses this in exactly the places where the app has to tell the user
 * something inconvenient and true: that hidden files are a visibility toggle and
 * not protection, that deletion on flash storage is best-effort, that there is
 * no public API for Quick Share. The 2dp red edge and the Doto tag mark it as a
 * statement of fact rather than an error.
 */
@Composable
fun FoldCallout(
    tag: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.accentTint)
            .accentEdge(colors.accent)
            .bottomRule(colors.divider)
            .padding(horizontal = FoldSpacing.screenGutter, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = tag,
            style = FoldTheme.typography.meta,
            color = colors.accentBodyText,
            maxLines = 1,
        )
        Text(
            text = text,
            style = FoldTheme.typography.bodyS,
            color = colors.onBackground.copy(alpha = 0.78f),
        )
    }
}
