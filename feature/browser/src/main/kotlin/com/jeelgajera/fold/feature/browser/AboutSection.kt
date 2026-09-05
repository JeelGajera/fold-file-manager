package com.jeelgajera.fold.feature.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.component.FoldSettingRow
import com.jeelgajera.fold.core.design.component.SectionHeading
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * Where a build came from, and how to report a problem with it.
 *
 * FOLD ships no crash reporter, which is a real privacy position and also means
 * a bug that nobody reports is a bug nobody hears about. This section is the
 * trade-off honoured properly rather than papered over: it shows the exact
 * commit the running build was made from, links to that commit in the source,
 * and opens a pre-filled issue with the environment already written out.
 *
 * The user sees and edits the whole report before it is sent. That is the entire
 * difference between this and a background uploader, and it is the reason this
 * approach does not weaken the "collects nothing" claim.
 */
@Composable
fun AboutSection(
    versionName: String,
    versionCode: String,
    gitSha: String,
    sourceUrl: String,
    issuesUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier.fillMaxWidth()) {
        SectionHeading(stringResource(R.string.about_group))

        FoldSettingRow(
            label = stringResource(R.string.about_version),
            help = stringResource(R.string.about_version_help),
            value = "$versionName ($versionCode)",
            onClick = {},
        )

        FoldSettingRow(
            label = stringResource(R.string.about_commit),
            help = stringResource(R.string.about_commit_help),
            value = gitSha,
            // Opens this exact commit in the repository, so "which build is this"
            // has a single unambiguous answer.
            onClick = { openUrl(context, "$sourceUrl/commit/$gitSha") },
        )

        FoldSettingRow(
            label = stringResource(R.string.about_source),
            help = stringResource(R.string.about_source_help),
            value = stringResource(R.string.about_open),
            onClick = { openUrl(context, sourceUrl) },
        )

        FoldSettingRow(
            label = stringResource(R.string.about_report),
            help = stringResource(R.string.about_report_help),
            value = stringResource(R.string.about_open),
            valueAccent = true,
            onClick = {
                openUrl(
                    context,
                    IssueReport.newIssueUrl(
                        issuesUrl = issuesUrl,
                        versionName = versionName,
                        versionCode = versionCode,
                        gitSha = gitSha,
                    ),
                )
            },
        )

        Text(
            stringResource(R.string.about_no_crash_reporter),
            style = FoldTheme.typography.bodyS,
            color = FoldTheme.colors.onBackgroundMuted,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Builds the pre-filled GitHub issue URL.
 *
 * Everything in the body is either build metadata or the device model -- the
 * things a maintainer needs and cannot guess. No file names, no paths, no
 * storage figures, nothing about what the user was looking at. The body opens in
 * a browser where they can read and edit it before submitting, which is the
 * point: the user is the one who decides what gets sent.
 */
object IssueReport {

    fun newIssueUrl(
        issuesUrl: String,
        versionName: String,
        versionCode: String,
        gitSha: String,
    ): String {
        val body = buildString {
            appendLine("<!-- Describe what happened, and what you expected instead. -->")
            appendLine()
            appendLine()
            appendLine("---")
            appendLine("**Build**")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            appendLine("| Version | $versionName ($versionCode) |")
            appendLine("| Commit | `$gitSha` |")
            appendLine("| Android | ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) |")
            appendLine("| Device | ${Build.MANUFACTURER} ${Build.MODEL} |")
        }

        return buildString {
            append(issuesUrl)
            append("/new?title=")
            append(Uri.encode(""))
            append("&body=")
            append(Uri.encode(body))
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // A device with no browser is unusual but not impossible. Failing silently
    // is better than crashing on the "report a problem" control.
    runCatching { context.startActivity(intent) }
}
