package com.jeelgajera.fold.feature.glyph

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The frame data behind every glyph event, kept separate from the hardware.
 *
 * These are pure functions over integers, which means the sequences can be unit
 * tested and previewed on screen without a Nothing phone anywhere near them. The
 * settings screen renders exactly these frames, so what the preview shows is what
 * the hardware will play.
 */
object GlyphSequences {

    // --- strip ------------------------------------------------------------

    /** Zones on a strip device, and the brightness steps the hardware accepts. */
    const val STRIP_ZONES = 5
    val BRIGHTNESS_STEPS = intArrayOf(0, 18, 45, 90, 100)
    const val STRIP_FRAME_MS = 200L

    /**
     * Transfer progress across the five zones.
     *
     * Zones below the fill are solid, the zone at the boundary pulses, and the
     * rest are dim but not off -- an entirely dark strip reads as "nothing is
     * happening", which is the opposite of the message.
     */
    fun stripProgress(progress: Float, tick: Int): IntArray {
        val filled = (progress.coerceIn(0f, 1f) * STRIP_ZONES)
        val whole = filled.toInt()
        return IntArray(STRIP_ZONES) { zone ->
            when {
                zone < whole -> 100
                zone == whole -> if (tick % 2 == 0) 90 else 45
                else -> 18
            }
        }
    }

    /** Every zone at full for 400ms, twice. The unambiguous "done". */
    fun stripComplete(): List<IntArray> = listOf(
        IntArray(STRIP_ZONES) { 100 },
        IntArray(STRIP_ZONES) { 0 },
        IntArray(STRIP_ZONES) { 100 },
        IntArray(STRIP_ZONES) { 0 },
    )

    /** The middle zone pulsing at 600ms. Loops until the request is answered. */
    fun stripIncoming(tick: Int): IntArray = IntArray(STRIP_ZONES) { zone ->
        if (zone == STRIP_ZONES / 2 && tick % 2 == 0) 100 else 0
    }

    /**
     * Outer zones only, three short bursts.
     *
     * Deliberately different in shape from every other sequence: a failed unlock
     * must not be mistakable for a finished transfer by someone glancing at the
     * back of a phone across a table.
     */
    fun stripVaultFailure(): List<IntArray> = buildList {
        repeat(3) {
            add(IntArray(STRIP_ZONES) { zone -> if (zone == 0 || zone == STRIP_ZONES - 1) 90 else 0 })
            add(IntArray(STRIP_ZONES) { 0 })
        }
    }

    // --- matrix -----------------------------------------------------------

    const val MATRIX_SIZE = 25
    const val MATRIX_FPS = 12
    const val MATRIX_FRAMES = 24

    /**
     * An expanding ring, one frame of it.
     *
     * Returns a [MATRIX_SIZE] x [MATRIX_SIZE] array of 0..255 brightness values,
     * row-major. The ring travels outward from the centre and the whole disc sits
     * at a low floor so the matrix reads as active rather than broken.
     */
    fun matrixRing(frame: Int): IntArray {
        val out = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val centre = MATRIX_SIZE / 2f
        val radius = MATRIX_SIZE / 2f
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val distance = hypot(x - centre, y - centre)
                if (distance > radius) continue
                val wave = (distance - frame * 1.2f).mod(8f)
                out[y * MATRIX_SIZE + x] = if (wave in 0f..2.2f) 255 else 20
            }
        }
        return out
    }

    /**
     * Progress drawn as a filled disc.
     *
     * Rows fill from the bottom, so the matrix works as a gauge rather than as an
     * animation: a glance tells you roughly how far along the transfer is.
     */
    fun matrixProgress(progress: Float): IntArray {
        val clamped = progress.coerceIn(0f, 1f)
        val out = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val centre = MATRIX_SIZE / 2f
        val radius = MATRIX_SIZE / 2f
        val waterline = MATRIX_SIZE - (clamped * MATRIX_SIZE).roundToInt()
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                if (hypot(x - centre, y - centre) > radius) continue
                out[y * MATRIX_SIZE + x] = if (y >= waterline) 255 else 25
            }
        }
        return out
    }

    /** A solid disc. The matrix's "done". */
    fun matrixComplete(): IntArray {
        val out = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val centre = MATRIX_SIZE / 2f
        val radius = MATRIX_SIZE / 2f
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                if (hypot(x - centre, y - centre) <= radius) out[y * MATRIX_SIZE + x] = 255
            }
        }
        return out
    }

    /**
     * A cross. Used for a failed vault unlock.
     *
     * A shape rather than a colour or a rhythm, so the meaning survives being
     * seen for a quarter of a second from an angle.
     */
    fun matrixCross(): IntArray {
        val out = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        for (i in 0 until MATRIX_SIZE) {
            out[i * MATRIX_SIZE + i] = 255
            out[i * MATRIX_SIZE + (MATRIX_SIZE - 1 - i)] = 255
        }
        return out
    }
}
