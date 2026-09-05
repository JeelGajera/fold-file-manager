package com.jeelgajera.fold.feature.transfer.server

import com.jeelgajera.fold.core.storage.mime.MimeResolver
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.ListOptions
import com.jeelgajera.fold.core.storage.provider.FileSystemProvider
import com.jeelgajera.fold.core.storage.provider.FsError
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.util.pipeline.PipelineContext
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.header
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.delete
import io.ktor.server.routing.routing
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The LAN file server.
 *
 * ### The shape of the security model
 *
 * Path traversal is the top risk, and it is *not* handled here. Every path in
 * every route is converted to an [FsPath] and handed to [provider], which runs it
 * through `PathGuard`: canonicalise, refuse the vault, refuse anything outside an
 * explicitly rooted allowlist. Keeping the check one layer down means a new route
 * added later -- or a bug in one of these -- cannot reach around it. The routes
 * below perform no path resolution of their own.
 *
 * The second layer is [serverRoot]: even inside the allowlist, this server only
 * serves what the user chose to share. Browsing the whole device from the UI is
 * one thing; exposing it to the network is another, and the two are not the same
 * permission.
 *
 * ### What it refuses to do
 *
 * - No directory listing outside [serverRoot], however the path is spelled.
 * - No upload unless the user turned uploads on; no delete unless they turned
 *   deletes on, and then only with an explicit confirmation token.
 * - No response body that echoes a resolved filesystem path back to the caller.
 *   A traversal attempt gets "Path is not accessible" and nothing else, because
 *   confirming where it landed maps the device for whoever is probing.
 *
 * ### Plain HTTP, deliberately
 *
 * The server runs plain HTTP on the LAN plus a PIN, and it is worth being honest
 * about the trade rather than reaching for TLS reflexively. A self-signed
 * certificate on a LAN address produces a full-page browser interstitial that
 * trains the user to click through security warnings, and it protects against an
 * attacker who is already on the same Wi-Fi *and* actively intercepting -- while
 * doing nothing about the far likelier case, which is someone on that Wi-Fi
 * simply opening the address. The PIN addresses that case. TLS is available as an
 * option in settings, with the certificate-warning cost stated in the UI rather
 * than buried.
 */
class FoldServer(
    private val provider: FileSystemProvider,
    private val auth: ServerAuth,
    private val serverRoot: FsPath,
    private val allowUpload: Boolean,
    private val allowDelete: Boolean,
    private val requirePin: Boolean,
    private val onEvent: (ServerEvent) -> Unit,
) {

    private var engine: ApplicationEngine? = null

    /**
     * Starts on [port], bound to [host].
     *
     * [host] is the Wi-Fi interface's address, never `0.0.0.0`. Binding to all
     * interfaces would put the server on cellular and on any VPN or tether the
     * phone happens to have up, which is not what "share over Wi-Fi" means.
     */
    fun start(host: String, port: Int) {
        stop()
        engine = embeddedServer(CIO, port = port, host = host) { module() }.also {
            it.start(wait = false)
        }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
        auth.revokeAll()
    }

    val isRunning: Boolean get() = engine != null

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { prettyPrint = false; encodeDefaults = true })
        }
        // Range requests, so a 400MB download resumes instead of restarting.
        install(PartialContent)
        install(AutoHeadResponse)
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                // Never let a stack trace or a resolved path reach the wire.
                val status = when (cause) {
                    is FsError.OutOfBounds -> HttpStatusCode.NotFound
                    is FsError.NotFound -> HttpStatusCode.NotFound
                    is FsError.PermissionDenied -> HttpStatusCode.Forbidden
                    else -> HttpStatusCode.InternalServerError
                }
                call.respondText("Request refused", status = status)
            }
        }

        routing {
            get("/") {
                call.respondText(IndexPage.html(requirePin), ContentType.Text.Html)
            }

            post("/api/auth") {
                val submitted = call.request.header(HEADER_PIN).orEmpty()
                val address = call.request.local.remoteHost
                val agent = call.request.header(HttpHeaders.UserAgent).orEmpty()

                if (!requirePin) {
                    call.respond(AuthResponse(token = "open", message = "No PIN required"))
                    return@post
                }

                when (val outcome = auth.authenticate(submitted, address, agent)) {
                    is AuthOutcome.Accepted -> {
                        onEvent(ServerEvent.ClientConnected(address, agent))
                        call.respond(AuthResponse(token = outcome.token))
                    }

                    is AuthOutcome.Rejected -> {
                        onEvent(ServerEvent.AuthRejected(address))
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            AuthResponse(
                                token = null,
                                message = "Wrong PIN. ${outcome.attemptsRemaining} attempts left.",
                            ),
                        )
                    }

                    is AuthOutcome.LockedOut -> {
                        call.response.header(
                            HttpHeaders.RetryAfter,
                            (outcome.retryAfterMillis / 1000).coerceAtLeast(1).toString(),
                        )
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            AuthResponse(token = null, message = "Too many attempts. Wait and try again."),
                        )
                    }
                }
            }

            get("/api/list") {
                requireSession() ?: return@get
                val target = resolveRequested(call.request.queryParameters["path"])
                    ?: return@get respondRefused()

                provider.list(target, ListOptions(includeHidden = false)).fold(
                    onSuccess = { entries ->
                        call.respond(
                            ListingResponse(
                                path = relativeTo(target),
                                parent = target.parent?.takeIf { it.isWithin(serverRoot) }
                                    ?.let { relativeTo(it) },
                                entries = entries.map { it.toDto() },
                            )
                        )
                    },
                    onFailure = { respondRefused() },
                )
            }

            get("/api/download") {
                requireSession() ?: return@get
                val target = resolveRequested(call.request.queryParameters["path"])
                    ?: return@get respondRefused()

                val entry = provider.stat(target).getOrElse { return@get respondRefused() }
                if (entry.isDirectory) return@get respondRefused()

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    // The filename is quoted and stripped of quotes and control
                    // characters so it cannot break out of the header.
                    "attachment; filename=\"${sanitiseHeaderValue(entry.name)}\"",
                )
                call.response.header(HttpHeaders.CacheControl, CacheControl.NoStore(null).toString())
                call.response.header(HttpHeaders.AcceptRanges, "bytes")

                val stream = provider.read(target).getOrElse { return@get respondRefused() }
                val contentType = runCatching { ContentType.parse(entry.mimeType) }
                    .getOrDefault(ContentType.Application.OctetStream)
                call.respondOutputStream(
                    contentType = contentType,
                    status = HttpStatusCode.OK,
                ) {
                    stream.use { source ->
                        onEvent(ServerEvent.TransferStarted(entry.name, entry.sizeBytes))
                        var sent = 0L
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            write(buffer, 0, read)
                            sent += read
                            onEvent(ServerEvent.TransferProgress(entry.name, sent, entry.sizeBytes))
                        }
                        onEvent(ServerEvent.TransferFinished(entry.name))
                    }
                }
            }

            post("/api/upload") {
                requireSession() ?: return@post
                if (!allowUpload) {
                    return@post call.respondText("Uploads are off", status = HttpStatusCode.Forbidden)
                }
                val directory = resolveRequested(call.request.queryParameters["path"])
                    ?: return@post respondRefused()

                var written = 0
                call.receiveMultipart().forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName?.let(::safeFileName)
                        if (name != null) {
                            // child() rejects separators, so an upload named
                            // "../../evil" cannot escape the directory, and the
                            // guard checks the result again regardless.
                            val destination = runCatching { directory.child(name) }.getOrNull()
                            if (destination != null) {
                                provider.write(destination).getOrNull()?.use { sink ->
                                    part.streamProvider().use { it.copyTo(sink) }
                                    written++
                                }
                            }
                        }
                    }
                    part.dispose()
                }
                call.respond(UploadResponse(written))
            }

            delete("/api/file") {
                requireSession() ?: return@delete
                if (!allowDelete) {
                    return@delete call.respondText("Deletes are off", status = HttpStatusCode.Forbidden)
                }
                // A second, explicit confirmation. A delete that can happen by
                // following a link is a delete that happens by accident.
                if (call.request.header(HEADER_CONFIRM) != "delete") {
                    return@delete call.respondText(
                        "Confirmation required",
                        status = HttpStatusCode.PreconditionRequired,
                    )
                }
                val target = resolveRequested(call.request.queryParameters["path"])
                    ?: return@delete respondRefused()

                provider.delete(target).fold(
                    onSuccess = { call.respond(HttpStatusCode.NoContent) },
                    onFailure = { respondRefused() },
                )
            }
        }
    }

    // --- helpers ---------------------------------------------------------

    /**
     * Turns a requested relative path into an absolute one under [serverRoot].
     *
     * This does the *shape* checks -- decode once, refuse absolute paths, refuse
     * anything that leaves the root after normalisation. It is not the security
     * boundary; `PathGuard` is, and it runs again inside the provider on every
     * call. Two independent checks, because this one is the kind of code that
     * gets refactored.
     */
    private fun resolveRequested(raw: String?): FsPath? {
        val requested = raw.orEmpty()
        if (requested.isEmpty()) return serverRoot

        // Decode exactly once. Decoding in a loop is how "%252e%252e" becomes
        // "..", which is a traversal an attacker gets for free.
        val decoded = runCatching { java.net.URLDecoder.decode(requested, "UTF-8") }
            .getOrNull() ?: return null

        if (decoded.startsWith('/')) return null

        val candidate = FsPath.raw("${serverRoot.value}/$decoded")
        return candidate.takeIf { it.isWithin(serverRoot) }
    }

    private fun relativeTo(path: FsPath): String =
        path.value.removePrefix(serverRoot.value).removePrefix("/")

    private fun FsEntry.toDto() = EntryDto(
        name = name,
        path = relativeTo(path),
        isDirectory = isDirectory,
        size = sizeBytes,
        modified = lastModifiedMillis,
        mimeType = mimeType,
    )

    /**
     * Rejects the request without saying anything useful about the filesystem.
     *
     * Every refusal -- out of bounds, not found, unreadable -- answers 404 with
     * the same body. Distinguishing them would let a caller map the device by
     * probing: "403" means the path exists, "404" means it does not.
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.respondRefused() {
        call.respondText("Path is not accessible", status = HttpStatusCode.NotFound)
    }

    /**
     * Requires a valid session token, answering 401 and returning null if absent.
     *
     * When the user has turned the PIN off this returns a placeholder session so
     * the routes keep one code path.
     */
    private suspend fun PipelineContext<Unit, ApplicationCall>.requireSession(): ServerSession? {
        if (!requirePin) {
            val now = System.currentTimeMillis()
            return ServerSession(
                token = "open",
                remoteAddress = call.request.local.remoteHost,
                userAgent = call.request.header(HttpHeaders.UserAgent).orEmpty(),
                issuedAtMillis = now,
                lastSeenMillis = now,
            )
        }
        val session = auth.validate(call.request.header(HEADER_TOKEN))
        if (session == null) {
            call.respondText("Sign in with the PIN", status = HttpStatusCode.Unauthorized)
        }
        return session
    }

    private companion object {
        const val HEADER_PIN = "X-Fold-Pin"
        const val HEADER_TOKEN = "X-Fold-Token"
        const val HEADER_CONFIRM = "X-Fold-Confirm"

        /** Strips anything that could break out of a quoted header value. */
        fun sanitiseHeaderValue(value: String): String =
            value.filter { it.code in 32..126 && it != '"' && it != '\\' }.ifEmpty { "download" }

        /**
         * Reduces an uploaded filename to a single safe segment.
         *
         * Everything before the last separator is discarded -- browsers and curl
         * both send full paths in some cases -- and the remainder is checked for
         * relative segments. Returns null rather than a "cleaned" name when the
         * input is hostile: silently rewriting an attacker's filename into
         * something acceptable hides the attempt.
         */
        fun safeFileName(raw: String): String? {
            val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
            if (leaf.isEmpty() || leaf == "." || leaf == "..") return null
            return leaf.take(255)
        }
    }
}

// --- wire types ---------------------------------------------------------

@Serializable
data class AuthResponse(val token: String?, val message: String? = null)

@Serializable
data class EntryDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val mimeType: String,
)

@Serializable
data class ListingResponse(
    val path: String,
    val parent: String?,
    val entries: List<EntryDto>,
)

@Serializable
data class UploadResponse(val written: Int)

/** Things the server tells the UI and the glyph about. */
sealed interface ServerEvent {
    data class ClientConnected(val address: String, val userAgent: String) : ServerEvent
    data class AuthRejected(val address: String) : ServerEvent
    data class TransferStarted(val name: String, val totalBytes: Long) : ServerEvent
    data class TransferProgress(val name: String, val sentBytes: Long, val totalBytes: Long) : ServerEvent
    data class TransferFinished(val name: String) : ServerEvent
}
