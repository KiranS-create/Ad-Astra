package org.sih.itantra.core.benchmark.channel

import java.util.Random

/**
 * Deterministic packet loss model for simulated network impairment.
 */
interface LossModel {
    /**
     * Evaluates whether the packet at [packetIndex] should be dropped.
     *
     * @param packetIndex 0-based index of transmitted packet in current session.
     * @param byteCount Size of the packet in bytes.
     * @return True if packet is lost in the channel, false if successfully received.
     */
    fun shouldDrop(packetIndex: Int, byteCount: Int): Boolean
}

/**
 * Independent Bernoulli packet loss model using seeded pseudo-randomness.
 */
class BernoulliLossModel(
    val lossRate: Double,
    private val random: Random
) : LossModel {
    init {
        require(lossRate in 0.0..1.0) { "Loss rate must be in range [0.0, 1.0]" }
    }

    override fun shouldDrop(packetIndex: Int, byteCount: Int): Boolean {
        if (lossRate <= 0.0) return false
        if (lossRate >= 1.0) return true
        return random.nextDouble() < lossRate
    }
}

/**
 * Deterministic burst-loss model dropping [burstLength] consecutive packets
 * starting at specified intervals or random trigger points.
 */
class BurstLossModel(
    val burstLength: Int,
    val triggerInterval: Int = 10,
    private val random: Random? = null
) : LossModel {
    private var currentBurstRemaining = 0
    private var packetsSinceLastBurst = 0

    override fun shouldDrop(packetIndex: Int, byteCount: Int): Boolean {
        if (burstLength <= 0) return false

        if (currentBurstRemaining > 0) {
            currentBurstRemaining--
            return true
        }

        packetsSinceLastBurst++
        val shouldTriggerBurst = if (random != null) {
            packetsSinceLastBurst > triggerInterval && random.nextDouble() < 0.4
        } else {
            packetsSinceLastBurst > triggerInterval
        }

        if (shouldTriggerBurst) {
            packetsSinceLastBurst = 0
            currentBurstRemaining = burstLength - 1 // this one counts as first
            return true
        }

        return false
    }

    fun reset() {
        currentBurstRemaining = 0
        packetsSinceLastBurst = 0
    }
}
