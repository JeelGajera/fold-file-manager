package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * `RECENT`, `IN PROGRESS`, `CONNECTED` -- the wide uppercase label that opens a
 * section, optionally with a Doto readout or an action on the right.
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    color: Color = FoldTheme.colors.onBackground.copy(alpha = 0.55f),
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(
                start = FoldSpacing.screenGutter,
                end = FoldSpacing.screenGutter,
                top = FoldSpacing.s4,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = FoldTheme.typography.label,
            color = color,
            maxLines = 1,
            modifier = Modifier.semantics { heading() },
        )
        trailing?.invoke()
    }
}
