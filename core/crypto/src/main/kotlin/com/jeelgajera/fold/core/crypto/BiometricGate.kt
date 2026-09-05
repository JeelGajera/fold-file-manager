package com.jeelgajera.fold.core.crypto

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the device can currently do about authentication. */
enum class AuthAvailability {
    /** A strong biometric is enrolled and usable. */
    BIOMETRIC,

    /** No biometric, but a PIN, pattern or password exists. */
    DEVICE_CREDENTIAL,

    /**
     * The device has no screen lock at all.
     *
     * The vault cannot exist here: a Keystore key that requires user
     * authentication cannot be generated on a device with nothing to
     * authenticate against. FOLD says so rather than offering a vault that would
     * fail at the first write.
     */
    NONE,
}

/** How the user authenticated, so the UI can name it in the unlocked state. */
data class AuthResult(val cipher: Cipher, val usedBiometric: Boolean)

/**
 * The bridge between `BiometricPrompt` and the Keystore.
 *
 * The important detail is that the [Cipher] goes *into* the prompt and comes back
 * out of it. The Keystore only releases the key for the cipher the user actually
 * authenticated for, which means an attacker who can fake the success callback
 * gains nothing: they still have no usable cipher. Authenticating first and then
 * using the key separately -- the shape most tutorials show -- has exactly that
 * hole.
 */
class BiometricGate {

    fun availability(activity: FragmentActivity): AuthAvailability {
        val manager = BiometricManager.from(activity)
        val strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strong == BiometricManager.BIOMETRIC_SUCCESS) return AuthAvailability.BIOMETRIC

        val credential = manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (credential == BiometricManager.BIOMETRIC_SUCCESS) return AuthAvailability.DEVICE_CREDENTIAL

        return AuthAvailability.NONE
    }

    /**
     * Shows the prompt and returns the authenticated cipher.
     *
     * Cancelling -- back, or the "Use PIN instead" path failing -- resumes with
     * [VaultException.NotAuthenticated] rather than hanging, and cancelling the
     * coroutine dismisses the prompt.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeText: String,
    ): AuthResult = suspendCancellableCoroutine { continuation ->
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated == null) {
                        // Success without a bound cipher means the key was never
                        // unlocked. Refusing here is the whole point of passing
                        // the cipher through the prompt.
                        continuation.resumeWithException(
                            VaultException.KeyUnavailable("Authentication returned no usable key")
                        )
                        return
                    }
                    continuation.resume(
                        AuthResult(
                            cipher = authenticated,
                            usedBiometric = result.authenticationType ==
                                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC,
                        )
                    )
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (!continuation.isActive) return
                    continuation.resumeWithException(
                        when (code) {
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            ->
                                VaultException.KeyUnavailable(message.toString())

                            else -> VaultException.NotAuthenticated()
                        }
                    )
                }

                // A rejected finger is not a failure of the whole attempt: the
                // prompt stays up and the user tries again. Only onAuthenticationError
                // ends it.
                override fun onAuthenticationFailed() = Unit
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // A crypto-backed prompt cannot combine BIOMETRIC_STRONG with
            // DEVICE_CREDENTIAL in one call, so the negative button carries the
            // credential fallback and the caller re-prompts with it.
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeText)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    /**
     * The device-credential path, for a phone with no enrolled biometric or a
     * user who chose "Use PIN instead".
     *
     * This prompt is not crypto-bound -- the platform does not allow a
     * `CryptoObject` with `DEVICE_CREDENTIAL` on every level -- so the key's own
     * `setUserAuthenticationParameters` window is what actually authorises the
     * subsequent Keystore call. The cipher is built *after* success and will
     * throw `UserNotAuthenticatedException` if the platform disagreed, which is
     * the check that matters.
     */
    suspend fun authenticateWithCredential(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(info)
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
