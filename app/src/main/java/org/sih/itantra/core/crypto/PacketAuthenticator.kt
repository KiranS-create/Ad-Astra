package org.sih.itantra.core.crypto

import org.sih.itantra.core.protocol.Packet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class AuthStatus {
    VALID,
    MISSING_TAG,
    MALFORMED_TAG,
    INVALID_TAG,
    UNKNOWN_KEY
}

data class AuthVerificationResult(
    val status: AuthStatus,
    val elapsedNanos: Long
) {
    val isValid: Boolean get() = status == AuthStatus.VALID
}

/**
 * High-performance, offline packet authenticator for iTantra tactical mesh networks.
 *
 * Cryptographic Construction:
 * - HMAC-SHA256 (RFC 2104 / FIPS 198-1).
 * - Truncated 8-byte (64-bit) authentication tag.
 * - Constant-time tag verification via [MessageDigest.isEqual].
 *
 * MANET Relay Hop Invariance:
 * - Covers all security-critical, end-to-end immutable fields (header, sequence, timestamp,
 *   addresses, canonical flags, language, location, and payload).
 * - Excludes mutable router hop fields (`ttl`, `FLAG_FORWARDED`, `crc32`).
 * - Allows packets to traverse multi-hop MANET relays with decremented TTL without breaking
 *   end-to-end sender authenticity.
 */
object PacketAuthenticator {

    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Serializes all security-critical invariant fields into a canonical byte representation
     * for HMAC computation.
     */
    fun getCanonicalBytes(packet: Packet): ByteArray {
        val hasSemantic = ((packet.flags.toInt() and Packet.FLAG_SEMANTIC) != 0) || packet.semanticCommand != null
        val payload = if (hasSemantic && packet.payload.isEmpty() && packet.semanticCommand != null) {
            packet.semanticCommand.serialize()
        } else {
            packet.payload
        }
        val effectiveFlags = if (hasSemantic) {
            (packet.flags.toInt() or Packet.FLAG_SEMANTIC).toByte()
        } else {
            packet.flags
        }

        // Mask out hop-mutable FLAG_FORWARDED to maintain MANET relay invariance
        val canonicalFlags = (effectiveFlags.toInt() and Packet.FLAG_FORWARDED.inv()).toByte()

        val hasLocation = (effectiveFlags.toInt() and Packet.FLAG_HAS_LOCATION) != 0 && packet.location != null
        val locBytes = if (hasLocation) Packet.LOCATION_SIZE_BYTES else 0

        // Invariant header size: 27 bytes (Header 28 bytes minus 1 byte mutable TTL)
        val canonicalHeaderSize = 27
        val totalSize = canonicalHeaderSize + locBytes + payload.size

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(packet.magic)
        buffer.put(packet.version)
        buffer.put(packet.msgType)
        buffer.put(packet.priority.id)
        buffer.put(canonicalFlags)
        buffer.putShort(packet.sequenceNumber)
        buffer.putLong(packet.timestamp)
        buffer.putInt(packet.sourceDeviceId)
        buffer.putInt(packet.destinationDeviceId)
        buffer.put(packet.language.id)
        buffer.putShort(payload.size.toShort())

        if (hasLocation) {
            val loc = packet.location!!
            buffer.putDouble(loc.latitude)
            buffer.putDouble(loc.longitude)
            buffer.putFloat(loc.accuracy)
            buffer.putLong(loc.timestamp)
            buffer.putFloat(loc.altitude?.toFloat() ?: Float.NaN)
        }

        buffer.put(payload)
        return buffer.array()
    }

    /**
     * Computes the truncated 8-byte HMAC-SHA256 authentication tag for the packet.
     *
     * @param packet The packet whose canonical invariant fields will be authenticated.
     * @param keyBytes The 256-bit symmetric key.
     * @return 8-byte truncated authentication tag.
     */
    fun computeTag(packet: Packet, keyBytes: ByteArray): ByteArray {
        val canonicalBytes = getCanonicalBytes(packet)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
        val fullTag = mac.doFinal(canonicalBytes)
        return fullTag.copyOfRange(0, Packet.AUTH_TAG_SIZE_BYTES)
    }

    /**
     * Computes the authentication tag and measures the elapsed nanoseconds.
     */
    fun computeTagWithLatency(packet: Packet, keyBytes: ByteArray): Pair<ByteArray, Long> {
        val t0 = System.nanoTime()
        val tag = computeTag(packet, keyBytes)
        val elapsed = System.nanoTime() - t0
        return Pair(tag, elapsed)
    }

    /**
     * Signs a packet by calculating its 8-byte authentication tag and setting [Packet.FLAG_AUTHENTICATED].
     *
     * @return A copy of the packet with `FLAG_AUTHENTICATED` set and `authTag` populated.
     */
    fun sign(packet: Packet, keyBytes: ByteArray): Packet {
        val authenticatedFlags = (packet.flags.toInt() or Packet.FLAG_AUTHENTICATED).toByte()
        val intermediate = packet.copy(flags = authenticatedFlags, authTag = null)
        val tag = computeTag(intermediate, keyBytes)
        return intermediate.copy(authTag = tag)
    }

    /**
     * Signs a packet and records generation latency.
     */
    fun signWithLatency(packet: Packet, keyBytes: ByteArray): Pair<Packet, Long> {
        val t0 = System.nanoTime()
        val signedPacket = sign(packet, keyBytes)
        val elapsed = System.nanoTime() - t0
        return Pair(signedPacket, elapsed)
    }

    /**
     * Verifies the authentication tag of an incoming packet.
     *
     * @param packet The packet to verify.
     * @param keyBytes The 256-bit symmetric key, or null if unprovisioned.
     * @return [AuthVerificationResult] detailing status and verification latency.
     */
    fun verify(packet: Packet, keyBytes: ByteArray?): AuthVerificationResult {
        val t0 = System.nanoTime()

        if (keyBytes == null || keyBytes.isEmpty()) {
            val elapsed = System.nanoTime() - t0
            return AuthVerificationResult(AuthStatus.UNKNOWN_KEY, elapsed)
        }

        val tag = packet.authTag
        if (tag == null) {
            val elapsed = System.nanoTime() - t0
            return AuthVerificationResult(AuthStatus.MISSING_TAG, elapsed)
        }

        if (tag.size != Packet.AUTH_TAG_SIZE_BYTES) {
            val elapsed = System.nanoTime() - t0
            return AuthVerificationResult(AuthStatus.MALFORMED_TAG, elapsed)
        }

        val expectedTag = computeTag(packet, keyBytes)
        val matches = MessageDigest.isEqual(tag, expectedTag)
        val elapsed = System.nanoTime() - t0

        return if (matches) {
            AuthVerificationResult(AuthStatus.VALID, elapsed)
        } else {
            AuthVerificationResult(AuthStatus.INVALID_TAG, elapsed)
        }
    }
}
