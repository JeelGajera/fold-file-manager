package com.jeelgajera.fold.core.crypto

import android.content.Context
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.provider.FileSystemProvider
import com.jeelgajera.fold.core.storage.provider.VaultLocations
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher

/** One file inside the vault, as the list screen sees it. */
@Serializable
data class VaultEntry(
    /** Opaque blob file name. Random, so the directory listing reveals nothing. */
    val blobId: String,
    val displayName: String,
    val mimeType: String,
    val plaintextSize: Long,
    val addedAtMillis: Long,
) {
    /**
     * The badge the vault list draws.
     *
     * A leading dot is a hidden-file marker, not an extension, so `.recovery`
     * gets the `?` badge rather than being read as a "recovery" type -- the same
     * rule `FsPath.extension` follows, so the two never disagree on screen.
     */
    val extensionBadge: String
        get() {
            val dot = displayName.lastIndexOf('.')
            if (dot <= 0) return "?"
            return displayName.substring(dot + 1).uppercase().take(4).ifEmpty { "?" }
        }
}

@Serializable
private data class VaultManifest(val entries: List<VaultEntry> = emptyList())

/**
 * The vault: encrypted files, their manifest, and the lock state around both.
 *
 * ### What is hidden, and where
 *
 * Blobs are written to app-private storage under a random name, so the directory
 * listing gives away nothing -- not the file names, not the types, not even the
 * extensions. The names live in a manifest that is itself encrypted with the same
 * scheme, because a plaintext index of "passport-scan.pdf, recovery-codes.txt"
 * would undo most of the point.
 *
 * ### What the vault is excluded from
 *
 * Index, search, thumbnails, widgets and the LAN server, all of it enforced one
 * level down: `PathGuard` denies `VaultLocations.blobDir` unconditionally, so a
 * new HTTP route or a bug in an existing one cannot reach in. This class does not
 * have to remember to be careful.
 *
 * ### On deletion
 *
 * [moveIn] deletes the original only after the ciphertext has been written *and*
 * read back and verified. Even then, deletion on flash storage is best-effort:
 * the flash translation layer may keep the old blocks readable until wear
 * levelling gets to them, and nothing an app can do changes that. The vault
 * screen says so in those words rather than implying secure erasure.
 */
class VaultRepository(
    private val context: Context,
    private val provider: FileSystemProvider,
    private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    private val blobDir: File get() = VaultLocations.blobDir(context)
    private val manifestFile: File get() = File(blobDir, MANIFEST_NAME)
    private val aliasFile: File get() = File(blobDir, ALIAS_NAME)

    private val _state = MutableStateFlow<VaultState>(VaultState.Locked())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /** The KEK generation currently used for new writes. */
    val currentAlias: String
        get() = aliasFile.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
            ?: VaultCrypto.aliasFor(1)

    /** True once a vault exists on this device. */
    fun exists(): Boolean = VaultCrypto.aliasExists(currentAlias) && manifestFile.exists()

    /** Creates the key and an empty manifest. Requires one authentication. */
    suspend fun create(authenticatedWrapCipher: Cipher, alias: String) = withContext(io) {
        VaultLocations.ensureNoMedia(context)
        aliasFile.writeText(alias)
        writeManifest(VaultManifest(), alias, authenticatedWrapCipher)
    }

    /**
     * Unlocks and loads the manifest.
     *
     * The vault stays unlocked until [lock], the auto-lock timer fires, or the
     * app leaves the foreground -- whichever comes first.
     */
    suspend fun unlock(): Result<List<VaultEntry>> = withContext(io) {
        try {
            val entries = readManifest().entries
            _state.value = VaultState.Unlocked(
                entries = entries,
                unlockedAtMillis = System.currentTimeMillis(),
            )
            Result.success(entries)
        } catch (e: VaultException) {
            _state.value = VaultState.Locked(lastError = e)
            Result.failure(e)
        }
    }

    /** Locks immediately and drops the decrypted manifest from memory. */
    fun lock() {
        _state.value = VaultState.Locked()
    }

    /**
     * Encrypts [source] into the vault and removes the original.
     *
     * The order is deliberate and non-negotiable: write, verify by reading the
     * ciphertext back, update the manifest, and only then delete. A crash at any
     * point leaves either the original or a complete blob -- never neither.
     */
    suspend fun moveIn(
        source: FsPath,
        displayName: String,
        mimeType: String,
        plaintextSize: Long,
        alias: String,
        authenticatedWrapCipher: Cipher,
    ): Result<VaultEntry> = withContext(io) {
        val blobId = newBlobId()
        val blob = File(blobDir, blobId)

        try {
            val input = provider.read(source).getOrElse { return@withContext Result.failure(it) }
            input.use { plain ->
                blob.outputStream().use { out ->
                    VaultCrypto.encrypt(plain, out, alias, authenticatedWrapCipher)
                }
            }

            // Verify before destroying anything. An unreadable blob plus a deleted
            // original is the one outcome this class must never produce.
            if (!verify(blob)) {
                blob.delete()
                return@withContext Result.failure(
                    VaultException.Tampered("The encrypted copy did not read back correctly")
                )
            }

            val entry = VaultEntry(
                blobId = blobId,
                displayName = displayName,
                mimeType = mimeType,
                plaintextSize = plaintextSize,
                addedAtMillis = System.currentTimeMillis(),
            )

            val manifest = readManifest()
            writeManifest(
                VaultManifest(manifest.entries + entry),
                alias,
                VaultCrypto.wrapCipher(alias),
            )

            // Best-effort, and described as such in the UI. Flash storage may keep
            // the old blocks readable until wear levelling reaches them.
            provider.delete(source)

            _state.value = (_state.value as? VaultState.Unlocked)
                ?.let { it.copy(entries = it.entries + entry) }
                ?: _state.value

            Result.success(entry)
        } catch (e: VaultException) {
            blob.delete()
            Result.failure(e)
        } catch (e: Exception) {
            blob.delete()
            Result.failure(VaultException.KeyUnavailable(e.message ?: "Could not encrypt", e))
        }
    }

    /** Decrypts one entry back out to [destination] and drops it from the vault. */
    suspend fun moveOut(entry: VaultEntry, destination: FsPath): Result<Unit> = withContext(io) {
        val blob = File(blobDir, entry.blobId)
        if (!blob.exists()) return@withContext Result.failure(VaultException.Tampered("Missing blob"))

        try {
            val out = provider.write(destination).getOrElse { return@withContext Result.failure(it) }
            out.use { sink ->
                blob.inputStream().use { raw ->
                    VaultCrypto.decrypt(raw).use { plain -> plain.copyTo(sink) }
                }
            }
            remove(entry)
            Result.success(Unit)
        } catch (e: VaultException) {
            Result.failure(e)
        }
    }

    /** A decrypted stream for previewing, without leaving plaintext on disk. */
    suspend fun open(entry: VaultEntry): Result<java.io.InputStream> = withContext(io) {
        val blob = File(blobDir, entry.blobId)
        if (!blob.exists()) return@withContext Result.failure(VaultException.Tampered("Missing blob"))
        try {
            Result.success(VaultCrypto.decrypt(blob.inputStream()))
        } catch (e: VaultException) {
            Result.failure(e)
        }
    }

    /** Removes an entry and its blob. */
    suspend fun remove(entry: VaultEntry): Result<Unit> = withContext(io) {
        try {
            File(blobDir, entry.blobId).delete()
            val manifest = readManifest()
            val alias = currentAlias
            writeManifest(
                VaultManifest(manifest.entries.filterNot { it.blobId == entry.blobId }),
                alias,
                VaultCrypto.wrapCipher(alias),
            )
            _state.value = (_state.value as? VaultState.Unlocked)
                ?.let { current -> current.copy(entries = current.entries.filterNot { it.blobId == entry.blobId }) }
                ?: _state.value
            Result.success(Unit)
        } catch (e: VaultException) {
            Result.failure(e)
        }
    }

    /**
     * Rotates to the next KEK generation.
     *
     * Blob by blob, so an interruption leaves a mixed-generation vault that still
     * opens -- each blob names the key that wrapped it. The old key is destroyed
     * only once nothing references it any more.
     */
    suspend fun rotateKey(
        newAlias: String,
        authenticatedWrapCipher: Cipher,
    ): Result<Int> = withContext(io) {
        val previousAlias = currentAlias
        var rotated = 0
        try {
            VaultCrypto.ensureKek(newAlias)

            blobDir.listFiles().orEmpty()
                .filter { it.isFile && it.name != MANIFEST_NAME && it.name != ALIAS_NAME && it.name != ".nomedia" }
                .forEach { blob ->
                    val temp = File(blobDir, "${blob.name}.rotating")
                    blob.inputStream().use { source ->
                        temp.outputStream().use { sink ->
                            VaultCrypto.rewrapHeader(
                                source,
                                sink,
                                newAlias,
                                VaultCrypto.wrapCipher(newAlias),
                            )
                        }
                    }
                    if (verify(temp)) {
                        temp.renameTo(blob)
                        rotated++
                    } else {
                        temp.delete()
                        throw VaultException.Tampered("Re-wrapped blob did not verify")
                    }
                }

            val manifest = readManifest()
            writeManifest(manifest, newAlias, authenticatedWrapCipher)
            aliasFile.writeText(newAlias)

            if (previousAlias != newAlias) VaultCrypto.destroyKek(previousAlias)
            Result.success(rotated)
        } catch (e: VaultException) {
            // The old key is still alive and every un-rotated blob still names it,
            // so the vault opens. Rotation can be retried.
            Result.failure(e)
        }
    }

    // --- internals -------------------------------------------------------

    /** Reads a blob to EOF so GCM's tag is checked. Cheap for the manifest, honest for a payload. */
    private fun verify(blob: File): Boolean = try {
        blob.inputStream().use { raw ->
            VaultCrypto.decrypt(raw).use { plain ->
                val buffer = ByteArray(64 * 1024)
                while (plain.read(buffer) >= 0) { /* drain to force the tag check */ }
            }
        }
        true
    } catch (e: Exception) {
        false
    }

    private fun readManifest(): VaultManifest {
        if (!manifestFile.exists()) return VaultManifest()
        return manifestFile.inputStream().use { raw ->
            VaultCrypto.decrypt(raw).use { plain ->
                json.decodeFromString(VaultManifest.serializer(), plain.readBytes().decodeToString())
            }
        }
    }

    private fun writeManifest(manifest: VaultManifest, alias: String, cipher: Cipher) {
        val bytes = json.encodeToString(VaultManifest.serializer(), manifest).encodeToByteArray()
        // Written to a temporary file and renamed, so a crash mid-write cannot
        // leave a half-encrypted manifest and lose every file name in the vault.
        val temp = File(blobDir, "$MANIFEST_NAME.tmp")
        temp.outputStream().use { out ->
            VaultCrypto.encrypt(bytes.inputStream(), out, alias, cipher)
        }
        temp.renameTo(manifestFile)
    }

    private fun newBlobId(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MANIFEST_NAME = "manifest.fvlt"
        private const val ALIAS_NAME = "key-generation"
    }
}

/** Locked or unlocked, and what the UI needs to draw either. */
sealed interface VaultState {
    data class Locked(
        val failedAttempts: Int = 0,
        val lastError: VaultException? = null,
    ) : VaultState

    data class Unlocked(
        val entries: List<VaultEntry>,
        val unlockedAtMillis: Long,
    ) : VaultState {
        val totalBytes: Long get() = entries.sumOf { it.plaintextSize }

        /** Milliseconds left before auto-lock, for the countdown in the header. */
        fun remainingMillis(autoLockMinutes: Int, now: Long = System.currentTimeMillis()): Long =
            (unlockedAtMillis + autoLockMinutes * 60_000L - now).coerceAtLeast(0)
    }
}
