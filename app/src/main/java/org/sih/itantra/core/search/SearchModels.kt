package org.sih.itantra.core.search

import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.mesh.MeshTopologySnapshot

/**
 * Categorical type of an offline search match.
 */
enum class SearchResultType(val label: String) {
    MESSAGE("Message"),
    CONVERSATION("Conversation"),
    NODE("Node"),
    CONTACT("Contact"),
    NEARBY_DEVICE("Nearby Device")
}

/**
 * Filter scope for local offline search queries.
 */
enum class SearchFilterType(val label: String, val resultType: SearchResultType?) {
    ALL("All", null),
    MESSAGES("Messages", SearchResultType.MESSAGE),
    NODES("Nodes", SearchResultType.NODE),
    CONTACTS("Contacts", SearchResultType.CONTACT),
    CONVERSATIONS("Chats", SearchResultType.CONVERSATION)
}

/**
 * Unified, deterministic local search result model.
 * Does not replicate large payloads; stores lightweight metadata and references.
 */
data class SearchResult(
    val resultId: String,
    val resultType: SearchResultType,
    val title: String,
    val subtitle: String? = null,
    val snippet: String? = null,
    val timestamp: Long? = null,
    val nodeId: Int? = null,
    val language: IndicLanguage? = null,
    val routeState: ChatRouteState? = null,
    val deliveryStatus: ChatDeliveryStatus? = null,
    val priority: MessagePriority? = null,
    val relevanceScore: Int = 0,
    val sourceReference: Any? = null,
    val authStatus: String? = null,
    val hopCount: Int? = null,
    val extraDetails: Map<String, String> = emptyMap()
) {
    val isEmergency: Boolean
        get() = priority == MessagePriority.DISTRESS || priority == MessagePriority.ALERT
}

/**
 * Filter criteria applied across search results.
 */
data class SearchFilterState(
    val filterType: SearchFilterType = SearchFilterType.ALL,
    val selectedLanguage: IndicLanguage? = null,
    val selectedPriority: MessagePriority? = null,
    val selectedDeliveryStatus: ChatDeliveryStatus? = null
) {
    val isFiltering: Boolean
        get() = filterType != SearchFilterType.ALL ||
                selectedLanguage != null ||
                selectedPriority != null ||
                selectedDeliveryStatus != null
}

/**
 * Lightweight contract for injecting contact data into the search index
 * without tight compile-time coupling to any specific Contacts repository implementation.
 */
fun interface SearchContactProvider {
    fun getContacts(): List<SearchContactItem>
}

/**
 * Decoupled representation of contact metadata for indexing.
 */
data class SearchContactItem(
    val nodeId: Int,
    val callsign: String,
    val displayName: String? = null,
    val supportedLanguages: List<IndicLanguage> = listOf(IndicLanguage.HINDI),
    val authStatus: String = "UNVERIFIED",
    val notes: String? = null,
    val isOffline: Boolean = true
)

/**
 * Lightweight contract for injecting mesh topology snapshots.
 */
fun interface SearchTopologyProvider {
    fun getTopologySnapshot(): MeshTopologySnapshot
}
