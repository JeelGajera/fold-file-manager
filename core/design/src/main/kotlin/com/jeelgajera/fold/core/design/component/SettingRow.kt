package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/** A settings row that opens something: label, help text, and a Doto value on the right. */
@Composable
fun FoldSettingRow(
    label: String,
    help: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueAccent: Boolean = false,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .bottomRule(colors.divider)
            .defaultMinSize(minHeight = FoldSizing.settingRowMinHeight)
            .padding(horizontal = FoldSpacing.screenGutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoldSpacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FoldTheme.typography.body, color = colors.onBackground)
            Text(
                text = help,
                style = FoldTheme.typography.bodyS.copy(fontSize = 11.5.sp),
                color = colors.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 3.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        }
        Text(
            text = value,
            style = FoldTheme.typography.meta,
            color = if (valueAccent) colors.accent else colors.onBackground,
            maxLines = 1,
        )
    }
}

/** A settings row that toggles something. The whole row is the switch. */
@Composable
fun FoldToggleRow(
    label: String,
    help: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    helpIsMeta: Boolean = false,
    background: Color = Color.Transparent,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .bottomRule(colors.divider)
            .defaultMinSize(minHeight = FoldSizing.settingRowMinHeight)
            .padding(horizontal = FoldSpacing.screenGutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoldSpacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FoldTheme.typography.body, color = colors.onBackground)
            Text(
                text = help,
                style = if (helpIsMeta) FoldTheme.typography.meta else FoldTheme.typography.bodyS,
                color = colors.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FoldSwitch(checked = checked, small = small)
    }
}

