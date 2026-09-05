package com.jeelgajera.fold.feature.transfer

import android.content.Context
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.core.storage.provider.FileSystemProviderFactory
import com.jeelgajera.fold.feature.glyph.GlyphController
import com.jeelgajera.fold.feature.glyph.GlyphEvent
import com.jeelgajera.fold.feature.transfer.net.Discovery
import com.jeelgajera.fold.feature.transfer.net.NetworkAddress
import com.jeelgajera.fold.feature.transfer.net.NetworkState
import com.jeelgajera.fold.feature.transfer.server.FoldServer
import com.jeelgajera.fold.feature.transfer.server.ServerAuth
import com.jeelgajera.fold.feature.transfer.server.ServerEvent
import com.jeelgajera.fold.feature.transfer.server.ServerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A transfer in flight, as the send screen draws it. */
data class ActiveTransfer(
    val name: String,
    val sentBytes: Long,
    val totalBytes: Long,
    val startedAtMillis: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (sentBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** Averaged over the whole transfer, which is steadier to read than an instantaneous rate. */
    fun bytesPerSecond(now: Long = System.currentTimeMillis()): Long {
        val elapsed = (now - startedAtMillis).coerceAtLeast(1)
        return sentBytes * 1000 / elapsed
    }

    fun remainingBytes(): Long = (totalBytes - sentBytes).coerceAtLeast(0)
}

/** Everything the share screens render. */
data class TransferState(
    val isRunning: Boolean = false,
    val isStarting: Boolean = false,
    val host: String? = null,
    val port: Int = 8080,
    val pin: String? = null,
    val network: NetworkState? = null,
    val clients: List<ServerSession> = emptyList(),
    val activeTransfer: ActiveTransfer? = null,
    val lastError: String? = null,
) {
    val url: String
        get() = host?.let { "http://$it:$port" } ?: "http://-.-.-.-:$port"
}

/**
 * Owns the server's lifecycle and everything the share screens read.
 *
 * A singleton because there is exactly one server, and both the UI and the
 * foreground service need to see the same state -- the service reads it to draw
 * its notification, the screens read it to draw the send tab, and the widget
 * reads it to draw the toggle.
 */
@Singleton
class TransferRepository @Inject constructor(
    private val context: Context,
    private val providerFactory: FileSystemProviderFactory,
    private val settings: SettingsRepository,
    private val glyph: GlyphController,
    private val scope: CoroutineScope,
) {
    private val auth = ServerAuth()
    private val discovery = Discovery(context)

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var server: FoldServer? = null

    val peers = discovery.discover()

    /**
     * Starts the server.
     *
     * @param confirmedCellular the user explicitly agreed to serve over cellular.
     *   Without it, a phone that is not on Wi-Fi refuses -- "share over Wi-Fi"
     *   must not quietly become "share over the carrier's network", which on some
     *   carriers means a publicly routable address.
     */
    suspend fun startServer(confirmedCellular: Boolean = false): Result<Unit> {
        val network = NetworkAddress.current(context)
        val preferences = settings.settings.first()

        if (network.isCellularOnly && !confirmedCellular) {
            _state.value = _state.value.copy(
                network = network,
                lastError = context.getString(R.string.transfer_error_cellular),
            )
            return Result.failure(IllegalStateException("Not on Wi-Fi"))
        }

        val host = network.ipv4 ?: run {
            _state.value = _state.value.copy(
                network = network,
                lastError = context.getString(R.string.transfer_error_no_address),
            )
            return Result.failure(IllegalStateException("No usable address"))
        }

        _state.value = _state.value.copy(isStarting = true, network = network, lastError = null)

        // A fresh PIN every session. A PIN that survives a restart is a PIN that
        // ends up written on a sticky note.
        val pin = auth.regeneratePin()

        val instance = FoldServer(
            provider = providerFactory.current,
            auth = auth,
            // The server is rooted at shared storage, not at "/". Browsing the
            // whole device in the app and exposing it to the network are not the
            // same permission, and the second one is not implied by the first.
            serverRoot = FsPath.raw(android.os.Environment.getExternalStorageDirectory().path),
            allowUpload = preferences.serverAllowUpload,
            allowDelete = preferences.serverAllowDelete,
            requirePin = preferences.serverRequirePin,
            onEvent = ::onServerEvent,
        )

        return try {
            instance.start(host, preferences.serverPort)
            server = instance
            discovery.advertise(preferences.serverPort)

            _state.value = _state.value.copy(
                isRunning = true,
                isStarting = false,
                host = host,
                port = preferences.serverPort,
                pin = if (preferences.serverRequirePin) pin else null,
                network = network,
            )
            startIdleWatchdog(preferences.serverIdleTimeoutMinutes)
            Result.success(Unit)
        } catch (e: Exception) {
            // A port already in use is the common case, and the message says so
            // rather than "failed to start".
            _state.value = TransferState(
                network = network,
                lastError = context.getString(R.string.transfer_error_port, preferences.serverPort),
            )
            Result.failure(e)
        }
    }

    suspend fun stopServer() {
        server?.stop()
        server = null
        discovery.unadvertise()
        glyph.stop()
        _state.value = TransferState(network = _state.value.network)
    }

    fun dropClient(token: String) {
        auth.revoke(token)
        _state.value = _state.value.copy(clients = auth.activeSessions())
    }

    fun refreshNetwork() {
        _state.value = _state.value.copy(network = NetworkAddress.current(context))
    }

    // --- internals -------------------------------------------------------

    private fun onServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.ClientConnected -> {
                _state.value = _state.value.copy(clients = auth.activeSessions())
                scope.launch { glyph.play(GlyphEvent.INCOMING_CONNECTION) }
            }

            is ServerEvent.AuthRejected -> {
                _state.value = _state.value.copy(clients = auth.activeSessions())
            }

            is ServerEvent.TransferStarted -> {
                _state.value = _state.value.copy(
                    activeTransfer = ActiveTransfer(
                        name = event.name,
                        sentBytes = 0,
                        totalBytes = event.totalBytes,
                        startedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }

            is ServerEvent.TransferProgress -> {
                val current = _state.value.activeTransfer ?: return
                val updated = current.copy(sentBytes = event.sentBytes)
                _state.value = _state.value.copy(activeTransfer = updated)
                // Throttled: this fires per 64KB chunk, and driving the glyph at
                // that rate would spend more time on IPC than on the transfer.
                if (shouldReportProgress(updated)) {
                    scope.launch { glyph.play(GlyphEvent.TRANSFER_PROGRESS, updated.fraction) }
                }
            }

            is ServerEvent.TransferFinished -> {
                _state.value = _state.value.copy(activeTransfer = null)
                scope.launch { glyph.play(GlyphEvent.TRANSFER_COMPLETE) }
            }
        }
    }

    private var lastReportedPercent = -1

    private fun shouldReportProgress(transfer: ActiveTransfer): Boolean {
        val percent = (transfer.fraction * 100).toInt()
        if (percent == lastReportedPercent) return false
        lastReportedPercent = percent
        return true
    }

    /**
     * Stops the server once nothing has talked to it for a while.
     *
     * The most likely way a LAN file server becomes a problem is being left on and
     * forgotten, so idleness is treated as a reason to stop rather than as a
     * neutral state.
     */
    private fun startIdleWatchdog(idleMinutes: Int) {
        scope.launch {
            while (isActive && _state.value.isRunning) {
                delay(60_000)
                if (_state.value.activeTransfer == null && auth.idleFor(idleMinutes)) {
                    stopServer()
                    return@launch
                }
            }
        }
    }
}
