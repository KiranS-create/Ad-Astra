package org.sih.itantra.core.mesh

import android.util.Log
import org.sih.itantra.core.context.ConflictResolutionResult
import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.context.SharedContextStore
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.vbr.ContextDelta
import org.sih.itantra.core.vbr.DecodedDeltaPayload
import org.sih.itantra.core.vbr.SemanticBase
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * Result of destination ingress evaluation for [ContextDelta] payloads (Cases A–F).
 */
data class DestinationReconstructionResult(
    val status: String,
    val forwardingAction: String,
    val isFallback: Boolean,
    val displayText: String,
    val ttsSpeechText: String,
    val semanticSummary: String?,
    val contextId: Int,
    val contextVersion: Int,
    val confidence: Int?,
    val deltaSummary: String,
    val basePayloadBytes: Int,
    val enhPayloadBytes: Int,
    val enhancementReceived: Boolean,
    val schemaVersion: Int,
    val updatedContext: SharedContextEntry? = null
)

enum class ContextRelayDecision {
    FORWARD_AND_DELIVER,
    FORWARD_ONLY,
    DELIVER_LOCAL_ONLY,
    DROP_DUPLICATE,
    DROP_SELF,
    DROP_TTL_EXPIRED
}

data class ContextRelayEvaluation(
    val decision: ContextRelayDecision,
    val forwardedPacket: Packet? = null,
    val dropReason: String? = null,
    val isPayloadPreserved: Boolean = true,
    val wirePayloadBytes: Int = 0
)

/**
 * Feature 20: Context-Aware Multi-Hop Relay + Delta Forwarding Engine.
 *
 * Core Principles:
 * 1. Forward the smallest valid representation without changing its semantic meaning.
 * 2. Never expand CONTEXT_DELTA or SEMANTIC_BASE payloads into full text on intermediate hops.
 * 3. Preserve contextId, version, sourceDeviceId, and authTag immutability across hops.
 * 4. Deterministically handle destination ingress Cases A–F.
 */
class ContextAwareRelayRouter(
    val localDeviceId: Int,
    private val cacheCapacity: Int = 1000
) {
    companion object {
        private const val TAG = "ContextAwareRelay"
    }

    private val countDeltasForwarded = AtomicLong(0)
    private val countBasesForwarded = AtomicLong(0)
    private val countOtherForwarded = AtomicLong(0)
    private val countDuplicatesDropped = AtomicLong(0)
    private val countTtlExpired = AtomicLong(0)
    private val countSelfDropped = AtomicLong(0)

    // Bounded thread-safe duplicate suppression cache
    private val seenCache: MutableMap<PacketKey, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<PacketKey, Long>(cacheCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PacketKey, Long>?): Boolean {
                return size > cacheCapacity
            }
        }
    )

    /**
     * Evaluates an incoming packet for multi-hop relaying with strict representation preservation.
     */
    fun evaluateRelay(
        packet: Packet,
        isRelayEnabled: Boolean = true
    ): ContextRelayEvaluation {
        // 1. Loopback prevention
        if (packet.sourceDeviceId == localDeviceId) {
            countSelfDropped.incrementAndGet()
            Log.d(TAG, "RELAY DROP SELF: source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return ContextRelayEvaluation(
                decision = ContextRelayDecision.DROP_SELF,
                dropReason = "Loopback packet originated from local device",
                wirePayloadBytes = packet.payload.size
            )
        }

        // 2. Duplicate suppression
        val key = PacketKey(packet.sourceDeviceId, packet.sequenceNumber)
        val isDuplicate = synchronized(seenCache) {
            if (seenCache.containsKey(key)) {
                true
            } else {
                seenCache[key] = System.currentTimeMillis()
                false
            }
        }

        if (isDuplicate) {
            countDuplicatesDropped.incrementAndGet()
            Log.i(TAG, "RELAY DROP DUPLICATE: source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return ContextRelayEvaluation(
                decision = ContextRelayDecision.DROP_DUPLICATE,
                dropReason = "Duplicate packet already seen",
                wirePayloadBytes = packet.payload.size
            )
        }

        // 3. Terminal destination check
        if (packet.destinationDeviceId == localDeviceId) {
            Log.i(TAG, "TERMINAL DESTINATION: packet for local device #$localDeviceId")
            return ContextRelayEvaluation(
                decision = ContextRelayDecision.DELIVER_LOCAL_ONLY,
                wirePayloadBytes = packet.payload.size
            )
        }

        // 4. Relay enabled check
        if (!isRelayEnabled) {
            return ContextRelayEvaluation(
                decision = ContextRelayDecision.DELIVER_LOCAL_ONLY,
                wirePayloadBytes = packet.payload.size
            )
        }

        // 5. TTL hop-limit check
        val nextTtl = (packet.ttl - 1).toByte()
        if (nextTtl <= 0) {
            countTtlExpired.incrementAndGet()
            Log.i(TAG, "RELAY DROP TTL=0: source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return ContextRelayEvaluation(
                decision = ContextRelayDecision.DROP_TTL_EXPIRED,
                dropReason = "Hop limit reached (TTL decremented to 0)",
                wirePayloadBytes = packet.payload.size
            )
        }

        // 6. Forwarding: preserve exact wire payload (never expand CONTEXT_DELTA or SEMANTIC_BASE)
        val forwardedFlags = (packet.flags.toInt() or Packet.FLAG_FORWARDED).toByte()
        val forwardedPacket = packet.copy(
            ttl = nextTtl,
            flags = forwardedFlags
        )

        // Track representation stats
        if (ContextDelta.isContextDeltaPayload(packet.payload)) {
            countDeltasForwarded.incrementAndGet()
        } else if (SemanticBase.deserialize(packet.payload) != null) {
            countBasesForwarded.incrementAndGet()
        } else {
            countOtherForwarded.incrementAndGet()
        }

        val isBroadcast = (packet.destinationDeviceId == Packet.BROADCAST_ID)
        val decision = if (isBroadcast) {
            ContextRelayDecision.FORWARD_AND_DELIVER
        } else {
            ContextRelayDecision.FORWARD_ONLY
        }

        Log.i(
            TAG,
            "RELAY FORWARD: dest=${packet.destinationDeviceId} seq=${packet.sequenceNumber} ttl=$nextTtl payloadBytes=${packet.payload.size}"
        )

        return ContextRelayEvaluation(
            decision = decision,
            forwardedPacket = forwardedPacket,
            isPayloadPreserved = forwardedPacket.payload.contentEquals(packet.payload),
            wirePayloadBytes = forwardedPacket.payload.size
        )
    }

    /**
     * Evaluates a received [ContextDelta] at the destination node, executing Cases A–F.
     */
    fun processDestinationDelta(
        packet: Packet,
        payload: ByteArray,
        currentTime: Long = System.currentTimeMillis()
    ): DestinationReconstructionResult? {
        val decoded = ContextDelta.deserialize(payload) ?: return null
        val delta = decoded.delta
        val enh = decoded.enhancement

        val enhBytes = if (enh != null) enh.serialize().size else 0
        val baseBytes = payload.size - enhBytes
        val schemaVer = delta.schemaVersion.toInt()
        val enhReceived = (enh != null)

        val activeContext = SharedContextStore.get(delta.contextId, currentTime)

        // Case B & C: Missing or Expired context
        if (activeContext == null) {
            val deltaSummary = delta.toSummaryString(null)
            val fallbackText = buildString {
                append("[DELTA v${delta.version} CTX #${delta.contextId}] ")
                append(deltaSummary)
                if (enh?.text != null) {
                    append(" • \"${enh.text}\"")
                }
            }
            return DestinationReconstructionResult(
                status = "FALLBACK",
                forwardingAction = "FALLBACK",
                isFallback = true,
                displayText = fallbackText,
                ttsSpeechText = "Update for context ${delta.contextId}: $deltaSummary",
                semanticSummary = "DELTA #${delta.contextId} v${delta.version}",
                contextId = delta.contextId,
                contextVersion = delta.version,
                confidence = null,
                deltaSummary = deltaSummary,
                basePayloadBytes = baseBytes,
                enhPayloadBytes = enhBytes,
                enhancementReceived = enhReceived,
                schemaVersion = schemaVer,
                updatedContext = null
            )
        }

        // Active context exists: evaluate update against SharedContextStore
        val deltaSummary = delta.toSummaryString(activeContext)
        val candidateContext = delta.applyTo(activeContext, currentTime = currentTime)
        val resolution = SharedContextStore.put(candidateContext, currentTime = currentTime)

        return when (resolution) {
            ConflictResolutionResult.APPLIED -> {
                // Case A: Matching context successfully updated
                val baseCmd = candidateContext.toSemanticCommand()
                val baseDisplay = baseCmd.toDisplayString()
                val displayText = if (enh?.text != null) "$baseDisplay • \"${enh.text}\"" else baseDisplay
                val ttsText = baseCmd.toTtsText(packet.language)

                DestinationReconstructionResult(
                    status = "RECONSTRUCTED",
                    forwardingAction = "RECONSTRUCTED",
                    isFallback = false,
                    displayText = displayText,
                    ttsSpeechText = ttsText,
                    semanticSummary = baseCmd.toBadgeString(),
                    contextId = delta.contextId,
                    contextVersion = candidateContext.version,
                    confidence = candidateContext.confidence,
                    deltaSummary = deltaSummary,
                    basePayloadBytes = baseBytes,
                    enhPayloadBytes = enhBytes,
                    enhancementReceived = enhReceived,
                    schemaVersion = schemaVer,
                    updatedContext = candidateContext
                )
            }

            ConflictResolutionResult.APPLIED_IDEMPOTENT -> {
                // Case E: Duplicate identical delta
                val baseCmd = activeContext.toSemanticCommand()
                val baseDisplay = baseCmd.toDisplayString()
                val displayText = if (enh?.text != null) "$baseDisplay • \"${enh.text}\"" else baseDisplay

                DestinationReconstructionResult(
                    status = "DUPLICATE SUPPRESSED",
                    forwardingAction = "DUPLICATE SUPPRESSED",
                    isFallback = false,
                    displayText = displayText,
                    ttsSpeechText = "", // Suppress redundant TTS audio
                    semanticSummary = baseCmd.toBadgeString(),
                    contextId = delta.contextId,
                    contextVersion = activeContext.version,
                    confidence = activeContext.confidence,
                    deltaSummary = deltaSummary,
                    basePayloadBytes = baseBytes,
                    enhPayloadBytes = enhBytes,
                    enhancementReceived = enhReceived,
                    schemaVersion = schemaVer,
                    updatedContext = activeContext
                )
            }

            ConflictResolutionResult.REJECTED_STALE -> {
                // Case D: Stale update rejected
                val displayText = "[STALE DELTA v${delta.version} REJECTED] Current: v${activeContext.version} • ${activeContext.toDisplayString()}"

                DestinationReconstructionResult(
                    status = "REJECTED STALE",
                    forwardingAction = "REJECTED STALE",
                    isFallback = true,
                    displayText = displayText,
                    ttsSpeechText = "Stale tactical update rejected for context ${delta.contextId}",
                    semanticSummary = "STALE DELTA v${delta.version}",
                    contextId = delta.contextId,
                    contextVersion = delta.version,
                    confidence = activeContext.confidence,
                    deltaSummary = deltaSummary,
                    basePayloadBytes = baseBytes,
                    enhPayloadBytes = enhBytes,
                    enhancementReceived = enhReceived,
                    schemaVersion = schemaVer,
                    updatedContext = activeContext
                )
            }

            ConflictResolutionResult.REJECTED_CONFLICT -> {
                // Case F: Conflicting update rejected
                val displayText = "[CONFLICT DELTA v${delta.version} REJECTED] Retained: ${activeContext.toDisplayString()}"

                DestinationReconstructionResult(
                    status = "REJECTED CONFLICT",
                    forwardingAction = "REJECTED CONFLICT",
                    isFallback = true,
                    displayText = displayText,
                    ttsSpeechText = "Conflicting tactical update rejected for context ${delta.contextId}",
                    semanticSummary = "CONFLICT DELTA v${delta.version}",
                    contextId = delta.contextId,
                    contextVersion = delta.version,
                    confidence = activeContext.confidence,
                    deltaSummary = deltaSummary,
                    basePayloadBytes = baseBytes,
                    enhPayloadBytes = enhBytes,
                    enhancementReceived = enhReceived,
                    schemaVersion = schemaVer,
                    updatedContext = activeContext
                )
            }

            ConflictResolutionResult.REJECTED_LOW_CONFIDENCE -> {
                val displayText = "[LOW CONFIDENCE DELTA v${delta.version} REJECTED] Retained: ${activeContext.toDisplayString()}"

                DestinationReconstructionResult(
                    status = "REJECTED LOW CONFIDENCE",
                    forwardingAction = "REJECTED LOW CONFIDENCE",
                    isFallback = true,
                    displayText = displayText,
                    ttsSpeechText = "Low confidence update rejected",
                    semanticSummary = "LOW CONFIDENCE DELTA",
                    contextId = delta.contextId,
                    contextVersion = delta.version,
                    confidence = candidateContext.confidence,
                    deltaSummary = deltaSummary,
                    basePayloadBytes = baseBytes,
                    enhPayloadBytes = enhBytes,
                    enhancementReceived = enhReceived,
                    schemaVersion = schemaVer,
                    updatedContext = activeContext
                )
            }

            ConflictResolutionResult.REJECTED_EXPIRED -> {
                // Case C fallback
                val displayText = "[EXPIRED DELTA v${delta.version} REJECTED]"
                DestinationReconstructionResult(
                    status = "FALLBACK",
                    forwardingAction = "FALLBACK",
                    isFallback = true,
                    displayText = displayText,
                    ttsSpeechText = "Expired update rejected",
                    semanticSummary = "EXPIRED DELTA",
                    contextId = delta.contextId,
                    contextVersion = delta.version,
                    confidence = null,
                    deltaSummary = deltaSummary,
                    basePayloadBytes = baseBytes,
                    enhPayloadBytes = enhBytes,
                    enhancementReceived = enhReceived,
                    schemaVersion = schemaVer,
                    updatedContext = null
                )
            }
        }
    }

    fun getStats(): Map<String, Long> = mapOf(
        "deltasForwarded" to countDeltasForwarded.get(),
        "basesForwarded" to countBasesForwarded.get(),
        "otherForwarded" to countOtherForwarded.get(),
        "duplicatesDropped" to countDuplicatesDropped.get(),
        "ttlExpired" to countTtlExpired.get(),
        "selfDropped" to countSelfDropped.get()
    )

    fun clearCache() {
        synchronized(seenCache) {
            seenCache.clear()
        }
    }

    fun resetStats() {
        countDeltasForwarded.set(0)
        countBasesForwarded.set(0)
        countOtherForwarded.set(0)
        countDuplicatesDropped.set(0)
        countTtlExpired.set(0)
        countSelfDropped.set(0)
    }
}
