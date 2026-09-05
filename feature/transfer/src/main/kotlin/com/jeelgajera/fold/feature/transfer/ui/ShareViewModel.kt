package com.jeelgajera.fold.feature.transfer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeelgajera.fold.feature.transfer.TransferRepository
import com.jeelgajera.fold.feature.transfer.TransferState
import com.jeelgajera.fold.feature.transfer.net.DiscoveredPeer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of the three share tabs is showing. */
enum class ShareTab { SEND, RECEIVE, QUICK }

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val repository: TransferRepository,
) : ViewModel() {

    val server: StateFlow<TransferState> = repository.state

    private val _tab = MutableStateFlow(ShareTab.SEND)
    val tab: StateFlow<ShareTab> = _tab.asStateFlow()

    /**
     * Peers on the network.
     *
     * `WhileSubscribed` rather than `Eagerly`: mDNS discovery holds a multicast
     * lock and wakes the radio, so it runs while the receive tab is on screen and
     * stops when it is not.
     */
    val peers: StateFlow<List<DiscoveredPeer>> = repository.peers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingCellularConfirmation = MutableStateFlow(false)
    val pendingCellularConfirmation: StateFlow<Boolean> = _pendingCellularConfirmation.asStateFlow()

    fun selectTab(next: ShareTab) {
        _tab.value = next
        if (next == ShareTab.SEND) repository.refreshNetwork()
    }

    fun toggleServer() {
        viewModelScope.launch {
            val state = repository.state.value
            if (state.isRunning || state.isStarting) {
                repository.stopServer()
            } else {
                val network = state.network
                if (network?.isCellularOnly == true) {
                    // Ask rather than refuse: tethering deliberately is a real use,
                    // it just must not happen because someone tapped a button
                    // labelled "share over Wi-Fi".
                    _pendingCellularConfirmation.value = true
                } else {
                    repository.startServer()
                }
            }
        }
    }

    fun confirmCellular() {
        _pendingCellularConfirmation.value = false
        viewModelScope.launch { repository.startServer(confirmedCellular = true) }
    }

    fun dismissCellular() {
        _pendingCellularConfirmation.value = false
    }

    fun dropClient(token: String) = repository.dropClient(token)
}
