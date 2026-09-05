package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.session.PriorityQueueManager

class PriorityQueueTest {

    @Test
    fun testEmergencyPreemption() {
        val queue = PriorityQueueManager()

        val normalPacket = Packet(
            sequenceNumber = 1,
            timestamp = 1000L,
            sourceDeviceId = 1,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.NORMAL,
            payload = "Normal message".toByteArray()
        )

        val importantPacket = Packet(
            sequenceNumber = 2,
            timestamp = 1001L,
            sourceDeviceId = 1,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.IMPORTANT,
            payload = "Important message".toByteArray()
        )

        val alertPacket = Packet(
            sequenceNumber = 3,
            timestamp = 1002L,
            sourceDeviceId = 1,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.ALERT,
            payload = "Alert message".toByteArray()
        )

        val distressPacket = Packet(
            sequenceNumber = 4,
            timestamp = 1003L,
            sourceDeviceId = 1,
            language = IndicLanguage.HINDI,
            priority = MessagePriority.DISTRESS,
            payload = "MAYDAY DISTRESS".toByteArray()
        )

        // Add in mixed order
        queue.enqueue(normalPacket)
        queue.enqueue(alertPacket)
        queue.enqueue(importantPacket)
        queue.enqueue(distressPacket)

        assertTrue(queue.hasEmergencyMessage)

        // Verify polled order is strictly: DISTRESS > ALERT > IMPORTANT > NORMAL
        assertEquals(MessagePriority.DISTRESS, queue.poll()?.priority)
        assertEquals(MessagePriority.ALERT, queue.poll()?.priority)
        assertEquals(MessagePriority.IMPORTANT, queue.poll()?.priority)
        assertEquals(MessagePriority.NORMAL, queue.poll()?.priority)
        assertEquals(0, queue.size)
    }
}
