package org.sih.itantra.core.emergency

import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageRecord

/**
 * Visual presentation state for the emergency banner in an individual chat thread.
 */
enum class EmergencyBannerType {
    NONE,
    ACTIVE,
    HISTORICAL
}

data class EmergencyChannelContext(
    val bannerType: EmergencyBannerType,
    val activeEmergencyCount: Int,
    val totalEmergencyCount: Int,
    val latestEmergencyRecord: MessageRecord?,
    val isEmergencyActive: Boolean
)

/**
 * Pure, deterministic mapper that derives emergency channel status and distinguishes
 * active emergency broadcasts from historical records without altering underlying data.
 */
object EmergencyUiMapper {

    /**
     * Active distress threshold window: 15 minutes (900,000 ms).
     * Any distress message newer than this window is considered active.
     */
    const val ACTIVE_WINDOW_MS: Long = 15 * 60 * 1000L

    fun map(
        messages: List<MessageRecord>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): EmergencyChannelContext {
        val emergencyMessages = messages.filter {
            it.priority == MessagePriority.DISTRESS || it.priority == MessagePriority.ALERT
        }

        if (emergencyMessages.isEmpty()) {
            return EmergencyChannelContext(
                bannerType = EmergencyBannerType.NONE,
                activeEmergencyCount = 0,
                totalEmergencyCount = 0,
                latestEmergencyRecord = null,
                isEmergencyActive = false
            )
        }

        val activeEmergencies = emergencyMessages.filter {
            (currentTimeMs - it.timestamp) in 0..ACTIVE_WINDOW_MS
        }

        val latestRecord = emergencyMessages.maxByOrNull { it.timestamp }
        val hasActive = activeEmergencies.isNotEmpty()

        val bannerType = if (hasActive) {
            EmergencyBannerType.ACTIVE
        } else {
            EmergencyBannerType.HISTORICAL
        }

        return EmergencyChannelContext(
            bannerType = bannerType,
            activeEmergencyCount = activeEmergencies.size,
            totalEmergencyCount = emergencyMessages.size,
            latestEmergencyRecord = latestRecord,
            isEmergencyActive = hasActive
        )
    }

    /**
     * Determines whether a specific message record represents an active distress signal.
     */
    fun isMessageActiveDistress(
        record: MessageRecord,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (record.priority != MessagePriority.DISTRESS && record.priority != MessagePriority.ALERT) {
            return false
        }
        return (currentTimeMs - record.timestamp) in 0..ACTIVE_WINDOW_MS
    }

    /**
     * Determines whether a specific message record represents a historical distress signal.
     */
    fun isMessageHistoricalDistress(
        record: MessageRecord,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (record.priority != MessagePriority.DISTRESS && record.priority != MessagePriority.ALERT) {
            return false
        }
        return (currentTimeMs - record.timestamp) > ACTIVE_WINDOW_MS
    }
}
