package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.component.FoldAccentButton
import com.jeelgajera.fold.core.design.component.FoldOutlineButton
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * The rationale screen that stands in front of All Files Access.
 *
 * FOLD never opens the system permission screen cold. Three steps: what the
 * problem is, what the app reads and does not send, and what Android is about to
 * ask. Only after the third does the system screen open.
 *
 * The second step is the one that matters most and it is the one an app is most
 * tempted to skip. It states plainly that file *contents* are read during a
 * contents search and at no other time, and that nothing leaves the device --
 * claims the rest of the codebase has to keep, and does.
 *
 * The secondary button is never a dead end. At every step it leads somewhere
 * real: skip, an explanation, or the folder picker.
 */
@Composable
fun OnboardingScreen(
    canRequestAllFiles: Boolean,
    onRequestAllFiles: () -> Unit,
    onPickFolders: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    val colors = FoldTheme.colors

    Column(modifier.fillMaxSize()) {
        // Three bars rather than dots: progress you can count without focusing.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(
                            if (index <= step) colors.accent
                            else colors.onBackground.copy(alpha = 0.18f)
                        ),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_step, step + 1),
                style = FoldTheme.typography.meta,
                color = colors.accent,
                maxLines = 1,
            )
            Text(
                stringResource(
                    when (step) {
                        0 -> R.string.onboarding_title_1
                        1 -> R.string.onboarding_title_2
                        else -> R.string.onboarding_title_3
                    }
                ),
                style = FoldTheme.typography.titleL,
                color = colors.onBackground,
                modifier = Modifier.padding(vertical = 14.dp).widthIn(max = 320.dp),
            )
            Text(
                stringResource(
                    when (step) {
                        0 -> R.string.onboarding_body_1
                        1 -> R.string.onboarding_body_2
                        else -> R.string.onboarding_body_3
                    }
                ),
                style = FoldTheme.typography.body,
                color = colors.onBackground.copy(alpha = 0.78f),
                modifier = Modifier.widthIn(max = 320.dp),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp)
                    .height(FoldRules.sectionDivider)
                    .background(colors.dividerStrong),
            )

            points(step).forEach { (mark, text) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bottomRule(colors.onBackground.copy(alpha = 0.1f))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = mark,
                        style = FoldTheme.typography.meta,
                        // "SENDS" and "NEVER" are the reassuring marks and get the
                        // softer accent; the rest get the full red.
                        color = if (mark == "SENDS" || mark == "NEVER") {
                            colors.accentBodyText
                        } else {
                            colors.accent
                        },
                        maxLines = 1,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        text = text,
                        style = FoldTheme.typography.bodyS,
                        color = colors.onBackground.copy(alpha = 0.78f),
                    )
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val finalStep = step == 2
            FoldAccentButton(
                label = if (finalStep) {
                    stringResource(R.string.onboarding_grant)
                } else {
                    stringResource(R.string.onboarding_continue)
                },
                onClick = {
                    if (finalStep) onRequestAllFiles() else step++
                },
                // Some OEM builds have no All Files Access screen at all. Offering
                // a button that opens nothing would be worse than not offering it.
                enabled = !finalStep || canRequestAllFiles,
                minHeight = 56.dp,
            )
            FoldOutlineButton(
                label = stringResource(
                    when (step) {
                        0 -> R.string.onboarding_skip
                        1 -> R.string.onboarding_why
                        else -> R.string.onboarding_pick_folders
                    }
                ),
                onClick = {
                    when (step) {
                        0 -> onSkip()
                        1 -> step = 0
                        else -> onPickFolders()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (step == 2 && !canRequestAllFiles) {
                Text(
                    stringResource(R.string.onboarding_unavailable),
                    style = FoldTheme.typography.bodyS,
                    color = colors.accentBodyText,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** The three supporting lines under each step's body copy. */
private fun points(step: Int): List<Pair<String, String>> = when (step) {
    0 -> listOf(
        ".md" to "Notes you downloaded but cannot attach",
        ".apk" to "Sideloads sitting in Download",
        ".log" to "Diagnostics an app wrote and forgot",
    )

    1 -> listOf(
        "READS" to "Names, sizes, dates, folder structure",
        "READS" to "File contents only during a contents search",
        "SENDS" to "Nothing — no network calls at all",
    )

    else -> listOf(
        "NOW" to "Android shows its own permission screen",
        "LATER" to "Revoke it any time in system settings",
        "NEVER" to "FOLD will not ask twice",
    )
}
