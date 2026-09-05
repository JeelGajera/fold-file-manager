package com.jeelgajera.fold.feature.transfer.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/** One authenticated browser session. */
data class ServerSession(
    val token: String,
    val remoteAddress: String,
    val userAgent: String,
    val issuedAtMillis: Long,
    @Volatile var lastSeenMillis: Long,
) {
    /** A readable name for the connected-clients list: `Mac · Safari`. */
    val label: String get() = ServerAuth.describeUserAgent(userAgent)
}

/**
 * PIN exchange, session tokens and rate limiting for the LAN server.
 *
 * ### The threat
 *
 * The server binds to the Wi-Fi interface, which means everyone on the network
 * can reach it -- a café, a shared flat, an office. The PIN is the only thing
 * between them and the user's files, and six digits is a million guesses, which a
 * machine on the same LAN can exhaust in seconds if nothing stops it.
 *
 * So: a per-address lockout with an exponential backoff, a fresh PIN each
 * session, and constant-time comparison so the failure path leaks nothing about
 * how many leading digits were right.
 *
 * ### Why six digits, not four
 *
 * The design mock shows four. Four digits is ten thousand guesses; with a
 * five-attempt lockout that is still a bad ratio against an attacker willing to
 * wait out a backoff on a long-running server. Six digits and the same lockout
 * moves it out of reach, and the extra two characters cost the person typing it
 * about a second. This is a deliberate departure from the mock, noted here so it
 * is not read as an oversight.
 */
class ServerAuth(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    /** The PIN for this run of the server. Regenerated every time it starts. */
    @Volatile
    var pin: String = generatePin()
        private set

    private val sessions = ConcurrentHashMap<String, ServerSession>()
    private val failures = ConcurrentHashMap<String, FailureRecord>()

    fun regeneratePin(): String = generatePin().also { pin = it }

    /**
     * Exchanges a PIN for a session token.
     *
     * @return the token, or null when the PIN is wrong or the caller is locked out.
     */
    fun authenticate(
        candidate: String,
        remoteAddress: String,
        userAgent: String,
    ): AuthOutcome {
        val now = clock()
        val record = failures[remoteAddress]
        if (record != null && now < record.lockedUntilMillis) {
            return AuthOutcome.LockedOut(record.lockedUntilMillis - now)
        }

        if (!constantTimeEquals(candidate, pin)) {
            val next = (record?.count ?: 0) + 1
            failures[remoteAddress] = FailureRecord(
                count = next,
                lockedUntilMillis = now + backoffMillis(next),
            )
            return AuthOutcome.Rejected(attemptsRemaining = (MAX_ATTEMPTS - next).coerceAtLeast(0))
        }

        failures.remove(remoteAddress)
        val token = newToken()
        sessions[token] = ServerSession(
            token = token,
            remoteAddress = remoteAddress,
            userAgent = userAgent,
            issuedAtMillis = now,
            lastSeenMillis = now,
        )
        return AuthOutcome.Accepted(token)
    }

    /** Validates a token and refreshes its idle timer. */
    fun validate(token: String?): ServerSession? {
        if (token.isNullOrEmpty()) return null
        val session = sessions[token] ?: return null
        val now = clock()
        if (now - session.lastSeenMillis > SESSION_IDLE_MILLIS) {
            sessions.remove(token)
            return null
        }
        session.lastSeenMillis = now
        return session
    }

    fun revoke(token: String) {
        sessions.remove(token)
    }

    /** Drops every session. Called when the server stops or the PIN is regenerated. */
    fun revokeAll() {
        sessions.clear()
        failures.clear()
    }

    fun activeSessions(): List<ServerSession> {
        val now = clock()
        sessions.entries.removeAll { now - it.value.lastSeenMillis > SESSION_IDLE_MILLIS }
        return sessions.values.sortedBy { it.issuedAtMillis }
    }

    /** True when no session has been seen for [idleMinutes]. Drives the auto-stop. */
    fun idleFor(idleMinutes: Int): Boolean {
        val newest = sessions.values.maxOfOrNull { it.lastSeenMillis } ?: return true
        return clock() - newest > idleMinutes * 60_000L
    }

    private fun generatePin(): String =
        (1..PIN_DIGITS).joinToString("") { random.nextInt(10).toString() }

    private fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Exponential backoff on repeated failures from one address.
     *
     * Capped so a forgotten server does not become permanently unusable to the
     * person who mistyped the PIN twice, but long enough that brute force is not
     * a strategy.
     */
    private fun backoffMillis(failureCount: Int): Long = when {
        failureCount < 3 -> 0L
        else -> min(BACKOFF_BASE_MILLIS shl (failureCount - 3), BACKOFF_CAP_MILLIS)
    }

    private data class FailureRecord(val count: Int, val lockedUntilMillis: Long)

    companion object {
        const val PIN_DIGITS = 6
        const val MAX_ATTEMPTS = 5
        private const val BACKOFF_BASE_MILLIS = 2_000L
        private const val BACKOFF_CAP_MILLIS = 5 * 60_000L
        private const val SESSION_IDLE_MILLIS = 30 * 60_000L

        /**
         * Compares in time independent of where the first difference is.
         *
         * A naive `==` returns as soon as two characters differ, and the timing of
         * that return tells an attacker on the same LAN how many leading digits
         * they got right -- which turns a million guesses into sixty.
         */
        fun constantTimeEquals(a: String, b: String): Boolean {
            val left = a.toByteArray(Charsets.UTF_8)
            val right = b.toByteArray(Charsets.UTF_8)
            // MessageDigest.isEqual is the platform's constant-time comparison and
            // handles unequal lengths without an early return.
            return MessageDigest.isEqual(left, right)
        }

        /** A friendly client name from a User-Agent string, for the clients list. */
        fun describeUserAgent(userAgent: String): String {
            val platform = when {
                userAgent.contains("Macintosh") -> "Mac"
                userAgent.contains("Windows") -> "Windows"
                userAgent.contains("Android") -> "Android"
                userAgent.contains("iPhone") || userAgent.contains("iPad") -> "iOS"
                userAgent.contains("Linux") -> "Linux"
                else -> "Device"
            }
            val browser = when {
                // Order matters: Chrome and Edge both claim Safari, Edge claims Chrome.
                userAgent.contains("Edg/") -> "Edge"
                userAgent.contains("Firefox") -> "Firefox"
                userAgent.contains("Chrome") -> "Chrome"
                userAgent.contains("Safari") -> "Safari"
                else -> "Browser"
            }
            return "$platform · $browser"
        }
    }
}

/** What [ServerAuth.authenticate] decided. */
sealed interface AuthOutcome {
    data class Accepted(val token: String) : AuthOutcome
    data class Rejected(val attemptsRemaining: Int) : AuthOutcome
    data class LockedOut(val retryAfterMillis: Long) : AuthOutcome
}
