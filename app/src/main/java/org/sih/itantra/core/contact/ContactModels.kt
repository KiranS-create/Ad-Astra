package org.sih.itantra.core.contact

import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.common.IndicLanguage

/**
 * Authentication and cryptographic integrity status for an iTantra contact.
 *
 * NOTE: HMAC-SHA256 authentication proves message origin authenticity and packet integrity.
 * It is explicitly labeled as AUTHENTICATED or UNVERIFIED, NEVER as "encryption".
 */
enum class ContactAuthStatus(val label: String, val badgeText: String) {
    AUTHENTICATED("Authenticated", "AUTH ✓"),
    TRUSTED("Trusted", "TRUSTED"),
    UNVERIFIED("Unverified", "UNVERIFIED")
}

/**
 * Persistent identity of an iTantra tactical node / communication peer.
 *
 * This identity persists across application restarts and device reboots,
 * and remains valid even when the node is physically offline.
 */
data class ContactIdentity(
    val nodeId: Int,
    val callsign: String,
    val displayName: String? = null,
    val supportedLanguages: List<IndicLanguage> = listOf(IndicLanguage.HINDI),
    val notes: String? = null,
    val authStatus: ContactAuthStatus = ContactAuthStatus.UNVERIFIED,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    init {
        require(nodeId > 0) { "Node ID must be a positive integer (> 0)" }
        require(callsign.isNotBlank()) { "Callsign cannot be blank" }
    }

    val formattedNodeId: String
        get() = "#$nodeId"

    val displayTitle: String
        get() = callsign.uppercase().trim()

    val displaySubtitle: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: "NODE #$nodeId"
}

/**
 * Live, transient network and routing state projected from active topology and message history.
 */
data class ContactNetworkState(
    val routeState: ChatRouteState = ChatRouteState.DISCONNECTED,
    val hopCount: Int = 0,
    val transport: String = "Wi-Fi",
    val lastHeardMs: Long? = null,
    val lastHeardFormatted: String = "OFFLINE",
    val isReachable: Boolean = false,
    val relayNodeId: Int? = null
)

/**
 * Unified tactical contact model projecting live network state onto persistent contact identity.
 */
data class TacticalContact(
    val identity: ContactIdentity,
    val networkState: ContactNetworkState = ContactNetworkState()
) {
    val nodeId: Int
        get() = identity.nodeId

    val callsign: String
        get() = identity.callsign

    val displayName: String
        get() = identity.displaySubtitle

    val supportedLanguages: List<IndicLanguage>
        get() = identity.supportedLanguages

    val authStatus: ContactAuthStatus
        get() = identity.authStatus

    val isOffline: Boolean
        get() = networkState.routeState == ChatRouteState.DISCONNECTED || !networkState.isReachable
}

/**
 * Utility functions for validating and formatting tactical contact input.
 */
object ContactValidator {

    /**
     * Validates candidate node ID string.
     * @return Pair of (isValid, errorMessage)
     */
    fun validateNodeId(raw: String): Pair<Boolean, String?> {
        val trimmed = raw.trim().removePrefix("#")
        if (trimmed.isBlank()) {
            return false to "Node ID cannot be blank"
        }
        val parsed = trimmed.toIntOrNull()
            ?: return false to "Node ID must be a valid positive integer"
        if (parsed <= 0) {
            return false to "Node ID must be greater than 0"
        }
        if (parsed > 9999999) {
            return false to "Node ID exceeds maximum supported range (9999999)"
        }
        return true to null
    }

    /**
     * Validates candidate callsign string.
     */
    fun validateCallsign(raw: String): Pair<Boolean, String?> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return false to "Callsign cannot be blank"
        }
        if (trimmed.length < 2) {
            return false to "Callsign must have at least 2 characters"
        }
        if (trimmed.length > 32) {
            return false to "Callsign cannot exceed 32 characters"
        }
        return true to null
    }
}
