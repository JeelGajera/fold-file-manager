package com.jeelgajera.fold.core.storage.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.jeelgajera.fold.core.storage.model.FsSort
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

/** Which ground the app paints on. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Which glyph hardware the user has chosen to drive, when the device has any. */
enum class GlyphMode { STRIP, MATRIX, OFF }

/**
 * Everything FOLD remembers.
 *
 * The whole of it is preferences. There is no usage history, no opened-file list,
 * no device identifier and no install id -- if it is not needed to draw the next
 * screen the way the user left it, it is not here.
 */
@Serializable
data class FoldSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /**
     * Whether dot-prefixed files appear in listings.
     *
     * A visibility toggle, and labelled as one everywhere it is surfaced. It is
     * not the vault and must never be presented as protection: any app with
     * storage access reads these files whether or not FOLD draws them.
     */
    val showHiddenFiles: Boolean = false,

    val defaultSort: FsSort = FsSort.DATE,
    val defaultSortDescending: Boolean = true,
    val gridView: Boolean = false,

    // --- LAN server ---
    val serverPort: Int = 8080,
    val serverRequirePin: Boolean = true,
    /** The server shuts down with the display. On by default; it is the safe answer. */
    val serverStopOnScreenOff: Boolean = true,
    val serverIdleTimeoutMinutes: Int = 15,
    /** Allow uploads from connected clients. Off by default: receiving is a decision. */
    val serverAllowUpload: Boolean = false,
    /** Allow deletes over HTTP. Off by default, and confirmed per request when on. */
    val serverAllowDelete: Boolean = false,

    // --- Vault ---
    val vaultAutoLockMinutes: Int = 5,

    // --- Glyph ---
    val glyphMode: GlyphMode = GlyphMode.STRIP,
    val glyphEvents: Set<String> = setOf(
        "TRANSFER_PROGRESS",
        "TRANSFER_COMPLETE",
        "INCOMING_CONNECTION",
    ),

    // --- Onboarding ---
    val onboardingComplete: Boolean = false,
    /**
     * Whether the All Files Access rationale has already been shown and answered.
     *
     * FOLD asks once. The onboarding copy promises exactly that, and this flag is
     * what keeps the promise.
     */
    val allFilesAccessAsked: Boolean = false,
)

/**
 * The DataStore serializer.
 *
 * `androidx.datastore:datastore` -- the typed store, not the preferences one --
 * with a kotlinx.serialization JSON codec rather than protobuf. The typed API and
 * its guarantees (atomic writes, a single writer, a Flow of the current value)
 * are what matter here; protobuf's wire format would add the `protoc` toolchain
 * to the build for a settings object of thirty scalars. Anyone who wants the
 * binary encoding can swap this one class.
 *
 * Corruption is not silently swallowed: a `CorruptionException` makes DataStore
 * run the corruption handler, which resets to [defaultValue] rather than leaving
 * the app unable to start.
 */
object FoldSettingsSerializer : Serializer<FoldSettings> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: FoldSettings = FoldSettings()

    override suspend fun readFrom(input: InputStream): FoldSettings = try {
        json.decodeFromString(FoldSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Settings file could not be read", e)
    }

    override suspend fun writeTo(t: FoldSettings, output: OutputStream) {
        output.write(json.encodeToString(FoldSettings.serializer(), t).encodeToByteArray())
    }

    const val FILE_NAME = "fold-settings.json"
}
