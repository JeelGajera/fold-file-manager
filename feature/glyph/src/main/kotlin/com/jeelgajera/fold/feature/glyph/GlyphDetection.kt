package com.jeelgajera.fold.feature.glyph

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

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

        return when {
            hasFeature(context, FEATURE_MATRIX) && isClassPresent(MATRIX_MANAGER) ->
                GlyphHardware.MATRIX

            hasFeature(context, FEATURE_GLYPH) && isClassPresent(GDK_MANAGER) ->
                GlyphHardware.STRIP

            else -> GlyphHardware.NONE
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
        hasFeature(context, FEATURE_MATRIX) -> GlyphHardware.MATRIX
        hasFeature(context, FEATURE_GLYPH) -> GlyphHardware.STRIP
        // A Nothing phone whose feature flags are unfamiliar. The strip is the
        // safer assumption; the controller still fails closed if it cannot bind.
        else -> GlyphHardware.STRIP
    }

    private fun isNothingDevice(): Boolean =
        Build.MANUFACTURER.equals("Nothing", ignoreCase = true) ||
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
