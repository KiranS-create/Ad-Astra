package org.sih.itantra.core.pairing

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactIdentity

/**
 * Public, non-sensitive tactical node identity representation for offline QR exchange.
 *
 * Security Guarantee:
 * This payload explicitly contains PUBLIC IDENTITY DATA ONLY.
 * It NEVER encodes HMAC keys, symmetric secrets, private keys, device MAC addresses,
 * authentication credentials, or session tokens.
 */
data class QrIdentityPayload(
    val nodeId: Int,
    val callsign: String,
    val displayName: String? = null,
    val supportedLanguages: List<IndicLanguage> = listOf(IndicLanguage.HINDI),
    val version: Int = CURRENT_VERSION
) {
    init {
        require(nodeId > 0) { "Node ID must be positive (> 0)" }
        require(callsign.isNotBlank()) { "Callsign cannot be blank" }
        require(version > 0) { "Version must be positive" }
    }

    val formattedNodeId: String
        get() = "#$nodeId"

    val displayTitle: String
        get() = callsign.uppercase().trim()

    val displaySubtitle: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: "NODE #$nodeId"

    /**
     * Converts to a persistent ContactIdentity.
     * Trust status is strictly set to UNVERIFIED upon QR pairing.
     */
    fun toContactIdentity(
        notes: String? = "Added via QR pairing",
        authStatus: ContactAuthStatus = ContactAuthStatus.UNVERIFIED
    ): ContactIdentity {
        return ContactIdentity(
            nodeId = nodeId,
            callsign = callsign.uppercase().trim(),
            displayName = displayName?.takeIf { it.isNotBlank() },
            supportedLanguages = supportedLanguages.ifEmpty { listOf(IndicLanguage.HINDI) },
            notes = notes,
            authStatus = authStatus,
            addedTimestamp = System.currentTimeMillis()
        )
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val SCHEME = "ITANTRA"
        const val MAX_PAYLOAD_BYTES = 512

        /**
         * Creates a QrIdentityPayload from an existing ContactIdentity.
         */
        fun fromContactIdentity(identity: ContactIdentity, version: Int = CURRENT_VERSION): QrIdentityPayload {
            return QrIdentityPayload(
                nodeId = identity.nodeId,
                callsign = identity.callsign,
                displayName = identity.displayName,
                supportedLanguages = identity.supportedLanguages,
                version = version
            )
        }
    }
}
