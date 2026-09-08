package org.sih.itantra.core.chat

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority

/**
 * Route reachability state between the local device and a conversation peer.
 * Operates purely offline without cloud presences.
 */
enum class ChatRouteState(val label: String, val badgeText: String) {
    CONNECTED_DIRECT("Direct", "DIRECT"),
    CONNECTED_RELAYED("Relayed", "RELAYED"),
    RECENTLY_HEARD("Recently Heard", "RECENT"),
    DTN_STORED("DTN Stored", "DTN"),
    DISCONNECTED("Disconnected", "OFFLINE")
}

/**
 * Radio-aware delivery status representing existing iTantra protocol states.
 */
enum class ChatDeliveryStatus(val label: String) {
    QUEUED("QUEUED"),
    TRANSMITTING("TRANSMITTING"),
    RELAYED("RELAYED"),
    RECEIVED("RECEIVED"),
    ACK("ACK"),
    DTN_STORED("DTN STORED"),
    FAILED("FAILED")
}

/**
 * Tactical conversation summary projected from MessageRecord history and Mesh topology.
 */
data class ConversationSummary(
    val id: String,
    val displayName: String,
    val peerNodeId: Int? = null,
    val lastMessageText: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val language: IndicLanguage = IndicLanguage.HINDI,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val routeState: ChatRouteState = ChatRouteState.DISCONNECTED,
    val hopCount: Int = 0,
    val deliveryStatus: ChatDeliveryStatus = ChatDeliveryStatus.RECEIVED,
    val transport: String = "Wi-Fi",
    val isEmergency: Boolean = false,
    val isAuthenticated: Boolean = true,
    val isEncrypted: Boolean = isAuthenticated
)

/**
 * Tactical header state for 1-to-1 conversation view with peer telemetry.
 */
data class IndividualChatHeaderState(
    val peerId: String,
    val displayName: String,
    val peerNodeId: Int? = null,
    val routeState: ChatRouteState = ChatRouteState.DISCONNECTED,
    val hopCount: Int = 0,
    val transport: String = "Wi-Fi Broadcast",
    val relayNodeId: Int? = null,
    val isEmergency: Boolean = false,
    val lastHeardMs: Long? = null
)
