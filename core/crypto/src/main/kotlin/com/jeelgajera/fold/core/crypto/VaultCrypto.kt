package com.jeelgajera.fold.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The vault's cryptography.
 *
 * ### Shape
 *
 * Every file gets its own 256-bit data key (a DEK), generated fresh and used
 * once. That DEK is wrapped with a key-encryption key (the KEK) that lives inside
 * the Android Keystore and never leaves it.
 *
 * The two-level scheme earns its complexity:
 * - unlocking the vault is one Keystore operation, not one per file;
 * - a single file can be re-keyed, exported or deleted without touching the rest;
 * - rotation re-wraps a few hundred small DEKs rather than re-encrypting
 *   gigabytes of payload.
 *
 * ### The KEK
 *
 * Generated with `setUserAuthenticationRequired(true)`, so the hardware itself
 * refuses to use it until the user has authenticated. That is the difference
 * between a vault and a folder with a password check painted in front of it: an
 * attacker holding the unlocked device's storage cannot ask the Keystore to
 * unwrap anything without a biometric or the device credential.
 *
 * `setInvalidatedByBiometricEnrollment(true)` means enrolling a new fingerprint
 * destroys the key and the blobs become permanently unreadable. That is the
 * correct trade -- someone who can add a finger to a borrowed phone must not
 * inherit the vault with it -- and [VaultException.KeyInvalidated] exists so the
 * UI can say so plainly instead of looping on a prompt that will never succeed.
 *
 * ### Key aliases and rotation
 *
 * KEKs are versioned: `fold.vault.kek.1`, `.2`, and so on. Each blob records the
 * alias that wrapped its DEK, so rotation does not have to be atomic across the
 * whole vault. A blob wrapped under an older alias keeps working until it is
 * re-wrapped, and an interrupted rotation leaves a readable vault rather than a
 * bricked one. The old key is only destroyed once nothing references it.
 *
 * ### The blob format
 *
 * ```
 * magic       4 bytes    "FVLT"
 * version     1 byte     2
 * aliasLen    1 byte
 * alias       n bytes    US-ASCII, the KEK that wrapped this DEK
 * wrapIvLen   1 byte
 * wrapIv      n bytes
 * wrapLen     2 bytes    big-endian
 * wrappedDek  n bytes
 * dataIv      12 bytes
 * payload     ...        AES-256-GCM, tag included
 * ```
 *
 * The header is authenticated indirectly: altering the alias, the wrapped DEK or
 * either IV makes a tag check fail, surfacing as [VaultException.Tampered]
 * rather than as plausible-looking garbage.
 */
object VaultCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS_PREFIX = "fold.vault.kek."

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val DEK_BYTES = 32

    private val MAGIC = "FVLT".toByteArray(Charsets.US_ASCII)
    const val FORMAT_VERSION: Byte = 2

    /** How long one authentication keeps the KEK usable. The vault auto-locks sooner. */
    const val DEFAULT_AUTH_VALIDITY_SECONDS = 300

    private val random = SecureRandom()

    // --- key management --------------------------------------------------

    fun aliasFor(generation: Int): String = "$ALIAS_PREFIX$generation"

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun aliasExists(alias: String): Boolean = keyStore().containsAlias(alias)

    /** Every FOLD KEK currently in the Keystore, oldest generation first. */
    fun existingAliases(): List<String> =
        keyStore().aliases().toList()
            .filter { it.startsWith(ALIAS_PREFIX) }
            .sortedBy { it.removePrefix(ALIAS_PREFIX).toIntOrNull() ?: 0 }

    /**
     * Creates the KEK for [alias] if it is not already there.
     *
     * @param authValiditySeconds how long one authentication is good for at the
     *   hardware level. A backstop -- the vault's own auto-lock is shorter.
     */
    fun ensureKek(alias: String, authValiditySeconds: Int = DEFAULT_AUTH_VALIDITY_SECONDS) {
        if (keyStore().containsAlias(alias)) return

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        authValiditySeconds,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()
            )
            generateKey()
        }
    }

    /** Destroys one KEK. Any blob still wrapped under it becomes unreadable. */
    fun destroyKek(alias: String) {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun secretKey(alias: String): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey
            ?: throw VaultException.KeyUnavailable("The vault key '$alias' is not available")

    /**
     * A cipher initialised for wrapping under [alias], ready for `BiometricPrompt`.
     *
     * Passing the cipher *through* the prompt is what binds the authentication to
     * this specific operation. Calling the prompt and then using the key
     * separately on success would let anything that can fake the callback skip
     * the hardware check entirely -- the cipher is the proof, not the callback.
     */
    fun wrapCipher(alias: String): Cipher = try {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey(alias)) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw VaultException.KeyInvalidated()
    }

    /** The unwrapping counterpart, for a blob's recorded alias and IV. */
    fun unwrapCipher(alias: String, wrapIv: ByteArray): Cipher = try {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(GCM_TAG_BITS, wrapIv))
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw VaultException.KeyInvalidated()
    }

    // --- per-file operations ---------------------------------------------

    /**
     * Encrypts [source] into [sink].
     *
     * [authenticatedWrapCipher] must already have been through `BiometricPrompt`.
     * This function does not authenticate; it consumes an authentication that
     * happened. [alias] must be the alias that cipher was built from -- it is
     * recorded in the header so the blob can be opened after a rotation.
     *
     * @return plaintext bytes consumed.
     */
    fun encrypt(
        source: InputStream,
        sink: OutputStream,
        alias: String,
        authenticatedWrapCipher: Cipher,
    ): Long {
        val dek = ByteArray(DEK_BYTES).also(random::nextBytes)
        val wrapped = try {
            authenticatedWrapCipher.doFinal(dek)
        } catch (e: UserNotAuthenticatedException) {
            dek.fill(0)
            throw VaultException.NotAuthenticated()
        } catch (e: KeyPermanentlyInvalidatedException) {
            dek.fill(0)
            throw VaultException.KeyInvalidated()
        }
        val wrapIv = authenticatedWrapCipher.iv

        val dataIv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val dataCipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(dek, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, dataIv),
            )
        }
        // The DEK is in the cipher now; drop this process's copy immediately. The
        // JVM promises nothing about the page ever being wiped, so this narrows
        // the exposure window rather than closing it -- and the in-app copy says
        // exactly that rather than implying more.
        dek.fill(0)

        writeHeader(sink, alias, wrapIv, wrapped, dataIv)

        var total = 0L
        CipherOutputStream(sink, dataCipher).use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
            }
        }
        return total
    }

    /**
     * Opens [source] as plaintext.
     *
     * GCM verifies its tag as the final block is read, so a truncated or altered
     * blob throws at the end of the stream rather than handing back partial
     * plaintext that looks complete. Callers must therefore read to EOF and treat
     * an exception at that point as corruption, not as an I/O hiccup.
     */
    fun decrypt(source: InputStream): InputStream {
        val header = readHeader(source)
        val dek = try {
            unwrapCipher(header.alias, header.wrapIv).doFinal(header.wrappedDek)
        } catch (e: UserNotAuthenticatedException) {
            throw VaultException.NotAuthenticated()
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw VaultException.KeyInvalidated()
        } catch (e: VaultException) {
            throw e
        } catch (e: Exception) {
            throw VaultException.Tampered("The wrapped key did not verify", e)
        }

        val dataCipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(dek, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, header.dataIv),
            )
        }
        dek.fill(0)
        return CipherInputStream(source, dataCipher)
    }

    /**
     * Re-wraps one blob's DEK under [newAlias], rewriting only the header.
     *
     * The payload is copied through untouched, which is what makes rotating a
     * 200GB vault a matter of moving headers rather than re-encrypting files.
     */
    fun rewrapHeader(
        source: InputStream,
        sink: OutputStream,
        newAlias: String,
        authenticatedWrapCipher: Cipher,
    ) {
        val header = readHeader(source)
        val dek = try {
            unwrapCipher(header.alias, header.wrapIv).doFinal(header.wrappedDek)
        } catch (e: UserNotAuthenticatedException) {
            throw VaultException.NotAuthenticated()
        }

        val rewrapped = authenticatedWrapCipher.doFinal(dek)
        dek.fill(0)

        // The data IV and the payload are unchanged: the DEK did not change, only
        // the key that protects it.
        writeHeader(sink, newAlias, authenticatedWrapCipher.iv, rewrapped, header.dataIv)
        source.copyTo(sink, bufferSize = 64 * 1024)
    }

    // --- header ----------------------------------------------------------

    data class BlobHeader(
        val version: Byte,
        val alias: String,
        val wrapIv: ByteArray,
        val wrappedDek: ByteArray,
        val dataIv: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is BlobHeader && version == other.version && alias == other.alias &&
                wrapIv.contentEquals(other.wrapIv) &&
                wrappedDek.contentEquals(other.wrappedDek) &&
                dataIv.contentEquals(other.dataIv)

        override fun hashCode(): Int {
            var result = version.toInt()
            result = 31 * result + alias.hashCode()
            result = 31 * result + wrapIv.contentHashCode()
            result = 31 * result + wrappedDek.contentHashCode()
            result = 31 * result + dataIv.contentHashCode()
            return result
        }
    }

    fun writeHeader(
        sink: OutputStream,
        alias: String,
        wrapIv: ByteArray,
        wrappedDek: ByteArray,
        dataIv: ByteArray,
    ) {
        val aliasBytes = alias.toByteArray(Charsets.US_ASCII)
        require(aliasBytes.size in 1..255) { "Key alias must fit in one byte of length" }
        require(wrapIv.size in 1..255) { "Wrap IV must fit in one byte of length" }
        require(dataIv.size == GCM_IV_BYTES) { "Data IV must be $GCM_IV_BYTES bytes" }
        require(wrappedDek.size in 1..4096) { "Wrapped DEK is implausible" }

        sink.write(MAGIC)
        sink.write(FORMAT_VERSION.toInt())
        sink.write(aliasBytes.size)
        sink.write(aliasBytes)
        sink.write(wrapIv.size)
        sink.write(wrapIv)
        sink.write((wrappedDek.size ushr 8) and 0xFF)
        sink.write(wrappedDek.size and 0xFF)
        sink.write(wrappedDek)
        sink.write(dataIv)
    }

    /**
     * Reads and validates the header, leaving [source] positioned at the payload.
     *
     * Every length is bounds-checked before it is used to allocate. A blob is a
     * file on disk that another process could have written, so a declared length
     * of two gigabytes must be a rejection rather than an allocation.
     */
    fun readHeader(source: InputStream): BlobHeader {
        val magic = source.readExactly(4)
        if (!magic.contentEquals(MAGIC)) throw VaultException.Tampered("Not a vault blob")

        val version = source.readByteOrThrow()
        if (version != FORMAT_VERSION) {
            throw VaultException.Tampered("Unsupported vault blob version $version")
        }

        val aliasLength = source.readByteOrThrow().toInt() and 0xFF
        if (aliasLength == 0) throw VaultException.Tampered("Bad key alias length")
        val alias = String(source.readExactly(aliasLength), Charsets.US_ASCII)
        if (!alias.startsWith(ALIAS_PREFIX)) throw VaultException.Tampered("Unknown key alias")

        val wrapIvLength = source.readByteOrThrow().toInt() and 0xFF
        if (wrapIvLength == 0) throw VaultException.Tampered("Bad key IV length")
        val wrapIv = source.readExactly(wrapIvLength)

        val high = source.readByteOrThrow().toInt() and 0xFF
        val low = source.readByteOrThrow().toInt() and 0xFF
        val wrappedLength = (high shl 8) or low
        if (wrappedLength !in 1..4096) throw VaultException.Tampered("Bad wrapped key length")
        val wrappedDek = source.readExactly(wrappedLength)

        val dataIv = source.readExactly(GCM_IV_BYTES)

        return BlobHeader(version, alias, wrapIv, wrappedDek, dataIv)
    }

    private fun InputStream.readByteOrThrow(): Byte {
        val value = read()
        if (value < 0) throw VaultException.Tampered("The blob ends inside its header")
        return value.toByte()
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(out, read, count - read)
            if (n < 0) throw VaultException.Tampered("The blob ends earlier than its header claims")
            read += n
        }
        return out
    }
}

/** Failures a vault caller is expected to handle rather than crash on. */
sealed class VaultException(message: String, cause: Throwable? = null) : IOException(message, cause) {

    /** No authentication, or the window expired. Show the prompt again. */
    class NotAuthenticated : VaultException("Unlock the vault to continue")

    /**
     * The Keystore destroyed the key, almost always because a biometric was
     * enrolled. The blobs cannot be opened and no amount of re-prompting helps,
     * so the UI must say so rather than loop.
     */
    class KeyInvalidated : VaultException(
        "The vault key was invalidated, most likely because a new fingerprint was " +
            "added. The encrypted files can no longer be opened."
    )

    class KeyUnavailable(message: String, cause: Throwable? = null) : VaultException(message, cause)

    /** The blob is not what it claims to be, or has been altered. */
    class Tampered(message: String, cause: Throwable? = null) : VaultException(message, cause)
}
