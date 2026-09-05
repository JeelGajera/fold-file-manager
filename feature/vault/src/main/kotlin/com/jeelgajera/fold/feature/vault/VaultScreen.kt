package com.jeelgajera.fold.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.crypto.AuthAvailability
import com.jeelgajera.fold.core.crypto.VaultState
import com.jeelgajera.fold.core.design.component.FoldAccentButton
import com.jeelgajera.fold.core.design.component.FoldCallout
import com.jeelgajera.fold.core.design.component.FoldInkButton
import com.jeelgajera.fold.core.design.component.FoldOutlineButton
import com.jeelgajera.fold.core.design.component.TypeBadge
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.icon.FoldIcon
import com.jeelgajera.fold.core.design.icon.FoldIconPaths
import com.jeelgajera.fold.core.design.theme.FoldLightColors
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.LocalFoldColors
import com.jeelgajera.fold.core.storage.util.Formatting
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The vault.
 *
 * Two states, and the difference between them is the whole ground of the screen:
 * locked is the app's dark surface, unlocked inverts to light. That inversion is
 * a second, non-colour carrier of "you are inside the vault" -- it survives
 * greyscale, colour blindness and a glance from across a table in a way a red
 * badge would not.
 *
 * `FLAG_SECURE`, exclusion from recents and exclusion from backup are set by the
 * host activity; see `MainActivity`. They are not this screen's to forget.
 */
@Composable
fun VaultScreen(
    onMoveFilesIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(activity) {
        activity?.let(viewModel::refreshAvailability)
    }

    when (val state = vault) {
        is VaultState.Locked -> LockedVault(
            ui = ui,
            onUnlock = {
                activity?.let {
                    viewModel.unlock(
                        activity = it,
                        title = context.getString(R.string.vault_prompt_title),
                        subtitle = context.getString(R.string.vault_prompt_subtitle),
                        negative = context.getString(R.string.vault_use_pin),
                    )
                }
            },
            onUseCredential = {
                activity?.let {
                    viewModel.unlockWithCredential(
                        activity = it,
                        title = context.getString(R.string.vault_prompt_title),
                        subtitle = context.getString(R.string.vault_prompt_subtitle),
                    )
                }
            },
            modifier = modifier,
        )

        is VaultState.Unlocked -> UnlockedVault(
            state = state,
            remainingMillis = ui.remainingMillis,
            onLock = viewModel::lock,
            onMoveFilesIn = onMoveFilesIn,
            modifier = modifier,
        )
    }
}

@Composable
private fun LockedVault(
    ui: VaultUiState,
    onUnlock: () -> Unit,
    onUseCredential: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 24.dp),
    ) {
        // A 5x5 mark. Decorative, so it is removed from the accessibility tree --
        // the heading below already says what this screen is.
        Column(
            Modifier
                .padding(bottom = 28.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(5) { y ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { x ->
                        val index = y * 5 + x
                        val on = index in LOCK_MARK
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    if (on) colors.accent else colors.onBackground.copy(alpha = 0.22f)
                                ),
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.vault_title),
            style = FoldTheme.typography.titleL,
            color = colors.onBackground,
        )
        Text(
            stringResource(R.string.vault_locked_body),
            style = FoldTheme.typography.body,
            color = colors.onBackground.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .height(FoldRules.sectionDivider)
                .background(colors.dividerStrong),
        )

        when {
            // No screen lock at all: the vault cannot exist, and saying so beats
            // a button that fails at the first write.
            ui.availability == AuthAvailability.NONE -> FoldCallout(
                tag = stringResource(R.string.vault_no_lock_tag),
                text = stringResource(R.string.vault_no_lock),
            )

            // The key is gone. Re-prompting cannot bring it back, so the screen
            // stops offering to.
            ui.permanentlyLost -> FoldCallout(
                tag = stringResource(R.string.vault_lost_tag),
                text = stringResource(R.string.vault_lost),
            )

            else -> {
                if (ui.availability == AuthAvailability.BIOMETRIC) {
                    FoldAccentButton(
                        label = stringResource(R.string.vault_unlock_biometric),
                        onClick = onUnlock,
                        minHeight = 60.dp,
                        leading = {
                            FoldIcon(
                                FoldIconPaths.Fingerprint,
                                tint = colors.accentInk,
                                size = 22.dp,
                                strokeWidth = 1.6f,
                            )
                        },
                    )
                }
                Box(Modifier.height(8.dp))
                FoldOutlineButton(
                    label = stringResource(R.string.vault_use_pin),
                    onClick = onUseCredential,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (ui.failedAttempts > 0 && !ui.permanentlyLost) {
            Row(
                Modifier.padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(5.dp).background(colors.onBackground.copy(alpha = 0.4f)))
                Text(
                    stringResource(R.string.vault_failed_attempts, ui.failedAttempts),
                    style = FoldTheme.typography.meta,
                    color = colors.onBackgroundMuted,
                    maxLines = 1,
                )
            }
        }

        ui.message?.takeIf { ui.failedAttempts > 0 }?.let { message ->
            Text(
                message,
                style = FoldTheme.typography.bodyS,
                color = colors.accentBodyText,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun UnlockedVault(
    state: VaultState.Unlocked,
    remainingMillis: Long,
    onLock: () -> Unit,
    onMoveFilesIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ground inverts. This is the vault's loudest signal and it is deliberate:
    // you should never be unsure whether the vault is open.
    CompositionLocalProvider(LocalFoldColors provides FoldLightColors) {
        val colors = FoldTheme.colors

        LazyColumn(
            modifier
                .fillMaxSize()
                .background(colors.background),
            contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.accent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.vault_unlocked),
                        style = FoldTheme.typography.label,
                        color = colors.accentInk,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.vault_timer, Formatting.countdown(remainingMillis)),
                        style = FoldTheme.typography.meta,
                        color = colors.accentInk,
                        maxLines = 1,
                    )
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bottomRule(colors.dividerStrong, FoldRules.sectionDivider)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        stringResource(
                            R.string.vault_summary,
                            state.entries.size,
                            Formatting.bytes(state.totalBytes),
                        ),
                        style = FoldTheme.typography.label,
                        color = colors.onBackground.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.vault_lock_now),
                        style = FoldTheme.typography.label,
                        color = colors.accentBodyText,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onLock)
                            .padding(vertical = 6.dp),
                    )
                }
            }

            items(state.entries, key = { it.blobId }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bottomRule(colors.divider)
                        .defaultMinSize(minHeight = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TypeBadge(
                        mark = entry.extensionBadge,
                        borderColor = colors.onBackground.copy(alpha = 0.4f),
                        textColor = colors.onBackground,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.displayName,
                            style = FoldTheme.typography.body,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${Formatting.bytes(entry.plaintextSize)} · ${
                                stringResource(
                                    R.string.vault_added,
                                    Formatting.date(entry.addedAtMillis),
                                )
                            }",
                            style = FoldTheme.typography.meta,
                            color = colors.onBackgroundMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    FoldInkButton(
                        label = stringResource(R.string.vault_move_in),
                        onClick = onMoveFilesIn,
                    )
                    Text(
                        stringResource(R.string.vault_ground_note),
                        style = FoldTheme.typography.bodyS,
                        color = colors.onBackground.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    // The honest sentence about deletion. It belongs in the
                    // product, next to the thing it qualifies.
                    Text(
                        stringResource(R.string.vault_deletion_note),
                        style = FoldTheme.typography.bodyS,
                        color = colors.onBackground.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}


/** The lit cells of the 5x5 lock mark, as indices into a row-major grid. */
private val LOCK_MARK = setOf(2, 6, 7, 8, 11, 13, 16, 17, 18, 21, 22, 23)
