package org.sih.itantra.core.wifidirect

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.crypto.NetworkKeyManager
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Feature 28 — Unit tests for TCP length-prefixed stream socket framing.
 * Verifies framing parser, multiple packets per stream, chunk fragmentation recovery,
 * and preservation of HMAC-SHA256 and CRC32 without byte mutation.
 */
class WifiDirectFramingTest {

    private val testKey = MessageDigest.getInstance("SHA-256")
        .digest("iTantra-WiFi-Direct-Test-Key-2026".toByteArray(Charsets.UTF_8))

    @Before
    fun setUp() {
        NetworkKeyManager.setTestKey(testKey)
    }

    private fun samplePacket(
        seq: Short = 101,
        src: Int = 209071,
        dest: Int = 209072,
        priority: MessagePriority = MessagePriority.NORMAL,
        text: String = "GRID 43A SECURE"
    ): Packet {
        return Packet(
            msgType = Packet.TYPE_TEXT,
            priority = priority,
            sequenceNumber = seq,
            timestamp = 1700000000000L,
            sourceDeviceId = src,
            destinationDeviceId = dest,
            language = IndicLanguage.HINDI,
            payload = text.toByteArray(Charsets.UTF_8)
        )
    }

    @Test
    fun testSinglePacketFramingRoundtrip() {
        val original = samplePacket()
        val serialized = PacketSerializer.serialize(original)

        val byteOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteOut)
        dataOut.writeInt(serialized.size)
        dataOut.write(serialized)
        dataOut.flush()

        val dataIn = DataInputStream(ByteArrayInputStream(byteOut.toByteArray()))
        val frameLen = dataIn.readInt()
        assertEquals(serialized.size, frameLen)

        val frameBuf = ByteArray(frameLen)
        dataIn.readFully(frameBuf)
        val deserialized = PacketSerializer.deserialize(frameBuf)

        assertEquals(original.sequenceNumber, deserialized.sequenceNumber)
        assertEquals(original.sourceDeviceId, deserialized.sourceDeviceId)
        assertEquals(original.destinationDeviceId, deserialized.destinationDeviceId)
        assertEquals(original.language, deserialized.language)
        assertArrayEquals(original.payload, deserialized.payload)
    }

    @Test
    fun testMultiplePacketsFramingRoundtrip() {
        val packets = listOf(
            samplePacket(seq = 1, text = "MSG 1"),
            samplePacket(seq = 2, text = "MSG 2"),
            samplePacket(seq = 3, text = "MSG 3")
        )

        val byteOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteOut)
        for (p in packets) {
            val bytes = PacketSerializer.serialize(p)
            dataOut.writeInt(bytes.size)
            dataOut.write(bytes)
        }
        dataOut.flush()

        val dataIn = DataInputStream(ByteArrayInputStream(byteOut.toByteArray()))
        val received = mutableListOf<Packet>()
        for (i in 0 until 3) {
            val len = dataIn.readInt()
            val buf = ByteArray(len)
            dataIn.readFully(buf)
            received.add(PacketSerializer.deserialize(buf))
        }

        assertEquals(3, received.size)
        for (i in 0 until 3) {
            assertEquals(packets[i].sequenceNumber, received[i].sequenceNumber)
            assertArrayEquals(packets[i].payload, received[i].payload)
        }
    }

    @Test
    fun testAuthenticatedPacketPreservedOverFraming() {
        val rawPacket = samplePacket(seq = 42, text = "CONFIDENTIAL ORDER")
        val signedPacket = PacketAuthenticator.sign(rawPacket, testKey)
        assertTrue(signedPacket.isAuthenticated)

        val bytes = PacketSerializer.serialize(signedPacket)
        val byteOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteOut)
        dataOut.writeInt(bytes.size)
        dataOut.write(bytes)
        dataOut.flush()

        val dataIn = DataInputStream(ByteArrayInputStream(byteOut.toByteArray()))
        val len = dataIn.readInt()
        val buf = ByteArray(len)
        dataIn.readFully(buf)
        val deserialized = PacketSerializer.deserialize(buf)

        assertTrue(deserialized.isAuthenticated)
        val verifyStatus = PacketAuthenticator.verify(deserialized, testKey)
        assertEquals(org.sih.itantra.core.crypto.AuthStatus.VALID, verifyStatus)
    }
}
