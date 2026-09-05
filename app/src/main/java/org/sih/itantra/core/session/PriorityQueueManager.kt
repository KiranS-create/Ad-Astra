package org.sih.itantra.core.session

import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.Packet
import java.util.concurrent.PriorityBlockingQueue

/**
 * Priority queue manager guaranteeing DISTRESS and ALERT messages
 * preempt ordinary transmission traffic.
 */
class PriorityQueueManager {

    private val queue = PriorityBlockingQueue<Packet>(32) { p1, p2 ->
        // Higher priority value comes first (DISTRESS=3 > ALERT=2 > IMPORTANT=1 > NORMAL=0)
        p2.priority.id.compareTo(p1.priority.id)
    }

    fun enqueue(packet: Packet) {
        queue.offer(packet)
    }

    fun poll(): Packet? {
        return queue.poll()
    }

    val size: Int
        get() = queue.size

    val hasEmergencyMessage: Boolean
        get() = queue.peek()?.priority?.isEmergency == true

    fun clear() {
        queue.clear()
    }
}
