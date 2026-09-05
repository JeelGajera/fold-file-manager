package com.jeelgajera.fold.feature.transfer.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * The auth failure paths and the rate limiter.
 *
 * Everything here models an attacker on the same Wi-Fi, because that is exactly
 * who can reach this server.
 */
class ServerAuthTest {

    private var now = 1_000_000L
    private val auth = ServerAuth(clock = { now }, random = SecureRandom())

    private fun attempt(pin: String, from: String = "192.168.1.44") =
        auth.authenticate(pin, from, "Mozilla/5.0 (Macintosh) Safari/17")

    @Test
    fun `a correct PIN yields a session token`() {
        val outcome = attempt(auth.pin)
        assertTrue(outcome is AuthOutcome.Accepted)
        val token = (outcome as AuthOutcome.Accepted).token
        assertEquals(64, token.length)
        assertTrue(auth.validate(token) != null)
    }

    @Test
    fun `the PIN is six digits, not four`() {
        // Deliberately stronger than the design mock. Four digits is ten thousand
        // guesses; on a long-running server that is reachable even through a
        // backoff.
        assertEquals(6, auth.pin.length)
        assertTrue(auth.pin.all { it.isDigit() })
    }

    @Test
    fun `a wrong PIN is rejected and counted`() {
        val wrong = wrongPin()
        val first = attempt(wrong)
        assertTrue(first is AuthOutcome.Rejected)
        assertEquals(ServerAuth.MAX_ATTEMPTS - 1, (first as AuthOutcome.Rejected).attemptsRemaining)
    }

    @Test
    fun `repeated failures lock the address out with a growing backoff`() {
        val wrong = wrongPin()
        // The first two failures are free -- a mistyped PIN is normal.
        repeat(3) { attempt(wrong) }

        val locked = attempt(wrong)
        assertTrue("expected a lockout, got $locked", locked is AuthOutcome.LockedOut)
        val firstWait = (locked as AuthOutcome.LockedOut).retryAfterMillis

        // Wait the lockout out. The next guess is allowed through and rejected,
        // which arms a longer lockout than the one before it.
        now += firstWait + 1
        assertTrue(attempt(wrong) is AuthOutcome.Rejected)

        val again = attempt(wrong)
        assertTrue("expected a second lockout, got $again", again is AuthOutcome.LockedOut)
        assertTrue(
            "the backoff must grow with each round",
            (again as AuthOutcome.LockedOut).retryAfterMillis > firstWait,
        )
    }

    @Test
    fun `a lockout applies per address, not globally`() {
        val wrong = wrongPin()
        repeat(3) { attempt(wrong, from = "192.168.1.44") }
        assertTrue(attempt(wrong, from = "192.168.1.44") is AuthOutcome.LockedOut)

        // Someone else on the network is not punished for it.
        assertTrue(attempt(auth.pin, from = "192.168.1.9") is AuthOutcome.Accepted)
    }

    @Test
    fun `a correct PIN clears the failure record`() {
        val wrong = wrongPin()
        attempt(wrong)
        attempt(wrong)
        assertTrue(attempt(auth.pin) is AuthOutcome.Accepted)
        // The counter reset, so the next wrong guess starts from the top again.
        val next = attempt(wrong)
        assertEquals(ServerAuth.MAX_ATTEMPTS - 1, (next as AuthOutcome.Rejected).attemptsRemaining)
    }

    @Test
    fun `comparison is constant time and length independent`() {
        assertTrue(ServerAuth.constantTimeEquals("472913", "472913"))
        assertFalse(ServerAuth.constantTimeEquals("472913", "472914"))
        // Different lengths must not throw and must not short-circuit.
        assertFalse(ServerAuth.constantTimeEquals("472913", "4729"))
        assertFalse(ServerAuth.constantTimeEquals("", "472913"))
    }

    @Test
    fun `an unknown or empty token is not a session`() {
        assertNull(auth.validate(null))
        assertNull(auth.validate(""))
        assertNull(auth.validate("f".repeat(64)))
    }

    @Test
    fun `a session expires after its idle window`() {
        val token = (attempt(auth.pin) as AuthOutcome.Accepted).token
        now += 31 * 60_000L
        assertNull(auth.validate(token))
    }

    @Test
    fun `validate refreshes the idle timer`() {
        val token = (attempt(auth.pin) as AuthOutcome.Accepted).token
        now += 20 * 60_000L
        assertTrue(auth.validate(token) != null)
        now += 20 * 60_000L
        // Still alive, because the check at +20m moved the window.
        assertTrue(auth.validate(token) != null)
    }

    @Test
    fun `revokeAll drops every session`() {
        val token = (attempt(auth.pin) as AuthOutcome.Accepted).token
        auth.revokeAll()
        assertNull(auth.validate(token))
    }

    @Test
    fun `each session draws a fresh PIN`() {
        // Any single redraw can legitimately land on the same six digits, so the
        // property under test is that the PIN is redrawn at all -- checked over
        // enough rounds that a fixed PIN could not pass by chance.
        val drawn = (1..40).map { auth.regeneratePin() }.toSet()
        assertTrue("PIN must be regenerated per session", drawn.size > 1)
        assertTrue(drawn.all { it.length == 6 && it.all(Char::isDigit) })
    }

    @Test
    fun `idleFor reports true when nothing has connected`() {
        assertTrue(auth.idleFor(idleMinutes = 15))
        attempt(auth.pin)
        assertFalse(auth.idleFor(idleMinutes = 15))
        now += 16 * 60_000L
        assertTrue(auth.idleFor(idleMinutes = 15))
    }

    @Test
    fun `user agents become readable client names`() {
        assertEquals(
            "Mac · Safari",
            ServerAuth.describeUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X) Safari/605"),
        )
        assertEquals(
            "Windows · Firefox",
            ServerAuth.describeUserAgent("Mozilla/5.0 (Windows NT 10.0) Gecko Firefox/128"),
        )
        // Edge claims both Chrome and Safari; the order of the checks matters.
        assertEquals(
            "Windows · Edge",
            ServerAuth.describeUserAgent("Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537 Edg/120"),
        )
        assertEquals("Device · Browser", ServerAuth.describeUserAgent(""))
    }

    private fun wrongPin(): String = if (auth.pin == "000000") "111111" else "000000"
}
