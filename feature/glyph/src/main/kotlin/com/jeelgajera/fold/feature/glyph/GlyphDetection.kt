package com.jeelgajera.fold.feature.glyph

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * A Nothing phone FOLD knows the glyph layout of.
 *
 * Identified by model code rather than by a system feature, because the feature
 * flags are not reliable: a Phone (2a) reports `com.nothing.feature.*` entries
 * and no `com.nothing.glyph` at all, so a feature-only check can never succeed
 * on it. The model code is what actually distinguishes the hardware.
 *
 * [zones] is the number of independently addressable glyph channels. These come
 * from the Glyph Developer Kit's published channel maps and are the numbers the
 * sequence maths in [GlyphSequences] is written against; they are worth
 * re-confirming against the SDK when it is finally on the classpath, since a
 * wrong count means a sequence that lights the wrong strip rather than one that
 * fails loudly.
 */
enum class GlyphDevice(val modelCode: String, val displayName: String, val hardware: GlyphHardware, val zones: Int) {
    PHONE_1("A063", "Phone (1)", GlyphHardware.STRIP, zones = 5),
    PHONE_2("A065", "Phone (2)", GlyphHardware.STRIP, zones = 11),
    PHONE_2A("A142", "Phone (2a)", GlyphHardware.STRIP, zones = 3),
    PHONE_2A_PLUS("A142P", "Phone (2a) Plus", GlyphHardware.STRIP, zones = 3),
    PHONE_3A("A059", "Phone (3a)", GlyphHardware.STRIP, zones = 3),
    PHONE_3A_PRO("A059P", "Phone (3a) Pro", GlyphHardware.STRIP, zones = 3),
    PHONE_3("A024", "Phone (3)", GlyphHardware.MATRIX, zones = 0),
    ;

    companion object {
        /**
         * The device this build is running on, or null if it is not a Nothing
         * phone with a glyph layout FOLD knows.
         *
         * Longest code first, so `A142P` is not swallowed by `A142`.
         */
        fun current(): GlyphDevice? {
            val model = Build.MODEL.trim().uppercase()
            return entries
                .sortedByDescending { it.modelCode.length }
                .firstOrNull { model == it.modelCode || model.startsWith(it.modelCode) }
        }
    }
}

/**
 * Runtime detection of glyph hardware.
 *
 * ### Why this is reflection and not a dependency
 *
 * Nothing's Glyph Developer Kit and Glyph Matrix SDK are not published to Maven
 * Central. Using them means registering as a developer, receiving the AAR, and
 * adding a manifest metadata key with the issued API key. FOLD is built so that
 * work can be done later without touching anything above this file: the SDK is
 * addressed by class name, and if it is not on the classpath -- which is the case
 * for every build until that registration happens -- detection resolves to
 * [GlyphHardware.NONE] and the app behaves exactly as it does on a Pixel.
 *
 * That has a cost worth stating plainly: **until the SDK is added, the strip and
 * matrix controllers cannot light anything up.** They are wired, tested against
 * their own frame maths, and inert. Phase 4 of the build plan is where the AAR
 * and the API key land, and [SDK_INTEGRATION_PENDING] is the flag that flips.
 *
 * ### Fail closed
 *
 * Three things must all agree before FOLD claims a glyph: the manufacturer, the
 * device's declared feature, and the SDK being loadable. Any doubt resolves to
 * no glyph, because lighting nothing up is a non-event and crashing on a phone
 * that turned out not to have the hardware is not.
 */
object GlyphDetection {

    /**
     * True while the Glyph SDKs are absent from the build.
     *
     * Read by the settings screen so it can say what is actually happening rather
     * than showing a preview of something that will not fire.
     */
    val SDK_INTEGRATION_PENDING: Boolean
        get() = !isClassPresent(GDK_MANAGER) && !isClassPresent(MATRIX_MANAGER)

    fun detect(context: Context): GlyphHardware {
        if (!isNothingDevice()) return GlyphHardware.NONE

        // The model is the primary signal; the declared feature is accepted as a
        // fallback for a Nothing phone released after this build. Either way the
        // SDK must be loadable, or there is nothing to drive the hardware with.
        val byModel = GlyphDevice.current()?.hardware
        val byFeature = when {
            hasFeature(context, FEATURE_MATRIX) -> GlyphHardware.MATRIX
            hasFeature(context, FEATURE_GLYPH) -> GlyphHardware.STRIP
            else -> GlyphHardware.NONE
        }
        val hardware = byModel ?: byFeature

        return when (hardware) {
            GlyphHardware.MATRIX -> if (isClassPresent(MATRIX_MANAGER)) hardware else GlyphHardware.NONE
            GlyphHardware.STRIP -> if (isClassPresent(GDK_MANAGER)) hardware else GlyphHardware.NONE
            GlyphHardware.NONE -> GlyphHardware.NONE
        }
    }

    /**
     * What the device *would* offer if the SDK were present.
     *
     * The settings screen uses this to explain the situation on a Nothing phone
     * with no SDK in the build, instead of silently hiding the section as it does
     * on hardware that genuinely has no glyph.
     */
    fun potentialHardware(context: Context): GlyphHardware = when {
        !isNothingDevice() -> GlyphHardware.NONE

        // A recognised model settles it without consulting the feature flags,
        // which a Phone (2a) does not declare.
        GlyphDevice.current() != null -> GlyphDevice.current()!!.hardware

        hasFeature(context, FEATURE_MATRIX) -> GlyphHardware.MATRIX

        hasFeature(context, FEATURE_GLYPH) -> GlyphHardware.STRIP

        // A Nothing phone whose model and feature flags are both unfamiliar. The
        // strip is the safer assumption; the controller still fails closed if it
        // cannot bind.
        else -> GlyphHardware.STRIP
    }

    /**
     * How this phone should be described on the settings screen.
     *
     * "Phone (2a) - 3 glyph zones" is a statement the user can check against the
     * back of their device. "Glyph hardware detected" is not.
     */
    fun deviceDescription(): String? = GlyphDevice.current()?.let { device ->
        when (device.hardware) {
            GlyphHardware.MATRIX -> "${device.displayName} · Glyph Matrix"
            GlyphHardware.STRIP -> "${device.displayName} · ${device.zones} glyph zones"
            GlyphHardware.NONE -> device.displayName
        }
    }

    private fun isNothingDevice(): Boolean = Build.MANUFACTURER.equals("Nothing", ignoreCase = true) ||
        Build.BRAND.equals("Nothing", ignoreCase = true)

    private fun hasFeature(context: Context, feature: String): Boolean = try {
        context.packageManager.hasSystemFeature(feature)
    } catch (e: RuntimeException) {
        false
    }

    private fun isClassPresent(name: String): Boolean = try {
        Class.forName(name, false, GlyphDetection::class.java.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: LinkageError) {
        // The class exists but its dependencies do not. Same answer.
        false
    }

    /** Device features Nothing declares for glyph-capable hardware. */
    private const val FEATURE_GLYPH = "com.nothing.glyph"
    private const val FEATURE_MATRIX = "com.nothing.glyph.matrix"

    /** SDK entry points, addressed by name so their absence is not a link error. */
    private const val GDK_MANAGER = "com.nothing.ketchum.GlyphManager"
    private const val MATRIX_MANAGER = "com.nothing.ketchum.GlyphMatrixManager"

    /**
     * The manifest metadata key Nothing's SDK reads for the issued API key.
     *
     * Documented here so the value has one obvious home when registration
     * completes: it goes in `:app`'s manifest, not in code.
     */
    const val MANIFEST_API_KEY = "NothingKey"
}
