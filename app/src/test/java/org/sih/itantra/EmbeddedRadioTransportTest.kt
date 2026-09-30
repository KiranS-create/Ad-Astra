package org.sih.itantra

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.transport.EmbeddedRadioTransport

class EmbeddedRadioTransportTest {

    private lateinit var radio: EmbeddedRadioTransport

    @Before
    fun setUp() {
        radio = EmbeddedRadioTransport()
        radio.enableSimulationLoopback(true)
    }

    @Test
    fun testCrc16CcittKnownVectors() {
        // Standard CCITT "123456789" ASCII has known CRC-16 CCITT 0x29B1
        val testData = "123456789".toByteArray(Charsets.US_ASCII)
        val computedCrc = EmbeddedRadioTransport.calculateCrc16(testData)
        assertEquals(0x29B1, computedCrc)
    }

    @Test
    fun testFrameEncodingStructure() {
        val packet = Packet(
            sequenceNumber = 42.toShort(),
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 101,
            language = IndicLanguage.HINDI,
            payload = "बाढ़ सहायता".toByteArray(Charsets.UTF_8)
        )
        val frame = radio.encodeFrame(packet, channelId = 5.toByte())

        // Header: 7 bytes (3B sync, 2B length, 1B channel, 1B flags)
        // Trailer: 2 bytes CRC
        assertTrue(frame.size >= 7 + 2)

        // Verify Sync Word 'SIH'
        assertEquals(0x53.toByte(), frame[0])
        assertEquals(0x49.toByte(), frame[1])
        assertEquals(0x48.toByte(), frame[2])

        // Verify Channel
        assertEquals(5.toByte(), frame[5])

        // Verify CRC matches
        val crc = EmbeddedRadioTransport.calculateCrc16(frame, 0, frame.size - 2)
        val highByte = (crc shr 8).toByte()
        val lowByte = (crc and 0xFF).toByte()
        assertEquals(highByte, frame[frame.size - 2])
        assertEquals(lowByte, frame[frame.size - 1])
    }

    @Test
    fun testFeedCorruptedCrcRejectsFrame() {
        val packet = Packet(
            sequenceNumber = 99.toShort(),
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 202,
            language = IndicLanguage.ENGLISH,
            payload = "CORRUPT_TEST".toByteArray(Charsets.UTF_8)
        )
        val frame = radio.encodeFrame(packet)

        // Corrupt a byte in the payload
        frame[7] = (frame[7].toInt() xor 0xFF).toByte()

        val parsed = radio.feedIncomingBytes(frame)
        assertTrue("Corrupted radio frame must be rejected by CRC check", parsed.isEmpty())
    }

    @Test
    fun testSlidingWindowStreamParserHandlesFragmentationAndNoise() {
        val packet = Packet(
            sequenceNumber = 12.toShort(),
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 303,
            language = IndicLanguage.BENGALI,
            payload = "বন্যা ত্রাণ".toByteArray(Charsets.UTF_8)
        )
        val radioFrame = radio.encodeFrame(packet)

        // Feed noise prefix to simulate real line noise / unaligned UART bytes
        val noisePrefix = byteArrayOf(0x00, 0x12, 0x53, 0x53, 0x49, 0x00)
        val parsedNoise = radio.feedIncomingBytes(noisePrefix)
        assertTrue(parsedNoise.isEmpty())

        // Feed first half of frame
        val half1 = radioFrame.sliceArray(0 until 12)
        val half2 = radioFrame.sliceArray(12 until radioFrame.size)

        val parsedHalf1 = radio.feedIncomingBytes(half1)
        assertTrue("Packet should not be parsed yet from partial half", parsedHalf1.isEmpty())

        // Feed second half
        val parsedHalf2 = radio.feedIncomingBytes(half2)
        assertEquals(1, parsedHalf2.size)
        assertEquals(packet.sourceDeviceId, parsedHalf2[0].sourceDeviceId)
        assertEquals(packet.sequenceNumber, parsedHalf2[0].sequenceNumber)
        assertEquals("বন্যা ত্রাণ", String(parsedHalf2[0].payload, Charsets.UTF_8))
    }

    @Test
    fun testSimulationLoopbackRoundTrip() = runBlocking {
        radio.start()

        val testPacket = Packet(
            sequenceNumber = 500.toShort(),
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 404,
            priority = MessagePriority.ALERT,
            language = IndicLanguage.TAMIL,
            payload = "வெள்ள அபாயம்".toByteArray(Charsets.UTF_8)
        )

        val sendSuccess = radio.send(testPacket)
        assertTrue("Send should succeed in simulation mode", sendSuccess)

        val received = withTimeout(2000) {
            radio.receivedPackets.first()
        }

        assertEquals(testPacket.sourceDeviceId, received.sourceDeviceId)
        assertEquals(testPacket.sequenceNumber, received.sequenceNumber)
        assertEquals(testPacket.priority, received.priority)
        assertEquals(testPacket.language, received.language)
        assertEquals("வெள்ள அபாயம்", String(received.payload, Charsets.UTF_8))

        radio.stop()
    }
}
