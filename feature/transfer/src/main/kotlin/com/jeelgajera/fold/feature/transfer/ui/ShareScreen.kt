package com.jeelgajera.fold.feature.transfer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.DotProgress
import com.jeelgajera.fold.core.design.component.FoldAccentButton
import com.jeelgajera.fold.core.design.component.FoldCallout
import com.jeelgajera.fold.core.design.component.FoldOutlineButton
import com.jeelgajera.fold.core.design.component.FoldTabStrip
import com.jeelgajera.fold.core.design.component.SectionHeading
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.util.Formatting
import com.jeelgajera.fold.feature.transfer.ActiveTransfer
import com.jeelgajera.fold.feature.transfer.R
import com.jeelgajera.fold.feature.transfer.TransferState
import com.jeelgajera.fold.feature.transfer.net.DiscoveredPeer
import com.jeelgajera.fold.feature.transfer.server.ServerSession

/**
 * The share screen: three tabs over one server.
 *
 * The send tab inverts to a full red block while the server is running. That is
 * the app's loudest state and it is meant to be -- a file server running on your
 * phone is not something to communicate with a small dot. Red never carries it
 * alone: the block is paired with the words "SHARING OVER WI-FI", the address in
 * Doto, and the PIN.
 */
@Composable
fun ShareScreen(
    onQuickShare: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.server.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val askCellular by viewModel.pendingCellularConfirmation.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        FoldTabStrip(
            options = listOf(
                stringResource(R.string.transfer_tab_send),
                stringResource(R.string.transfer_tab_receive),
                stringResource(R.string.transfer_tab_quick),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { viewModel.selectTab(ShareTab.entries[it]) },
        )

        when (tab) {
            ShareTab.SEND -> SendTab(
                state = state,
                askCellular = askCellular,
                onToggle = viewModel::toggleServer,
                onConfirmCellular = viewModel::confirmCellular,
                onDismissCellular = viewModel::dismissCellular,
                onDropClient = viewModel::dropClient,
            )

            ShareTab.RECEIVE -> ReceiveTab(peers = peers, networkName = state.network?.ssid)

            ShareTab.QUICK -> QuickTab(onSend = onQuickShare)
        }
    }
}

@Composable
private fun SendTab(
    state: TransferState,
    askCellular: Boolean,
    onToggle: () -> Unit,
    onConfirmCellular: () -> Unit,
    onDismissCellular: () -> Unit,
    onDropClient: (String) -> Unit,
) {
    val colors = FoldTheme.colors
    val running = state.isRunning

    LazyColumn(contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (running) colors.accent else colors.surface)
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 22.dp),
            ) {
                val ink = if (running) colors.accentInk else colors.onBackground

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            running -> stringResource(R.string.transfer_status_sharing)
                            state.isStarting -> stringResource(R.string.transfer_status_starting)
                            else -> stringResource(R.string.transfer_status_off)
                        },
                        style = FoldTheme.typography.label,
                        color = ink,
                        maxLines = 1,
                    )
                    // Eight dots: a running indicator that is a shape, not a hue.
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(8) { index ->
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        ink.copy(alpha = if (running && index % 2 == 0) 1f else 0.3f)
                                    ),
                            )
                        }
                    }
                }

                Text(
                    text = state.url,
                    style = FoldTheme.typography.displayM,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state.pin != null) {
                    Row(
                        Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.transfer_pin_label),
                            style = FoldTheme.typography.label,
                            color = ink.copy(alpha = 0.7f),
                        )
                        Text(
                            // Spaced so it can be read aloud across a room.
                            text = state.pin.toCharArray().joinToString(" "),
                            style = FoldTheme.typography.displayL,
                            color = ink,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = when {
                        running && state.network?.ssid != null ->
                            stringResource(R.string.transfer_help_running, state.network!!.ssid!!)
                        running -> stringResource(R.string.transfer_help_running_unknown_network)
                        else -> stringResource(R.string.transfer_help_off)
                    },
                    style = FoldTheme.typography.bodyS,
                    color = ink.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 14.dp),
                )

                Box(Modifier.padding(top = 18.dp)) {
                    if (running) {
                        // Inverted inside the red block: dark fill, red label.
                        FoldOutlineButton(
                            label = stringResource(R.string.transfer_stop),
                            onClick = onToggle,
                            minHeight = 48.dp,
                            borderColor = colors.accentInk,
                            contentColor = colors.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentInk),
                        )
                    } else {
                        FoldAccentButton(
                            label = if (state.isStarting) {
                                stringResource(R.string.transfer_cancel)
                            } else {
                                stringResource(R.string.transfer_start)
                            },
                            onClick = onToggle,
                            minHeight = 48.dp,
                        )
                    }
                }
            }
        }

        state.lastError?.let { message ->
            item { FoldCallout(tag = "WI-FI", text = message) }
        }

        if (askCellular) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.transfer_error_cellular),
                        style = FoldTheme.typography.bodyS,
                        color = colors.onBackground,
                    )
                    FoldAccentButton("SHARE ANYWAY", onConfirmCellular, minHeight = 48.dp)
                    FoldOutlineButton("NOT NOW", onDismissCellular, Modifier.fillMaxWidth())
                }
            }
        }

        state.activeTransfer?.let { transfer ->
            item { SectionHeading(stringResource(R.string.transfer_section_in_progress)) }
            item { TransferProgressRow(transfer) }
        }

        if (running) {
            item { SectionHeading(stringResource(R.string.transfer_section_connected)) }
            if (state.clients.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.transfer_no_clients),
                        style = FoldTheme.typography.bodyS,
                        color = colors.onBackgroundMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.clients) { session -> ClientRow(session, onDrop = { onDropClient(session.token) }) }
        }
    }
}

@Composable
private fun TransferProgressRow(transfer: ActiveTransfer) {
    val colors = FoldTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                transfer.name,
                style = FoldTheme.typography.body,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // The numeral is what carries the state; the red bar agrees with it.
            Text(
                "${(transfer.fraction * 100).toInt()}%",
                style = FoldTheme.typography.meta.copy(fontSize = 14.sp),
                color = colors.accent,
                maxLines = 1,
            )
        }

        DotProgress(
            progress = transfer.fraction,
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${Formatting.bytes(transfer.sentBytes)} / ${Formatting.bytes(transfer.totalBytes)}",
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
            )
            val rate = transfer.bytesPerSecond()
            val eta = Formatting.eta(transfer.remainingBytes(), rate)
            Text(
                listOfNotNull(Formatting.rate(rate), eta).joinToString(" · "),
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ClientRow(session: ServerSession, onDrop: () -> Unit) {
    val colors = FoldTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .bottomRule(colors.divider)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.accent))
        Column(Modifier.weight(1f)) {
            Text(session.label, style = FoldTheme.typography.body, color = colors.onBackground, maxLines = 1)
            Text(
                session.remoteAddress,
                style = FoldTheme.typography.meta,
                color = colors.onBackgroundMuted,
                maxLines = 1,
            )
        }
        FoldOutlineButton(
            label = stringResource(R.string.transfer_drop),
            onClick = onDrop,
            minHeight = 36.dp,
            modifier = Modifier.defaultMinSize(minWidth = 72.dp),
            borderColor = colors.onBackground.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun ReceiveTab(peers: List<DiscoveredPeer>, networkName: String?) {
    val colors = FoldTheme.colors
    LazyColumn(contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.dividerStrong, com.jeelgajera.fold.core.design.theme.FoldRules.sectionDivider)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            ) {
                Text(
                    text = networkName
                        ?.let { stringResource(R.string.transfer_scanning, it.uppercase()) }
                        ?: stringResource(R.string.transfer_scanning_unknown),
                    style = FoldTheme.typography.label,
                    color = colors.onBackground.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(colors.onBackground.copy(alpha = 0.1f)),
                )
            }
        }

        items(peers) { peer ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.divider)
                    .defaultMinSize(minHeight = 72.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(peer.name, style = FoldTheme.typography.body, color = colors.onBackground, maxLines = 1)
                    Text(
                        peer.url,
                        style = FoldTheme.typography.meta,
                        color = colors.onBackgroundMuted,
                        maxLines = 1,
                    )
                }
                Text(
                    stringResource(R.string.transfer_send_to),
                    style = FoldTheme.typography.label,
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }

        item {
            Text(
                stringResource(R.string.transfer_receive_note),
                style = FoldTheme.typography.bodyS,
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun QuickTab(onSend: () -> Unit) {
    val colors = FoldTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = FoldSpacing.dockClearance)) {
        FoldAccentButton(
            label = stringResource(R.string.transfer_quick_send),
            onClick = onSend,
            minHeight = 60.dp,
        )
        Text(
            stringResource(R.string.transfer_quick_note),
            style = FoldTheme.typography.bodyS,
            color = colors.onBackgroundMuted,
            modifier = Modifier.padding(16.dp),
        )
        // The one honest sentence about Quick Share, in the product rather than
        // in a release note nobody reads.
        FoldCallout(tag = "SHARE SHEET", text = stringResource(R.string.transfer_quick_limitation))
    }
}
