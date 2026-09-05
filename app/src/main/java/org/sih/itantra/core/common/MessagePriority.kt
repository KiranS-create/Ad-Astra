package org.sih.itantra.core.common

/**
 * Message transmission and playback priority hierarchy.
 * DISTRESS and ALERT bypass ordinary transmission queues and interrupt normal playback.
 */
enum class MessagePriority(
    val id: Byte,
    val label: String,
    val isEmergency: Boolean
) {
    NORMAL(0, "NORMAL", false),
    IMPORTANT(1, "IMPORTANT", false),
    ALERT(2, "ALERT", true),
    DISTRESS(3, "DISTRESS", true);

    companion object {
        fun fromId(id: Byte): MessagePriority {
            return entries.firstOrNull { it.id == id } ?: NORMAL
        }
    }
}
