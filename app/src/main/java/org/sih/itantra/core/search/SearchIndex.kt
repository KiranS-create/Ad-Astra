package org.sih.itantra.core.search

import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyNode
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fast, deterministic, in-memory local search index.
 *
 * Guarantees:
 * - Purely local and offline execution.
 * - Zero neural or speech model loading.
 * - Deterministic relevance scoring and ranking.
 * - Exact and token prefix matching across Latin and Indic scripts.
 * - Safe node ID normalization ("Node #477124" == "477124" == "#477124").
 * - Reactive index synchronization with MessageHistoryStore.
 * - Bounded memory footprint.
 */
class SearchIndex(
    private var contactProvider: SearchContactProvider? = null
) {
    // Indexed items keyed by unique resultId
    private val messageIndex = ConcurrentHashMap<String, SearchResult>()
    private val nodeIndex = ConcurrentHashMap<Int, SearchResult>()
    private val contactIndex = ConcurrentHashMap<Int, SearchResult>()
    private val conversationIndex = ConcurrentHashMap<String, SearchResult>()

    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()

    private val historyListener = {
        syncWithHistory()
        notifyListeners()
    }

    init {
        syncWithHistory()
        MessageHistoryStore.addListener(historyListener)
    }

    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    private fun notifyListeners() {
        changeListeners.forEach { it.invoke() }
    }

    fun setContactProvider(provider: SearchContactProvider) {
        this.contactProvider = provider
        refreshContacts()
        notifyListeners()
    }

    /**
     * Ingests active mesh topology snapshot into the node index.
     */
    fun updateTopology(snapshot: MeshTopologySnapshot) {
        snapshot.nodes.forEach { node ->
            indexTopologyNode(node)
        }
        notifyListeners()
    }

    /**
     * Synchronizes message entries from MessageHistoryStore.
     */
    fun syncWithHistory() {
        val records = MessageHistoryStore.getRecords()
        val currentIds = HashSet<String>(records.size)

        for (record in records) {
            currentIds.add(record.id)
            indexMessage(record)
        }

        // Remove deleted records if history was cleared
        val iterator = messageIndex.keys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (!currentIds.contains(key)) {
                iterator.remove()
            }
        }

        // Project conversations from history
        projectConversationsFromRecords(records)
    }

    /**
     * Indexes a single MessageRecord incrementally.
     */
    fun indexMessage(record: MessageRecord) {
        val peerNodeId = SearchQueryParser.extractNodeId(record.peer)
        val title = formatMessageTitle(record.peer, peerNodeId)
        val subtitle = buildMessageSubtitle(record, peerNodeId)
        val delivery = deriveDeliveryStatus(record)

        val result = SearchResult(
            resultId = "msg_${record.id}",
            resultType = SearchResultType.MESSAGE,
            title = title,
            subtitle = subtitle,
            snippet = record.text,
            timestamp = record.timestamp,
            nodeId = peerNodeId,
            language = record.language,
            routeState = if (record.hopCount > 1 || record.isRelayed) ChatRouteState.CONNECTED_RELAYED else ChatRouteState.CONNECTED_DIRECT,
            deliveryStatus = delivery,
            priority = record.priority,
            relevanceScore = 0,
            sourceReference = record,
            authStatus = if (record.isSecure) "AUTH ✓" else null,
            hopCount = record.hopCount
        )

        messageIndex[record.id] = result

        // If this record indicates a peer node ID, ensure a known node stub exists if not already present
        if (peerNodeId != null && !nodeIndex.containsKey(peerNodeId)) {
            nodeIndex[peerNodeId] = SearchResult(
                resultId = "node_$peerNodeId",
                resultType = SearchResultType.NODE,
                title = "NODE #$peerNodeId",
                subtitle = "Logged in message history",
                snippet = "Last message: \"${record.text.take(40)}\"",
                timestamp = record.timestamp,
                nodeId = peerNodeId,
                routeState = ChatRouteState.RECENTLY_HEARD,
                deliveryStatus = delivery,
                hopCount = record.hopCount
            )
        }
    }

    /**
     * Indexes a topology node.
     */
    fun indexTopologyNode(node: TopologyNode) {
        val routeState = when {
            node.isLocal -> ChatRouteState.CONNECTED_DIRECT
            !node.isReachable -> ChatRouteState.DISCONNECTED
            node.hopCount <= 1 -> ChatRouteState.CONNECTED_DIRECT
            else -> ChatRouteState.CONNECTED_RELAYED
        }

        val result = SearchResult(
            resultId = "node_${node.nodeId}",
            resultType = SearchResultType.NODE,
            title = node.displayName.ifBlank { "NODE #${node.nodeId}" }.uppercase(),
            subtitle = "NODE #${node.nodeId} · ${node.transport} · ${node.hopCount} HOPS",
            snippet = "State: ${node.state.label} · Last seen: ${node.lastSeen}",
            timestamp = node.lastSeenMs,
            nodeId = node.nodeId,
            routeState = routeState,
            hopCount = node.hopCount,
            sourceReference = node
        )

        nodeIndex[node.nodeId] = result
    }

    /**
     * Re-indexes contacts from contactProvider.
     */
    fun refreshContacts() {
        val provider = contactProvider ?: return
        contactIndex.clear()
        provider.getContacts().forEach { contact ->
            val result = SearchResult(
                resultId = "contact_${contact.nodeId}",
                resultType = SearchResultType.CONTACT,
                title = contact.callsign.uppercase(),
                subtitle = contact.displayName ?: "NODE #${contact.nodeId}",
                snippet = contact.notes ?: "Languages: ${contact.supportedLanguages.joinToString(", ") { it.displayName }}",
                nodeId = contact.nodeId,
                authStatus = contact.authStatus,
                sourceReference = contact,
                extraDetails = mapOf(
                    "languages" to contact.supportedLanguages.joinToString(" · ") { it.displayName },
                    "offline" to contact.isOffline.toString()
                )
            )
            contactIndex[contact.nodeId] = result
        }
    }

    private fun projectConversationsFromRecords(records: List<MessageRecord>) {
        conversationIndex.clear()
        if (records.isEmpty()) return

        val groups = records.groupBy { it.peer.trim() }
        for ((peer, peerRecords) in groups) {
            val sorted = peerRecords.sortedByDescending { it.timestamp }
            val latest = sorted.first()
            val peerNodeId = SearchQueryParser.extractNodeId(peer)
            val displayName = formatMessageTitle(peer, peerNodeId)

            val convResult = SearchResult(
                resultId = "conv_$peer",
                resultType = SearchResultType.CONVERSATION,
                title = displayName,
                subtitle = if (peerNodeId != null) "Node #$peerNodeId" else null,
                snippet = latest.text,
                timestamp = latest.timestamp,
                nodeId = peerNodeId,
                language = latest.language,
                priority = latest.priority,
                deliveryStatus = deriveDeliveryStatus(latest),
                hopCount = latest.hopCount,
                sourceReference = peer
            )
            conversationIndex[peer] = convResult
        }
    }

    /**
     * Core deterministic search query evaluation.
     */
    fun search(rawQuery: String, filterState: SearchFilterState = SearchFilterState()): List<SearchResult> {
        val parsed = SearchQueryParser.parse(rawQuery)
        if (parsed.isBlank) {
            return emptyList()
        }

        val effectiveFilterType = parsed.typeFilterHint ?: filterState.filterType

        val candidateResults = ArrayList<SearchResult>()

        // 1. Match Messages (if applicable)
        if (effectiveFilterType == SearchFilterType.ALL || effectiveFilterType == SearchFilterType.MESSAGES) {
            messageIndex.values.forEach { item ->
                val score = calculateScore(item, parsed)
                if (score > 0) {
                    candidateResults.add(item.copy(relevanceScore = score))
                }
            }
        }

        // 2. Match Nodes (if applicable)
        if (effectiveFilterType == SearchFilterType.ALL || effectiveFilterType == SearchFilterType.NODES) {
            nodeIndex.values.forEach { item ->
                val score = calculateScore(item, parsed)
                if (score > 0) {
                    candidateResults.add(item.copy(relevanceScore = score))
                }
            }
        }

        // 3. Match Contacts (if applicable)
        if (effectiveFilterType == SearchFilterType.ALL || effectiveFilterType == SearchFilterType.CONTACTS) {
            contactIndex.values.forEach { item ->
                val score = calculateScore(item, parsed)
                if (score > 0) {
                    candidateResults.add(item.copy(relevanceScore = score))
                }
            }
        }

        // 4. Match Conversations (if applicable)
        if (effectiveFilterType == SearchFilterType.ALL || effectiveFilterType == SearchFilterType.CONVERSATIONS) {
            conversationIndex.values.forEach { item ->
                val score = calculateScore(item, parsed)
                if (score > 0) {
                    candidateResults.add(item.copy(relevanceScore = score))
                }
            }
        }

        // Apply secondary metadata filters
        var filtered = candidateResults.asSequence()

        if (filterState.selectedLanguage != null) {
            filtered = filtered.filter { it.language == filterState.selectedLanguage }
        }

        if (filterState.selectedPriority != null) {
            filtered = filtered.filter { it.priority == filterState.selectedPriority }
        }

        if (filterState.selectedDeliveryStatus != null) {
            filtered = filtered.filter { it.deliveryStatus == filterState.selectedDeliveryStatus }
        }

        // Sort by deterministic relevance ranking, with recency as tiebreaker
        return filtered.sortedWith(
            compareByDescending<SearchResult> { it.relevanceScore }
                .thenByDescending { it.timestamp ?: 0L }
        ).take(100).toList()
    }

    /**
     * Deterministic scoring algorithm:
     * 1. Exact node ID match (1000)
     * 2. Exact callsign / title match (800)
     * 3. Exact phrase match in message / text (600)
     * 4. Token prefix match in title / node (400)
     * 5. Token match in message content (200 per token)
     * 6. Substring match (100)
     */
    private fun calculateScore(item: SearchResult, query: ParsedQuery): Int {
        var score = 0
        val normTitle = SearchQueryParser.normalizeText(item.title)
        val normSubtitle = item.subtitle?.let { SearchQueryParser.normalizeText(it) } ?: ""
        val normSnippet = item.snippet?.let { SearchQueryParser.normalizeText(it) } ?: ""

        // 1. Exact Node ID match
        if (query.candidateNodeId != null && item.nodeId == query.candidateNodeId) {
            score += when (item.resultType) {
                SearchResultType.NODE -> 1000
                SearchResultType.CONTACT -> 900
                SearchResultType.CONVERSATION -> 700
                SearchResultType.MESSAGE -> 350
                SearchResultType.NEARBY_DEVICE -> 800
            }
        }

        // 2. Exact Title / Callsign match
        if (normTitle == query.normalized) {
            score += when (item.resultType) {
                SearchResultType.CONTACT -> 850
                SearchResultType.NODE -> 800
                else -> 700
            }
        }

        // 3. Exact phrase match in message or snippet
        if (normSnippet.isNotEmpty() && normSnippet.contains(query.normalized)) {
            score += if (item.resultType == SearchResultType.MESSAGE) 600 else 300
        }

        // 4. Token prefix match in title or subtitle
        for (token in query.tokens) {
            if (token.isBlank()) continue

            if (normTitle.startsWith(token) || normTitle.split(" ").any { it.startsWith(token) }) {
                score += 400
            } else if (normSubtitle.isNotEmpty() && normSubtitle.split(" ").any { it.startsWith(token) }) {
                score += 300
            } else if (normSnippet.isNotEmpty() && normSnippet.contains(token)) {
                // Indic script or standard token match
                score += 200
            }
        }

        // 5. Fallback substring match in title or notes
        if (score == 0) {
            if (normTitle.contains(query.normalized) || normSubtitle.contains(query.normalized)) {
                score += 100
            }
        }

        return score
    }

    private fun formatMessageTitle(peer: String, peerNodeId: Int?): String {
        return when {
            peer.equals("Broadcast", ignoreCase = true) -> "TACTICAL BROADCAST"
            peer.equals("Emergency Broadcast", ignoreCase = true) -> "DISTRESS FREQUENCY"
            peerNodeId != null -> "NODE #$peerNodeId"
            else -> peer.uppercase()
        }
    }

    private fun buildMessageSubtitle(record: MessageRecord, peerNodeId: Int?): String {
        val directionLabel = if (record.direction == MessageDirection.SENT) "OUTGOING (TX)" else "INCOMING (RX)"
        val peerLabel = if (peerNodeId != null) "Node #$peerNodeId" else record.peer
        return "$directionLabel · $peerLabel"
    }

    private fun deriveDeliveryStatus(record: MessageRecord): ChatDeliveryStatus {
        return when (record.direction) {
            MessageDirection.RECEIVED -> {
                if (record.isRelayed || record.hopCount > 1) ChatDeliveryStatus.RELAYED else ChatDeliveryStatus.RECEIVED
            }
            MessageDirection.SENT -> {
                val qos = record.qosStatus
                when {
                    qos != null && qos.contains("QUEUED") -> ChatDeliveryStatus.QUEUED
                    record.deliveryStatus == DeliveryStatus.DELIVERED -> ChatDeliveryStatus.ACK
                    record.deliveryStatus == DeliveryStatus.TIMEOUT -> ChatDeliveryStatus.FAILED
                    record.deliveryStatus == DeliveryStatus.PENDING || record.deliveryStatus == DeliveryStatus.SENDING -> {
                        if (record.transferId != null && record.isRelayed) ChatDeliveryStatus.DTN_STORED else ChatDeliveryStatus.TRANSMITTING
                    }
                    record.isRelayed -> ChatDeliveryStatus.RELAYED
                    else -> ChatDeliveryStatus.ACK
                }
            }
        }
    }

    fun clear() {
        MessageHistoryStore.removeListener(historyListener)
        messageIndex.clear()
        nodeIndex.clear()
        contactIndex.clear()
        conversationIndex.clear()
        changeListeners.clear()
    }
}
