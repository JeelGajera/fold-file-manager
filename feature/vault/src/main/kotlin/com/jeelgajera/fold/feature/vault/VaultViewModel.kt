package com.jeelgajera.fold.feature.vault

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeelgajera.fold.core.crypto.AuthAvailability
import com.jeelgajera.fold.core.crypto.BiometricGate
import com.jeelgajera.fold.core.crypto.VaultCrypto
import com.jeelgajera.fold.core.crypto.VaultException
import com.jeelgajera.fold.core.crypto.VaultRepository
import com.jeelgajera.fold.core.crypto.VaultState
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.feature.glyph.GlyphController
import com.jeelgajera.fold.feature.glyph.GlyphEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the vault screen needs beyond the repository's own state. */
data class VaultUiState(
    val failedAttempts: Int = 0,
    val remainingMillis: Long = 0,
    val autoLockMinutes: Int = 5,
    val availability: AuthAvailability = AuthAvailability.NONE,
    /** A sentence the screen can show. Null when nothing has gone wrong. */
    val message: String? = null,
    /** True when the key is gone for good and re-prompting cannot help. */
    val permanentlyLost: Boolean = false,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val gate: BiometricGate,
    private val settings: SettingsRepository,
    private val glyph: GlyphController,
) : ViewModel() {

    val vault: StateFlow<VaultState> = repository.state

    private val _ui = MutableStateFlow(VaultUiState())
    val ui: StateFlow<VaultUiState> = _ui.asStateFlow()

    private var countdown: Job? = null

    fun refreshAvailability(activity: FragmentActivity) {
        _ui.value = _ui.value.copy(availability = gate.availability(activity))
    }

    /**
     * Unlocks with a biometric.
     *
     * The cipher goes through the prompt and comes back authenticated -- see
     * [BiometricGate]. Nothing here trusts a success callback on its own.
     */
    fun unlock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negative: String,
    ) {
        viewModelScope.launch {
            val minutes = settings.settings.first().vaultAutoLockMinutes
            try {
                val alias = repository.currentAlias
                VaultCrypto.ensureKek(alias, authValiditySeconds = minutes * 60)

                gate.authenticate(activity, VaultCrypto.wrapCipher(alias), title, subtitle, negative)

                repository.unlock().fold(
                    onSuccess = {
                        _ui.value = _ui.value.copy(
                            failedAttempts = 0,
                            message = null,
                            autoLockMinutes = minutes,
                        )
                        startCountdown(minutes)
                    },
                    onFailure = ::onUnlockFailure,
                )
            } catch (e: VaultException) {
                onUnlockFailure(e)
            }
        }
    }

    /** The device-credential path, for "USE PIN INSTEAD" and for phones with no biometric. */
    fun unlockWithCredential(activity: FragmentActivity, title: String, subtitle: String) {
        viewModelScope.launch {
            val minutes = settings.settings.first().vaultAutoLockMinutes
            if (!gate.authenticateWithCredential(activity, title, subtitle)) {
                onUnlockFailure(VaultException.NotAuthenticated())
                return@launch
            }
            repository.unlock().fold(
                onSuccess = {
                    _ui.value = _ui.value.copy(failedAttempts = 0, message = null, autoLockMinutes = minutes)
                    startCountdown(minutes)
                },
                onFailure = ::onUnlockFailure,
            )
        }
    }

    fun lock() {
        countdown?.cancel()
        repository.lock()
        _ui.value = _ui.value.copy(remainingMillis = 0)
    }

    /**
     * Locks when the app leaves the foreground.
     *
     * Called from the activity's lifecycle rather than from a timer, because "I
     * switched apps for a second" is exactly when a shoulder-surfer gets the
     * phone.
     */
    fun onAppBackgrounded() = lock()

    private fun onUnlockFailure(error: Throwable) {
        val attempts = _ui.value.failedAttempts + 1
        _ui.value = _ui.value.copy(
            failedAttempts = attempts,
            message = error.message,
            // A key destroyed by a new biometric enrolment cannot be recovered, so
            // the screen must stop offering to try again.
            permanentlyLost = error is VaultException.KeyInvalidated,
        )
        viewModelScope.launch { glyph.play(GlyphEvent.VAULT_UNLOCK_FAILURE) }
    }

    /** Drives the countdown in the unlocked header, and locks when it reaches zero. */
    private fun startCountdown(minutes: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (isActive) {
                val state = repository.state.value as? VaultState.Unlocked ?: return@launch
                val remaining = state.remainingMillis(minutes)
                _ui.value = _ui.value.copy(remainingMillis = remaining)
                if (remaining <= 0) {
                    lock()
                    return@launch
                }
                delay(1_000)
            }
        }
    }
}
