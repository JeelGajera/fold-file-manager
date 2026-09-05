package com.jeelgajera.fold.feature.glyph

/**
 * Something worth showing on the back of the phone.
 *
 * The list is deliberately short. A glyph is a notification you can see with the
 * phone face-down, and it stops being useful the moment it fires for things that
 * do not need attention.
 */
enum class GlyphEvent {
    /** A transfer is running. Drives a progress fill; takes a 0..1 value. */
    TRANSFER_PROGRESS,

    /** A transfer finished. A short, unambiguous flash. */
    TRANSFER_COMPLETE,

    /** Someone on the LAN is asking to connect. Loops until answered. */
    INCOMING_CONNECTION,

    /**
     * A vault unlock failed.
     *
     * On the back of the phone, where the person holding it can see it and the
     * person using it might not be the owner.
     */
    VAULT_UNLOCK_FAILURE,
}

/** Which glyph hardware, if any, this device has. */
enum class GlyphHardware {
    /** Phone (1), (2), (2a): addressable light strips in zones. */
    STRIP,

    /** Phone (3): a 25x25 dot matrix. */
    MATRIX,

    /** Everything else, including every non-Nothing device. */
    NONE,
}

/**
 * The glyph seam.
 *
 * Three implementations sit behind it -- strip, matrix, and a no-op -- and the
 * choice is made **at runtime**, never at compile time. A build that assumed
 * Nothing hardware would crash on every other phone, and a build that assumed its
 * absence could not light anything up. Detection fails closed: anything unclear
 * resolves to [NoOpGlyphController].
 *
 * On a device without glyph hardware nothing degrades. The settings section is
 * hidden entirely rather than shown greyed out, transfers report progress through
 * the notification shade as they always would, and no feature depends on a light.
 */
interface GlyphController {

    val hardware: GlyphHardware

    val isAvailable: Boolean get() = hardware != GlyphHardware.NONE

    /**
     * Plays [event].
     *
     * @param progress 0..1 for [GlyphEvent.TRANSFER_PROGRESS]; ignored otherwise.
     */
    suspend fun play(event: GlyphEvent, progress: Float? = null)

    /** Stops whatever is playing and releases the hardware. */
    suspend fun stop()

    /** Releases the session. Called when the app stops needing the glyph at all. */
    suspend fun release() = stop()
}

/**
 * The fallback, and the default.
 *
 * Every method succeeds and does nothing, so no caller needs a null check and no
 * caller needs to know which phone it is running on.
 */
object NoOpGlyphController : GlyphController {
    override val hardware: GlyphHardware = GlyphHardware.NONE
    override suspend fun play(event: GlyphEvent, progress: Float?) = Unit
    override suspend fun stop() = Unit
}
