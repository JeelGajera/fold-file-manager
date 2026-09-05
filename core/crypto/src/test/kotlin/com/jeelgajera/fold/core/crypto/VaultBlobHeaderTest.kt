package com.jeelgajera.fold.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The blob header, tested on the JVM.
 *
 * The Keystore half of [VaultCrypto] needs real hardware and lives in the
 * instrumented suite. The header is pure byte handling, and it is the part an
 * attacker can actually write to -- blobs are files on disk that another process
 * with root could rewrite -- so it gets an adversarial unit suite of its own.
 */
class VaultBlobHeaderTest {

    private val alias = VaultCrypto.aliasFor(1)
    private val wrapIv = ByteArray(12) { it.toByte() }
    private val wrappedDek = ByteArray(48) { (it * 3).toByte() }
    private val dataIv = ByteArray(12) { (it + 100).toByte() }

    private fun encoded(
        alias: String = this.alias,
        wrapIv: ByteArray = this.wrapIv,
        wrappedDek: ByteArray = this.wrappedDek,
        dataIv: ByteArray = this.dataIv,
        payload: ByteArray = "payload".toByteArray(),
    ): ByteArray = ByteArrayOutputStream().apply {
        VaultCrypto.writeHeader(this, alias, wrapIv, wrappedDek, dataIv)
        write(payload)
    }.toByteArray()

    @Test
    fun `round-trips every field`() {
        val header = VaultCrypto.readHeader(ByteArrayInputStream(encoded()))
        assertEquals(VaultCrypto.FORMAT_VERSION, header.version)
        assertEquals(alias, header.alias)
        assertArrayEquals(wrapIv, header.wrapIv)
        assertArrayEquals(wrappedDek, header.wrappedDek)
        assertArrayEquals(dataIv, header.dataIv)
    }

    @Test
    fun `leaves the stream positioned at the payload`() {
        val stream = ByteArrayInputStream(encoded(payload = "PAYLOAD".toByteArray()))
        VaultCrypto.readHeader(stream)
        assertEquals("PAYLOAD", stream.readBytes().decodeToString())
    }

    @Test
    fun `records the alias so a rotated vault still opens`() {
        // The point of versioned aliases: a blob wrapped under generation 1 keeps
        // naming generation 1 after the vault has moved on to generation 2.
        val header = VaultCrypto.readHeader(ByteArrayInputStream(encoded(alias = VaultCrypto.aliasFor(7))))
        assertEquals(VaultCrypto.aliasFor(7), header.alias)
    }

    // --- rejection --------------------------------------------------------

    @Test
    fun `rejects a file that is not a vault blob`() {
        val error = assertThrows(VaultException.Tampered::class.java) {
            VaultCrypto.readHeader(ByteArrayInputStream("just a text file".toByteArray()))
        }
        assertTrue(error.message!!.contains("Not a vault blob"))
    }

    @Test
    fun `rejects an unknown format version`() {
        val bytes = encoded()
        bytes[4] = 99
        assertThrows(VaultException.Tampered::class.java) {
            VaultCrypto.readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `rejects an alias that is not one of FOLD's`() {
        // A rewritten header pointing at some other app's Keystore alias.
        assertThrows(VaultException.Tampered::class.java) {
            VaultCrypto.readHeader(ByteArrayInputStream(encoded(alias = "com.other.app.key")))
        }
    }

    @Test
    fun `rejects a truncated header instead of reading past the end`() {
        val full = encoded()
        for (cut in 1 until full.size.coerceAtMost(40)) {
            assertThrows(
                "truncating to $cut bytes should be refused",
                VaultException.Tampered::class.java,
            ) {
                VaultCrypto.readHeader(ByteArrayInputStream(full.copyOf(cut)))
            }
        }
    }

    @Test
    fun `rejects an implausible wrapped-key length rather than allocating it`() {
        val bytes = encoded()
        // Find the two big-endian length bytes and claim 65535 bytes of key.
        val lengthOffset = 4 + 1 + 1 + alias.length + 1 + wrapIv.size
        bytes[lengthOffset] = 0xFF.toByte()
        bytes[lengthOffset + 1] = 0xFF.toByte()
        assertThrows(VaultException.Tampered::class.java) {
            VaultCrypto.readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `rejects a zero-length alias`() {
        val bytes = encoded()
        bytes[5] = 0
        assertThrows(VaultException.Tampered::class.java) {
            VaultCrypto.readHeader(ByteArrayInputStream(bytes))
        }
    }

    // --- writer guards ----------------------------------------------------

    @Test
    fun `refuses to write a header it could not read back`() {
        val sink = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.writeHeader(sink, alias, wrapIv, wrappedDek, ByteArray(8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.writeHeader(sink, "", wrapIv, wrappedDek, dataIv)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.writeHeader(sink, alias, ByteArray(0), wrappedDek, dataIv)
        }
    }

    @Test
    fun `the extension badge never comes out empty`() {
        assertEquals("PDF", entry("passport-scan.pdf").extensionBadge)
        assertEquals("?", entry("ssh-id_ed25519").extensionBadge)
        assertEquals("?", entry(".recovery").extensionBadge)
    }

    private fun entry(name: String) = VaultEntry(
        blobId = "abc",
        displayName = name,
        mimeType = "application/octet-stream",
        plaintextSize = 1,
        addedAtMillis = 0,
    )
}
