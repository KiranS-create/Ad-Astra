package org.sih.itantra.core.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline conversation repository for iTantra.
 *
 * Projects raw MessageRecord items and MeshTopology snapshots into familiar,
 * tactical conversation summaries.
 *
 * Features:
 * - Unread message counting with Android SharedPreferences persistence across lifecycle.
 * - Dynamic route reachability (Connected Direct, Relayed, Recently Heard, DTN Stored, Disconnected).
 * - Radio-aware delivery status (QUEUED, TRANSMITTING, RELAYED, RECEIVED, ACK, DTN STORED, FAILED).
 * - In-memory instant offline search filter.
 */
class ChatRepository(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences("itantra_chat_unread_prefs", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    // In-memory cache of last-read timestamp per conversation ID
    private val lastReadTimestamps = ConcurrentHashMap<String, Long>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var currentTopology: MeshTopologySnapshot = MeshTopologySnapshot()

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    private val _filteredConversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val filteredConversations: StateFlow<List<ConversationSummary>> = _filteredConversations.asStateFlow()

    private val historyListener = { refresh() }

    init {
        // Hydrate persisted read timestamps if preferences are available
        prefs?.all?.forEach { (key, value) ->
            if (key.startsWith("last_read_") && value is Long) {
                val peerId = key.removePrefix("last_read_")
                lastReadTimestamps[peerId] = value
            }
        }
        MessageHistoryStore.addListener(historyListener)
        refresh()

        scope.launch {
            MessageHistoryStore.historyFlow.collect {
                refresh()
            }
        }
    }

    fun updateTopology(snapshot: MeshTopologySnapshot) {
        currentTopology = snapshot
        refresh()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilter()
    }

    private fun applyFilter() {
        val q = _searchQuery.value.trim()
        val list = _conversations.value
        _filteredConversations.value = if (q.isBlank()) {
            list
        } else {
            list.filter { c ->
                c.displayName.contains(q, ignoreCase = true) ||
                c.lastMessageText.contains(q, ignoreCase = true) ||
                (c.peerNodeId != null && c.peerNodeId.toString().contains(q))
            }
        }
    }

    fun refresh() {
        val records = MessageHistoryStore.getRecords()
        _conversations.value = aggregateConversations(records, currentTopology)
        applyFilter()
    }

    /**
     * Clears unread status for a given conversation by stamping the current timestamp.
     */
    fun markConversationAsRead(peerId: String) {
        val now = System.currentTimeMillis()
        lastReadTimestamps[peerId] = now
        try {
            prefs?.edit()?.putLong("last_read_$peerId", now)?.apply()
        } catch (_: Throwable) {
            // Safe fallback for test environments without Android Context
        }
        refresh()
    }

    private fun aggregateConversations(
        records: List<MessageRecord>,
        topology: MeshTopologySnapshot
    ): List<ConversationSummary> {
        if (records.isEmpty()) return emptyList()

        // Group records by conversation peer
        val groups = records.groupBy { record ->
            normalizePeerId(record.peer)
        }

        val summaries = groups.map { (peerId, peerRecords) ->
            // Most recent message in this conversation
            val sorted = peerRecords.sortedByDescending { it.timestamp }
            val latest = sorted.first()

            val peerNodeId = extractNodeId(peerId)
            val displayName = formatDisplayName(peerId, peerNodeId)

            // Calculate unread count (incoming messages since last read timestamp)
            val lastRead = lastReadTimestamps[peerId] ?: 0L
            val unreadCount = peerRecords.count { r ->
                r.direction == MessageDirection.RECEIVED && r.timestamp > lastRead
            }

            // Derive route reachability from topology and message telemetry
            val (routeState, hopCount) = deriveRouteState(peerNodeId, latest, topology)

            // Derive radio-aware delivery status
            val deliveryStatus = deriveDeliveryStatus(latest)

            val isEmergency = sorted.any {
                (it.priority == MessagePriority.DISTRESS || it.priority == MessagePriority.ALERT) &&
                (System.currentTimeMillis() - it.timestamp < 300_000L) // active within 5 mins
            } || latest.priority == MessagePriority.DISTRESS || latest.priority == MessagePriority.ALERT

            ConversationSummary(
                id = peerId,
                displayName = displayName,
                peerNodeId = peerNodeId,
                lastMessageText = latest.text,
                lastTimestamp = latest.timestamp,
                unreadCount = unreadCount,
                language = latest.language,
                priority = latest.priority,
                routeState = routeState,
                hopCount = hopCount,
                deliveryStatus = deliveryStatus,
                transport = if (latest.direction == MessageDirection.SENT) "Wi-Fi Broadcast" else "Mesh Receiver",
                isEmergency = isEmergency,
                isAuthenticated = latest.isSecure,
                isEncrypted = latest.isSecure
            )
        }

        return summaries.sortedByDescending { it.lastTimestamp }
    }

    /**
     * Retrieves all messages for a specific peer, sorted chronologically (oldest to newest).
     */
    fun getThreadMessages(peerId: String): List<MessageRecord> {
        val normTarget = normalizePeerId(peerId)
        val allRecords = MessageHistoryStore.getRecords()
        return allRecords.filter { record ->
            normalizePeerId(record.peer) == normTarget
        }.sortedBy { it.timestamp }
    }

    /**
     * Derives current tactical header and route telemetry for a 1-to-1 conversation view.
     */
    fun getHeaderState(peerId: String): IndividualChatHeaderState {
        val normId = normalizePeerId(peerId)
        val peerNodeId = extractNodeId(normId)
        val displayName = formatDisplayName(normId, peerNodeId)
        val thread = getThreadMessages(normId)
        val latest = thread.lastOrNull()

        val (routeState, hopCount) = if (latest != null) {
            deriveRouteState(peerNodeId, latest, currentTopology)
        } else {
            val active = currentTopology.nodes.firstOrNull { it.nodeId == peerNodeId }
            if (active != null && active.isReachable) {
                (if (active.hopCount <= 1) ChatRouteState.CONNECTED_DIRECT else ChatRouteState.CONNECTED_RELAYED) to active.hopCount
            } else {
                ChatRouteState.DISCONNECTED to 0
            }
        }

        val activeNode = currentTopology.nodes.firstOrNull { it.nodeId == peerNodeId }
        val activeRoute = currentTopology.routes.firstOrNull { it.destinationNodeId == peerNodeId }
        val relayId = activeRoute?.nextHopNodeId
        val isEmergency = thread.any {
            it.priority == MessagePriority.DISTRESS || it.priority == MessagePriority.ALERT
        }

        return IndividualChatHeaderState(
            peerId = normId,
            displayName = displayName,
            peerNodeId = peerNodeId,
            routeState = routeState,
            hopCount = hopCount,
            transport = activeNode?.transport ?: (latest?.let { if (it.direction == MessageDirection.SENT) "Wi-Fi Broadcast" else "Mesh Receiver" } ?: "Wi-Fi"),
            relayNodeId = relayId,
            isEmergency = isEmergency,
            lastHeardMs = latest?.timestamp
        )
    }

    fun normalizePeerId(peer: String): String {
        val trimmed = peer.trim()
        val nodeId = extractNodeId(trimmed)
        return if (nodeId != null) "Node #$nodeId" else trimmed
    }

    fun extractNodeId(peer: String): Int? {
        val regex = Regex("""(?:Node\s*#?|^#?)(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(peer.trim())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun formatDisplayName(peer: String, peerNodeId: Int?): String {
        return when {
            peer.equals("Broadcast", ignoreCase = true) -> "TACTICAL BROADCAST"
            peer.equals("Emergency Broadcast", ignoreCase = true) -> "DISTRESS FREQUENCY"
            peerNodeId != null -> "NODE #$peerNodeId"
            else -> peer.uppercase()
        }
    }

    private fun deriveRouteState(
        peerNodeId: Int?,
        latest: MessageRecord,
        topology: MeshTopologySnapshot
    ): Pair<ChatRouteState, Int> {
        val now = System.currentTimeMillis()

        if (peerNodeId != null) {
            val activeNode = topology.nodes.firstOrNull { it.nodeId == peerNodeId }
            if (activeNode != null && activeNode.isReachable) {
                return if (activeNode.hopCount <= 1) {
                    ChatRouteState.CONNECTED_DIRECT to 1
                } else {
                    ChatRouteState.CONNECTED_RELAYED to activeNode.hopCount
                }
            }
        }

        // Check if message indicates relayed hops
        if (latest.hopCount > 1 || latest.isRelayed) {
            return if (now - latest.timestamp < 180_000L) {
                ChatRouteState.CONNECTED_RELAYED to latest.hopCount.coerceAtLeast(2)
            } else {
                ChatRouteState.RECENTLY_HEARD to latest.hopCount
            }
        }

        // Check message age for recently heard
        if (now - latest.timestamp < 120_000L) {
            return ChatRouteState.CONNECTED_DIRECT to 1
        } else if (now - latest.timestamp < 600_000L) {
            return ChatRouteState.RECENTLY_HEARD to latest.hopCount
        }

        // Check if there are DTN stored items for this transfer
        if (latest.deliveryStatus == DeliveryStatus.PENDING && latest.transferId != null) {
            return ChatRouteState.DTN_STORED to latest.hopCount
        }

        return ChatRouteState.DISCONNECTED to latest.hopCount
    }

    private fun deriveDeliveryStatus(record: MessageRecord): ChatDeliveryStatus {
        return when (record.direction) {
            MessageDirection.RECEIVED -> {
                if (record.isRelayed || record.hopCount > 1) {
                    ChatDeliveryStatus.RELAYED
                } else {
                    ChatDeliveryStatus.RECEIVED
                }
            }
            MessageDirection.SENT -> {
                val qos = record.qosStatus
                when {
                    qos != null && qos.contains("QUEUED") -> ChatDeliveryStatus.QUEUED
                    record.deliveryStatus == DeliveryStatus.DELIVERED -> ChatDeliveryStatus.ACK
                    record.deliveryStatus == DeliveryStatus.TIMEOUT -> ChatDeliveryStatus.FAILED
                    record.deliveryStatus == DeliveryStatus.PENDING || record.deliveryStatus == DeliveryStatus.SENDING -> {
                        if (record.transferId != null && record.isRelayed) {
                            ChatDeliveryStatus.DTN_STORED
                        } else {
                            ChatDeliveryStatus.TRANSMITTING
                        }
                    }
                    record.isRelayed -> ChatDeliveryStatus.RELAYED
                    else -> ChatDeliveryStatus.ACK
                }
            }
        }
    }
}
