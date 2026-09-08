package org.sih.itantra.core.contact

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Offline tactical contact repository for iTantra.
 *
 * Persists verified peer identities locally in SharedPreferences and dynamically projects
 * live network, routing, and message history telemetry onto them.
 *
 * Designed to operate 100% offline with zero external cloud dependencies.
 */
class ContactRepository(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    // In-memory store of persistent identities keyed by nodeId
    private val identityStore = ConcurrentHashMap<Int, ContactIdentity>()

    // Current live topology snapshot
    private var currentTopology: MeshTopologySnapshot = MeshTopologySnapshot()

    // Reactive list of all projected tactical contacts
    private val _contacts = MutableStateFlow<List<TacticalContact>>(emptyList())
    val contacts: StateFlow<List<TacticalContact>> = _contacts.asStateFlow()

    // Listeners for UI notification
    private val historyListener = { refresh() }

    init {
        loadPersistedIdentities()
        MessageHistoryStore.addListener(historyListener)
        refresh()

        scope.launch {
            MessageHistoryStore.historyFlow.collect {
                refresh()
            }
        }
    }

    /**
     * Updates the current mesh topology and refreshes dynamic network states.
     */
    fun updateTopology(snapshot: MeshTopologySnapshot) {
        currentTopology = snapshot
        refresh()
    }

    /**
     * Adds a new tactical contact identity.
     * @return true if successfully added, false if a contact with the same nodeId already exists.
     */
    fun addContact(identity: ContactIdentity): Boolean {
        if (identityStore.containsKey(identity.nodeId)) {
            return false
        }
        identityStore[identity.nodeId] = identity
        persistIdentity(identity)
        refresh()
        return true
    }

    /**
     * Updates an existing contact identity.
     * @return true if updated, false if not found.
     */
    fun updateContact(identity: ContactIdentity): Boolean {
        if (!identityStore.containsKey(identity.nodeId)) {
            return false
        }
        identityStore[identity.nodeId] = identity
        persistIdentity(identity)
        refresh()
        return true
    }

    /**
     * Deletes a contact identity by node ID.
     * @return true if removed, false if not found.
     */
    fun deleteContact(nodeId: Int): Boolean {
        val removed = identityStore.remove(nodeId) != null
        if (removed) {
            removePersistedIdentity(nodeId)
            refresh()
        }
        return removed
    }

    /**
     * Retrieves a contact by node ID.
     */
    fun getContact(nodeId: Int): TacticalContact? {
        val identity = identityStore[nodeId] ?: return null
        return projectContact(identity, currentTopology, MessageHistoryStore.getRecords())
    }

    /**
     * Retrieves a contact by exact or case-insensitive callsign.
     */
    fun getContactByCallsign(callsign: String): TacticalContact? {
        val trimmed = callsign.trim()
        val identity = identityStore.values.firstOrNull {
            it.callsign.equals(trimmed, ignoreCase = true)
        } ?: return null
        return projectContact(identity, currentTopology, MessageHistoryStore.getRecords())
    }

    /**
     * Searches and filters contacts by search query and optional language filter.
     */
    fun searchContacts(
        query: String = "",
        languageFilter: IndicLanguage? = null
    ): List<TacticalContact> {
        val q = query.trim()
        val all = _contacts.value

        return all.filter { contact ->
            val matchesQuery = if (q.isBlank()) {
                true
            } else {
                contact.callsign.contains(q, ignoreCase = true) ||
                contact.displayName.contains(q, ignoreCase = true) ||
                contact.nodeId.toString().contains(q) ||
                contact.supportedLanguages.any { it.displayName.contains(q, ignoreCase = true) || it.isoCode.equals(q, ignoreCase = true) }
            }

            val matchesLang = if (languageFilter == null) {
                true
            } else {
                contact.supportedLanguages.contains(languageFilter)
            }

            matchesQuery && matchesLang
        }
    }

    /**
     * Refreshes the projected contact list from persistent identities, current topology,
     * and message history.
     */
    fun refresh() {
        val records = MessageHistoryStore.getRecords()
        val projected = identityStore.values.map { identity ->
            projectContact(identity, currentTopology, records)
        }.sortedWith(
            compareBy<TacticalContact> { it.isOffline }
                .thenBy { it.networkState.hopCount }
                .thenBy { it.callsign }
        )
        _contacts.value = projected
    }

    /**
     * Projects live network and history state onto a static contact identity.
     */
    private fun projectContact(
        identity: ContactIdentity,
        topology: MeshTopologySnapshot,
        records: List<MessageRecord>
    ): TacticalContact {
        val now = System.currentTimeMillis()
        val peerNodeId = identity.nodeId

        // 1. Check live topology nodes first
        val activeNode = topology.nodes.firstOrNull { it.nodeId == peerNodeId }
        val activeRoute = topology.routes.firstOrNull { it.destinationNodeId == peerNodeId }
        val relayId = activeRoute?.nextHopNodeId

        if (activeNode != null && activeNode.isReachable) {
            val routeState = if (activeNode.hopCount <= 1) {
                ChatRouteState.CONNECTED_DIRECT
            } else {
                ChatRouteState.CONNECTED_RELAYED
            }
            val formattedTime = formatTime(activeNode.lastSeenMs, now)

            return TacticalContact(
                identity = identity,
                networkState = ContactNetworkState(
                    routeState = routeState,
                    hopCount = activeNode.hopCount.coerceAtLeast(1),
                    transport = activeNode.transport.takeIf { it.isNotBlank() } ?: "Wi-Fi",
                    lastHeardMs = activeNode.lastSeenMs,
                    lastHeardFormatted = formattedTime,
                    isReachable = true,
                    relayNodeId = relayId
                )
            )
        }

        // 2. Fall back to message history for last-heard telemetry
        val peerMessages = records.filter { r ->
            extractNodeId(r.peer) == peerNodeId
        }.sortedByDescending { it.timestamp }

        val latestMessage = peerMessages.firstOrNull()

        if (latestMessage != null) {
            val diffMs = now - latestMessage.timestamp
            val (routeState, hopCount) = when {
                diffMs < 120_000L -> {
                    (if (latestMessage.hopCount > 1 || latestMessage.isRelayed) ChatRouteState.CONNECTED_RELAYED else ChatRouteState.CONNECTED_DIRECT) to latestMessage.hopCount.coerceAtLeast(1)
                }
                diffMs < 600_000L -> {
                    ChatRouteState.RECENTLY_HEARD to latestMessage.hopCount.coerceAtLeast(1)
                }
                else -> {
                    ChatRouteState.DISCONNECTED to latestMessage.hopCount
                }
            }

            val transport = if (latestMessage.direction == MessageDirection.SENT) "Wi-Fi Broadcast" else "Mesh Receiver"
            val formattedTime = formatTime(latestMessage.timestamp, now)

            return TacticalContact(
                identity = identity,
                networkState = ContactNetworkState(
                    routeState = routeState,
                    hopCount = hopCount,
                    transport = transport,
                    lastHeardMs = latestMessage.timestamp,
                    lastHeardFormatted = formattedTime,
                    isReachable = routeState == ChatRouteState.CONNECTED_DIRECT || routeState == ChatRouteState.CONNECTED_RELAYED,
                    relayNodeId = relayId
                )
            )
        }

        // 3. Offline / disconnected peer
        return TacticalContact(
            identity = identity,
            networkState = ContactNetworkState(
                routeState = ChatRouteState.DISCONNECTED,
                hopCount = 0,
                transport = "None",
                lastHeardMs = null,
                lastHeardFormatted = "OFFLINE",
                isReachable = false,
                relayNodeId = null
            )
        )
    }

    private fun formatTime(timestampMs: Long, now: Long): String {
        val diffSec = (now - timestampMs) / 1000
        return when {
            diffSec < 5 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            else -> "${diffSec / 3600}h ago"
        }
    }

    private fun extractNodeId(peer: String): Int? {
        val regex = Regex("""(?:Node\s*#?|^#?)(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(peer.trim())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun persistIdentity(identity: ContactIdentity) {
        try {
            val key = "$KEY_PREFIX_CONTACT${identity.nodeId}"
            val serialized = serializeIdentity(identity)
            prefs?.edit()?.putString(key, serialized)?.apply()
        } catch (_: Throwable) {
            // In-memory fallback
        }
    }

    private fun removePersistedIdentity(nodeId: Int) {
        try {
            val key = "$KEY_PREFIX_CONTACT$nodeId"
            prefs?.edit()?.remove(key)?.apply()
        } catch (_: Throwable) {
            // In-memory fallback
        }
    }

    private fun loadPersistedIdentities() {
        try {
            val all = prefs?.all ?: emptyMap()
            var count = 0
            all.forEach { (key, value) ->
                if (key.startsWith(KEY_PREFIX_CONTACT) && value is String) {
                    val identity = deserializeIdentity(value)
                    if (identity != null) {
                        identityStore[identity.nodeId] = identity
                        count++
                    }
                }
            }

            // Seed default tactical peers if completely empty
            if (count == 0) {
                seedInitialTacticalNodes()
            }
        } catch (_: Throwable) {
            seedInitialTacticalNodes()
        }
    }

    /**
     * Seeds default tactical contacts for initial testing and demonstration.
     */
    fun seedInitialTacticalNodes() {
        val defaults = listOf(
            ContactIdentity(
                nodeId = 209070,
                callsign = "NODE ALPHA",
                displayName = "Squad Alpha Lead",
                supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
                notes = "Forward Scout Unit",
                authStatus = ContactAuthStatus.AUTHENTICATED
            ),
            ContactIdentity(
                nodeId = 477124,
                callsign = "NODE BRAVO",
                displayName = "Recon Patrol",
                supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI),
                notes = "Tactical Relay Point",
                authStatus = ContactAuthStatus.AUTHENTICATED
            ),
            ContactIdentity(
                nodeId = 312900,
                callsign = "COMMAND BASE",
                displayName = "HQ Tactical Control",
                supportedLanguages = listOf(IndicLanguage.ENGLISH),
                notes = "Central Command Node",
                authStatus = ContactAuthStatus.UNVERIFIED
            )
        )
        defaults.forEach { addContact(it) }
    }

    /**
     * Clears all contacts from memory and persistence.
     */
    fun clearAll() {
        identityStore.clear()
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (_: Throwable) {}
        refresh()
    }

    companion object {
        private const val PREFS_NAME = "itantra_tactical_contacts_prefs"
        private const val KEY_PREFIX_CONTACT = "contact_"

        /**
         * Robust, dependency-free delimited string serialization:
         * nodeId|callsign|displayName|langs(iso1,iso2)|notes|authStatus|addedTimestamp
         */
        fun serializeIdentity(identity: ContactIdentity): String {
            val langs = identity.supportedLanguages.joinToString(",") { it.isoCode }
            val disp = identity.displayName.orEmpty()
            val notes = identity.notes.orEmpty()
            return "${identity.nodeId}|${identity.callsign}|$disp|$langs|$notes|${identity.authStatus.name}|${identity.addedTimestamp}"
        }

        fun deserializeIdentity(raw: String): ContactIdentity? {
            val parts = raw.split("|")
            if (parts.size < 7) return null
            val nodeId = parts[0].toIntOrNull() ?: return null
            val callsign = parts[1]
            val disp = parts[2].takeIf { it.isNotBlank() }
            val langs = parts[3].split(",")
                .filter { it.isNotBlank() }
                .map { IndicLanguage.fromIsoCode(it) }
                .takeIf { it.isNotEmpty() } ?: listOf(IndicLanguage.HINDI)
            val notes = parts[4].takeIf { it.isNotBlank() }
            val authStatus = try {
                ContactAuthStatus.valueOf(parts[5])
            } catch (_: Throwable) {
                ContactAuthStatus.UNVERIFIED
            }
            val addedTs = parts[6].toLongOrNull() ?: System.currentTimeMillis()

            return ContactIdentity(
                nodeId = nodeId,
                callsign = callsign,
                displayName = disp,
                supportedLanguages = langs,
                notes = notes,
                authStatus = authStatus,
                addedTimestamp = addedTs
            )
        }
    }
}
