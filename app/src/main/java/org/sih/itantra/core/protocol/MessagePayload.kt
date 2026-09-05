package org.sih.itantra.core.protocol

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority

sealed class MessagePayload {
    data class TextMessage(
        val text: String,
        val language: IndicLanguage,
        val priority: MessagePriority = MessagePriority.NORMAL
    ) : MessagePayload()

    data class AlertMessage(
        val alertText: String,
        val language: IndicLanguage,
        val isDistress: Boolean = false
    ) : MessagePayload()

    data class AckMessage(
        val acknowledgedSeqNum: Short
    ) : MessagePayload()

    data class PingMessage(
        val timestamp: Long
    ) : MessagePayload()
}
