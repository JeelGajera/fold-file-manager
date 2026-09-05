package com.jeelgajera.fold.feature.glyph

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Strip hardware: Phone (1), (2) and (2a).
 *
 * Looping events run on their own job so a new event replaces the old one
 * cleanly. Only one animation is ever in flight -- two sequences fighting over
 * five zones produces a mess that means nothing.
 */
class StripGlyphController(
    private val backend: GlyphBackend,
    private val scope: CoroutineScope,
) : GlyphController {

    override val hardware: GlyphHardware = GlyphHardware.STRIP

    private val lock = Mutex()
    private var animation: Job? = null
    private var opened = false

    override suspend fun play(event: GlyphEvent, progress: Float?) = lock.withLock {
        if (!ensureOpen()) return@withLock
        animation?.cancel()

        animation = when (event) {
            GlyphEvent.TRANSFER_PROGRESS -> scope.launch {
                var tick = 0
                while (isActive) {
                    backend.writeZones(GlyphSequences.stripProgress(progress ?: 0f, tick++))
                    delay(GlyphSequences.STRIP_FRAME_MS)
                }
            }

            GlyphEvent.TRANSFER_COMPLETE -> scope.launch {
                GlyphSequences.stripComplete().forEach { frame ->
                    backend.writeZones(frame)
                    delay(400)
                }
                backend.clear()
            }

            GlyphEvent.INCOMING_CONNECTION -> scope.launch {
                var tick = 0
                while (isActive) {
                    backend.writeZones(GlyphSequences.stripIncoming(tick++))
                    delay(600)
                }
            }

            GlyphEvent.VAULT_UNLOCK_FAILURE -> scope.launch {
                GlyphSequences.stripVaultFailure().forEach { frame ->
                    backend.writeZones(frame)
                    delay(120)
                }
                backend.clear()
            }
        }
    }

    override suspend fun stop() = lock.withLock {
        animation?.cancel()
        animation = null
        if (opened) backend.clear()
    }

    override suspend fun release() = lock.withLock {
        animation?.cancel()
        animation = null
        if (opened) {
            backend.close()
            opened = false
        }
    }

    private suspend fun ensureOpen(): Boolean {
        if (!opened) opened = backend.open()
        return opened
    }
}

/** Matrix hardware: Phone (3). Same contract, 625 cells instead of five zones. */
class MatrixGlyphController(
    private val backend: GlyphBackend,
    private val scope: CoroutineScope,
) : GlyphController {

    override val hardware: GlyphHardware = GlyphHardware.MATRIX

    private val lock = Mutex()
    private var animation: Job? = null
    private var opened = false

    private val frameDelay = 1000L / GlyphSequences.MATRIX_FPS

    override suspend fun play(event: GlyphEvent, progress: Float?) = lock.withLock {
        if (!ensureOpen()) return@withLock
        animation?.cancel()

        animation = when (event) {
            GlyphEvent.TRANSFER_PROGRESS -> scope.launch {
                backend.writeMatrix(GlyphSequences.matrixProgress(progress ?: 0f))
            }

            GlyphEvent.TRANSFER_COMPLETE -> scope.launch {
                backend.writeMatrix(GlyphSequences.matrixComplete())
                delay(800)
                backend.clear()
            }

            GlyphEvent.INCOMING_CONNECTION -> scope.launch {
                var frame = 0
                while (isActive) {
                    backend.writeMatrix(GlyphSequences.matrixRing(frame))
                    frame = (frame + 1) % GlyphSequences.MATRIX_FRAMES
                    delay(frameDelay)
                }
            }

            GlyphEvent.VAULT_UNLOCK_FAILURE -> scope.launch {
                repeat(3) {
                    backend.writeMatrix(GlyphSequences.matrixCross())
                    delay(120)
                    backend.clear()
                    delay(120)
                }
            }
        }
    }

    override suspend fun stop() = lock.withLock {
        animation?.cancel()
        animation = null
        if (opened) backend.clear()
    }

    override suspend fun release() = lock.withLock {
        animation?.cancel()
        animation = null
        if (opened) {
            backend.close()
            opened = false
        }
    }

    private suspend fun ensureOpen(): Boolean {
        if (!opened) opened = backend.open()
        return opened
    }
}

/**
 * Builds the controller for whatever this device turns out to be.
 *
 * Called once at startup and after nothing else -- hardware does not change under
 * a running process. The result is a [NoOpGlyphController] on every non-Nothing
 * phone and on every build without the SDK, and nothing above this call has to
 * know which.
 */
object GlyphControllerFactory {

    fun create(context: Context, scope: CoroutineScope): GlyphController =
        when (GlyphDetection.detect(context)) {
            GlyphHardware.STRIP -> StripGlyphController(
                ReflectiveGlyphBackend(context, GlyphHardware.STRIP),
                scope,
            )

            GlyphHardware.MATRIX -> MatrixGlyphController(
                ReflectiveGlyphBackend(context, GlyphHardware.MATRIX),
                scope,
            )

            GlyphHardware.NONE -> NoOpGlyphController
        }
}
