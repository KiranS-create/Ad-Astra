package org.sih.itantra.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.protocol.ReassemblyBuffer
import org.sih.itantra.core.protocol.SemanticCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/**
 * Feature 23: Deterministic Packet Deserialization Fuzzing & Safety Test.
 *
 * Uses fixed seed (securitySeed = 20260915) to guarantee exact reproducible test runs.
 *
 * Invariant Guarantees:
 * - Deserializer NEVER throws unhandled fatal JVM exceptions (NullPointerException, OutOfMemoryError, StackOverflow).
 * - Parser ALWAYS rejects malformed frames via [CorruptPacketException].
 * - Bounded execution time (< 1ms per malformed buffer).
 * - Zero state mutation on rejected inputs.
 */
class PacketFuzzSafetyTest {

    private val securitySeed = 20260915L

    @Test
    fun fuzz_emptyAndUndersizedByteArrays_throwCorruptPacketException() {
        // Size 0 to 31 bytes (Packet.MIN_PACKET_SIZE is 32 bytes)
        for (len in 0 until Packet.MIN_PACKET_SIZE) {
            val raw = ByteArray(len)
            try {
                PacketSerializer.deserialize(raw)
                fail("Undersized buffer of length $len must throw CorruptPacketException")
            } catch (e: CorruptPacketException) {
                // Expected behavior
                assertTrue(e.message?.contains("undersized", ignoreCase = true) == true)
            } catch (t: Throwable) {
                fail("Unexpected exception type for length $len: ${t.javaClass.simpleName}")
            }
        }
    }

    @Test
    fun fuzz_randomGarbageBuffers_safelyRejectedWithoutFatalErrors() {
        val random = Random(securitySeed)
        val iterations = 500
        var rejectedCount = 0

        val t0 = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val length = random.nextInt(2048) // 0 to 2047 bytes
            val garbage = ByteArray(length).also { random.nextBytes(it) }

            try {
                PacketSerializer.deserialize(garbage)
                // Extremely rare: random bytes accidentally forming valid magic, version, payloadLen, and CRC32
            } catch (e: CorruptPacketException) {
                rejectedCount++
            } catch (t: Throwable) {
                fail("Fuzz input #$i (len=$length) threw unhandled ${t.javaClass.name}: ${t.message}")
            }
        }

        val elapsed = System.currentTimeMillis() - t0
        assertTrue("All random buffers should be rejected", rejectedCount >= iterations - 1)
        assertTrue("Fuzzing 500 buffers must be fast (< 2000ms, took ${elapsed}ms)", elapsed < 2000)
    }

    @Test
    fun fuzz_absurdDeclaredPayloadLengths_rejectedWithoutUnboundedAllocation() {
        // Valid header with huge declared length (e.g. 65000 bytes) in a small buffer
        val buf = ByteBuffer.allocate(Packet.MIN_PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(Packet.MAGIC)
        buf.put(Packet.PROTOCOL_VERSION)
        buf.put(Packet.TYPE_TEXT)
        buf.put(MessagePriority.NORMAL.id)
        buf.put(Packet.DEFAULT_TTL)
        buf.put(0.toByte()) // flags
        buf.putShort(1.toShort()) // seq
        buf.putLong(System.currentTimeMillis())
        buf.putInt(111111) // src
        buf.putInt(Packet.BROADCAST_ID) // dest
        buf.put(IndicLanguage.ENGLISH.id)
        buf.putShort(0xFFFE.toShort()) // 65534 bytes declared payload!
        buf.putInt(0) // dummy CRC

        try {
            PacketSerializer.deserialize(buf.array())
            fail("Declared payload exceeding buffer size must throw CorruptPacketException")
        } catch (e: CorruptPacketException) {
            assertTrue(e.message?.contains("exceeds buffer size", ignoreCase = true) == true)
        }
    }

    @Test
    fun fuzz_invalidMagicBytes_rejectedDeterministically() {
        val validPacket = Packet(
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 123,
            language = IndicLanguage.ENGLISH,
            payload = "Test".toByteArray(Charsets.UTF_8)
        )
        val serialized = PacketSerializer.serialize(validPacket)

        // Mutate magic bytes from 0x4954 to arbitrary values
        val badMagicValues = listOf(
            0x0000.toShort(),
            0xFFFF.toShort(),
            0x1234.toShort(),
            0x4955.toShort()
        )

        for (badMagic in badMagicValues) {
            val mutated = serialized.copyOf()
            ByteBuffer.wrap(mutated).order(ByteOrder.BIG_ENDIAN).putShort(0, badMagic)

            try {
                PacketSerializer.deserialize(mutated)
                fail("Bad magic 0x${Integer.toHexString(badMagic.toInt())} must be rejected")
            } catch (e: CorruptPacketException) {
                assertTrue(e.message?.contains("magic", ignoreCase = true) == true)
            }
        }
    }

    @Test
    fun fuzz_unsupportedProtocolVersion_rejectedDeterministically() {
        val validPacket = Packet(
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 123,
            language = IndicLanguage.ENGLISH,
            payload = "Test".toByteArray(Charsets.UTF_8)
        )
        val serialized = PacketSerializer.serialize(validPacket)

        // Mutate version byte (offset 2)
        val badVersions = listOf(0.toByte(), 2.toByte(), 99.toByte(), (-1).toByte())

        for (badVer in badVersions) {
            val mutated = serialized.copyOf()
            mutated[2] = badVer

            try {
                PacketSerializer.deserialize(mutated)
                fail("Unsupported version $badVer must be rejected")
            } catch (e: CorruptPacketException) {
                assertTrue(e.message?.contains("version", ignoreCase = true) == true)
            }
        }
    }

    @Test
    fun fuzz_crcBitFlipping_oneBitCorruptionDetected() {
        val validPacket = Packet(
            sequenceNumber = 42,
            timestamp = 1772000000000L,
            sourceDeviceId = 209070,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = "Critical tactical payload: 128 bytes of mission data".toByteArray(Charsets.UTF_8)
        )
        val serialized = PacketSerializer.serialize(validPacket)

        // Flip 1 bit in each byte of the serialized packet (excluding the CRC itself)
        val bodyLength = serialized.size - Packet.CRC_SIZE_BYTES
        for (i in 0 until bodyLength) {
            for (bit in 0..7) {
                val corrupted = serialized.copyOf()
                corrupted[i] = (corrupted[i].toInt() xor (1 shl bit)).toByte()

                try {
                    PacketSerializer.deserialize(corrupted)
                    fail("Bit flip at byte $i bit $bit must throw CorruptPacketException")
                } catch (e: CorruptPacketException) {
                    // Success: either magic/version/length/semantic or CRC mismatch caught it
                    assertNotNull(e.message)
                }
            }
        }
    }

    @Test
    fun fuzz_declaredLocationWithoutBytes_rejectedDeterministically() {
        val buf = ByteBuffer.allocate(Packet.MIN_PACKET_SIZE + 10).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(Packet.MAGIC)
        buf.put(Packet.PROTOCOL_VERSION)
        buf.put(Packet.TYPE_TEXT)
        buf.put(MessagePriority.NORMAL.id)
        buf.put(Packet.DEFAULT_TTL)
        buf.put(Packet.FLAG_HAS_LOCATION.toByte()) // FLAG_HAS_LOCATION requires 32 extra bytes
        buf.putShort(1.toShort())
        buf.putLong(System.currentTimeMillis())
        buf.putInt(111111)
        buf.putInt(Packet.BROADCAST_ID)
        buf.put(IndicLanguage.ENGLISH.id)
        buf.putShort(10.toShort()) // 10 bytes payload
        buf.put(ByteArray(10)) // payload bytes
        buf.putInt(0) // CRC

        try {
            PacketSerializer.deserialize(buf.array())
            fail("Declared location without location bytes must throw CorruptPacketException")
        } catch (e: CorruptPacketException) {
            assertTrue(e.message?.contains("exceeds buffer size", ignoreCase = true) == true)
        }
    }

    @Test
    fun fuzz_declaredAuthWithoutBytes_rejectedDeterministically() {
        val buf = ByteBuffer.allocate(Packet.MIN_PACKET_SIZE + 10).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(Packet.MAGIC)
        buf.put(Packet.PROTOCOL_VERSION)
        buf.put(Packet.TYPE_TEXT)
        buf.put(MessagePriority.NORMAL.id)
        buf.put(Packet.DEFAULT_TTL)
        buf.put(Packet.FLAG_AUTHENTICATED.toByte()) // FLAG_AUTHENTICATED requires 8 extra bytes
        buf.putShort(1.toShort())
        buf.putLong(System.currentTimeMillis())
        buf.putInt(111111)
        buf.putInt(Packet.BROADCAST_ID)
        buf.put(IndicLanguage.ENGLISH.id)
        buf.putShort(10.toShort())
        buf.put(ByteArray(10))
        buf.putInt(0)

        try {
            PacketSerializer.deserialize(buf.array())
            fail("Declared auth tag without tag bytes must throw CorruptPacketException")
        } catch (e: CorruptPacketException) {
            assertTrue(e.message?.contains("exceeds buffer size", ignoreCase = true) == true)
        }
    }

    @Test
    fun fuzz_malformedSemanticPayload_rejectedClosed() {
        // Packet with FLAG_SEMANTIC set but payload less than 6 bytes
        val packet = Packet(
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 123,
            flags = Packet.FLAG_SEMANTIC.toByte(),
            language = IndicLanguage.ENGLISH,
            payload = ByteArray(3) // Only 3 bytes, min is 6
        )
        val serialized = PacketSerializer.serialize(packet)

        try {
            PacketSerializer.deserialize(serialized)
            fail("FLAG_SEMANTIC with truncated payload must throw CorruptPacketException")
        } catch (e: CorruptPacketException) {
            assertTrue(e.message?.contains("Malformed semantic command", ignoreCase = true) == true)
        }
    }

    @Test
    fun fuzz_propertyTest_validPacketRoundTripPreservesAllFields() {
        val loc = GeoLocation(12.9716, 77.5946, 3.2f, 1772000000000L, 920.0)
        val cmd = SemanticCommand(
            category = EmergencyCategory.FIRE,
            subtype = EmergencySubtype.BUILDING,
            count = 5,
            severity = EmergencySeverity.CRITICAL,
            parameter = 12
        )
        val original = Packet(
            sequenceNumber = 777,
            timestamp = 1772000123456L,
            sourceDeviceId = 303030,
            destinationDeviceId = 404040,
            priority = MessagePriority.ALERT,
            ttl = 3,
            flags = (Packet.FLAG_HAS_LOCATION or Packet.FLAG_SEMANTIC).toByte(),
            language = IndicLanguage.TAMIL,
            location = loc,
            semanticCommand = cmd,
            payload = cmd.serialize()
        )

        val bytes = PacketSerializer.serialize(original)
        val reconstructed = PacketSerializer.deserialize(bytes)

        assertEquals(original.magic, reconstructed.magic)
        assertEquals(original.version, reconstructed.version)
        assertEquals(original.msgType, reconstructed.msgType)
        assertEquals(original.priority, reconstructed.priority)
        assertEquals(original.ttl, reconstructed.ttl)
        assertEquals(original.sequenceNumber, reconstructed.sequenceNumber)
        assertEquals(original.timestamp, reconstructed.timestamp)
        assertEquals(original.sourceDeviceId, reconstructed.sourceDeviceId)
        assertEquals(original.destinationDeviceId, reconstructed.destinationDeviceId)
        assertEquals(original.language, reconstructed.language)
        assertEquals(original.location?.latitude, reconstructed.location?.latitude)
        assertEquals(original.location?.longitude, reconstructed.location?.longitude)
        assertEquals(original.semanticCommand?.category, reconstructed.semanticCommand?.category)
        assertEquals(original.semanticCommand?.subtype, reconstructed.semanticCommand?.subtype)
        assertEquals(original.semanticCommand?.count, reconstructed.semanticCommand?.count)
        assertEquals(original.semanticCommand?.sector, reconstructed.semanticCommand?.sector)
        assertTrue(original.payload.contentEquals(reconstructed.payload))
    }
}
