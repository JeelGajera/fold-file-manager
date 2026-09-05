package com.jeelgajera.fold.feature.glyph

import android.content.Context
import android.util.Log

/**
 * The thin surface the Nothing SDK sits behind.
 *
 * Splitting it out this way keeps every frame calculation in [GlyphSequences],
 * where it can be unit tested and previewed on screen, and confines the parts
 * that need real hardware to a handful of methods. When the SDK AAR lands, one
 * implementation of this interface is the whole of the work.
 */
interface GlyphBackend {

    /** Acquires the hardware session. Returns false if the SDK refuses. */
    suspend fun open(): Boolean

    /** Sets the brightness of each strip zone, 0..100. */
    suspend fun writeZones(brightness: IntArray)

    /** Writes one 25x25 frame, row-major, values 0..255. */
    suspend fun writeMatrix(frame: IntArray)

    /** Turns everything off without closing the session. */
    suspend fun clear()

    suspend fun close()
}

/**
 * Binds to Nothing's SDK by reflection.
 *
 * Every call is wrapped: a `NoSuchMethodException` from an SDK version that
 * moved a signature must dim a light, not crash a file manager. On any failure
 * the backend reports itself unusable and the caller drops to
 * [NoOpGlyphController].
 *
 * **This class cannot light anything up until the Glyph SDK AAR is added to the
 * build and the API key is declared in `:app`'s manifest.** Until then [open]
 * returns false on every device, which is the correct behaviour and not a bug --
 * see [GlyphDetection.SDK_INTEGRATION_PENDING].
 */
class ReflectiveGlyphBackend(
    private val context: Context,
    private val hardware: GlyphHardware,
) : GlyphBackend {

    private var session: Any? = null

    override suspend fun open(): Boolean {
        if (GlyphDetection.SDK_INTEGRATION_PENDING) return false
        return try {
            val className = when (hardware) {
                GlyphHardware.MATRIX -> "com.nothing.ketchum.GlyphMatrixManager"
                GlyphHardware.STRIP -> "com.nothing.ketchum.GlyphManager"
                GlyphHardware.NONE -> return false
            }
            val manager = Class.forName(className)
            val instance = manager.getMethod("getInstance", Context::class.java)
                .invoke(null, context)
            manager.getMethod("init", Class.forName("com.nothing.ketchum.GlyphManager\$Callback"))
                .invoke(instance, null)
            session = instance
            true
        } catch (e: ReflectiveOperationException) {
            Log.i(TAG, "Glyph SDK not usable on this device; falling back to no-op")
            false
        } catch (e: RuntimeException) {
            Log.i(TAG, "Glyph session refused; falling back to no-op")
            false
        }
    }

    override suspend fun writeZones(brightness: IntArray) {
        invoke("setChannelBrightness", brightness)
    }

    override suspend fun writeMatrix(frame: IntArray) {
        invoke("setMatrixFrame", frame)
    }

    override suspend fun clear() {
        invoke("turnOff")
    }

    override suspend fun close() {
        invoke("unInit")
        session = null
    }

    private fun invoke(method: String, argument: Any? = null) {
        val target = session ?: return
        try {
            if (argument == null) {
                target.javaClass.getMethod(method).invoke(target)
            } else {
                target.javaClass.getMethod(method, argument.javaClass).invoke(target, argument)
            }
        } catch (e: ReflectiveOperationException) {
            // An SDK revision moved or renamed something. A dark glyph is an
            // acceptable outcome; a crashed file manager is not.
            Log.d(TAG, "Glyph call '$method' unavailable in this SDK build")
        } catch (e: RuntimeException) {
            Log.d(TAG, "Glyph call '$method' failed")
        }
    }

    private companion object {
        const val TAG = "FoldGlyph"
    }
}
