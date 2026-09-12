package org.sih.itantra.core.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.GeoLocation

class EmergencyUiMapperTest {

    private fun createMessage(
        id: String,
        timestamp: Long,
        priority: MessagePriority,
        text: String = "Test Message",
        location: GeoLocation? = null
    ): MessageRecord = MessageRecord(
        id = id,
        timestamp = timestamp,
        direction = MessageDirection.RECEIVED,
        language = IndicLanguage.HINDI,
        priority = priority,
        text = text,
        peer = "Node #209071",
        packetSizeBytes = 128,
        rawAudioEquivalentBytes = 0L,
        measuredLatencyMs = 15.0,
        location = location,
        deliveryStatus = DeliveryStatus.DELIVERED
    )

    @Test
    fun testInitialStateNoEmergencyMessages() {
        val messages = listOf(
            createMessage("1", System.currentTimeMillis(), MessagePriority.NORMAL),
            createMessage("2", System.currentTimeMillis() - 1000, MessagePriority.IMPORTANT)
        )

        val context = EmergencyUiMapper.map(messages)

        assertEquals(EmergencyBannerType.NONE, context.bannerType)
        assertFalse(context.isEmergencyActive)
        assertEquals(0, context.activeEmergencyCount)
        assertEquals(0, context.totalEmergencyCount)
        assertNull(context.latestEmergencyRecord)
    }

    @Test
    fun testEmptyMessageListReturnsNone() {
        val context = EmergencyUiMapper.map(emptyList())

        assertEquals(EmergencyBannerType.NONE, context.bannerType)
        assertFalse(context.isEmergencyActive)
        assertEquals(0, context.activeEmergencyCount)
        assertEquals(0, context.totalEmergencyCount)
    }

    @Test
    fun testActiveEmergencyWithinThreshold() {
        val now = 1000000L
        val recentDistress = createMessage("d1", now - (2 * 60 * 1000L), MessagePriority.DISTRESS)
        val normalMsg = createMessage("n1", now - 5000L, MessagePriority.NORMAL)

        val context = EmergencyUiMapper.map(listOf(recentDistress, normalMsg), currentTimeMs = now)

        assertEquals(EmergencyBannerType.ACTIVE, context.bannerType)
        assertTrue(context.isEmergencyActive)
        assertEquals(1, context.activeEmergencyCount)
        assertEquals(1, context.totalEmergencyCount)
        assertEquals("d1", context.latestEmergencyRecord?.id)
    }

    @Test
    fun testHistoricalEmergencyPastThreshold() {
        val now = 2000000L
        val oldDistress = createMessage("d-old", now - (20 * 60 * 1000L), MessagePriority.DISTRESS)
        val normalMsg = createMessage("n1", now - 5000L, MessagePriority.NORMAL)

        val context = EmergencyUiMapper.map(listOf(oldDistress, normalMsg), currentTimeMs = now)

        assertEquals(EmergencyBannerType.HISTORICAL, context.bannerType)
        assertFalse(context.isEmergencyActive)
        assertEquals(0, context.activeEmergencyCount)
        assertEquals(1, context.totalEmergencyCount)
        assertEquals("d-old", context.latestEmergencyRecord?.id)
    }

    @Test
    fun testMultipleEmergencyMessagesActiveAndHistorical() {
        val now = 5000000L
        val oldDistress1 = createMessage("d1", now - (30 * 60 * 1000L), MessagePriority.DISTRESS)
        val oldDistress2 = createMessage("d2", now - (25 * 60 * 1000L), MessagePriority.ALERT)
        val recentDistress = createMessage("d3", now - (5 * 60 * 1000L), MessagePriority.DISTRESS)

        val context = EmergencyUiMapper.map(listOf(oldDistress1, oldDistress2, recentDistress), currentTimeMs = now)

        assertEquals(EmergencyBannerType.ACTIVE, context.bannerType)
        assertTrue(context.isEmergencyActive)
        assertEquals(1, context.activeEmergencyCount)
        assertEquals(3, context.totalEmergencyCount)
        assertEquals("d3", context.latestEmergencyRecord?.id)
    }

    @Test
    fun testIsMessageActiveDistressPredicate() {
        val now = 1000000L
        val recentDistress = createMessage("d1", now - (3 * 60 * 1000L), MessagePriority.DISTRESS)
        val oldDistress = createMessage("d2", now - (18 * 60 * 1000L), MessagePriority.DISTRESS)
        val normalMsg = createMessage("n1", now - (1 * 60 * 1000L), MessagePriority.NORMAL)

        assertTrue(EmergencyUiMapper.isMessageActiveDistress(recentDistress, currentTimeMs = now))
        assertFalse(EmergencyUiMapper.isMessageActiveDistress(oldDistress, currentTimeMs = now))
        assertFalse(EmergencyUiMapper.isMessageActiveDistress(normalMsg, currentTimeMs = now))
    }

    @Test
    fun testIsMessageHistoricalDistressPredicate() {
        val now = 1000000L
        val recentDistress = createMessage("d1", now - (3 * 60 * 1000L), MessagePriority.DISTRESS)
        val oldDistress = createMessage("d2", now - (18 * 60 * 1000L), MessagePriority.DISTRESS)
        val normalMsg = createMessage("n1", now - (25 * 60 * 1000L), MessagePriority.NORMAL)

        assertFalse(EmergencyUiMapper.isMessageHistoricalDistress(recentDistress, currentTimeMs = now))
        assertTrue(EmergencyUiMapper.isMessageHistoricalDistress(oldDistress, currentTimeMs = now))
        assertFalse(EmergencyUiMapper.isMessageHistoricalDistress(normalMsg, currentTimeMs = now))
    }

    @Test
    fun testAlertPriorityTreatedAsEmergency() {
        val now = 1000000L
        val alertMsg = createMessage("a1", now - 1000L, MessagePriority.ALERT)

        val context = EmergencyUiMapper.map(listOf(alertMsg), currentTimeMs = now)

        assertEquals(EmergencyBannerType.ACTIVE, context.bannerType)
        assertTrue(context.isEmergencyActive)
        assertEquals(1, context.activeEmergencyCount)
    }
}
