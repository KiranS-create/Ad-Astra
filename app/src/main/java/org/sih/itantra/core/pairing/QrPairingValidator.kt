package org.sih.itantra.core.pairing

import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.contact.TacticalContact

/**
 * Result of validating a scanned or entered QR node identity.
 */
sealed class ValidationResult {
    /** Valid new contact identity ready for confirmation and addition. */
    data class Success(val payload: QrIdentityPayload) : ValidationResult()

    /** Scanned QR matches the local handset's own node ID. Must be rejected. */
    data class SelfNode(
        val nodeId: Int,
        val userMessage: String = "This is this device (Local Node #$nodeId). You cannot pair your own node."
    ) : ValidationResult()

    /** Contact already exists in local directory. */
    data class Duplicate(
        val existingContact: TacticalContact,
        val payload: QrIdentityPayload? = null,
        val userMessage: String = "Contact #${existingContact.nodeId} (${existingContact.callsign}) is already in your tactical contacts directory."
    ) : ValidationResult()

    /** Syntax, version, or format error. */
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * Tactical validator for QR pairing workflows.
 *
 * Enforces:
 * - Rejection of self-node identities.
 * - Detection of existing/duplicate contacts.
 * - Robust error handling for malformed or unsupported QR formats.
 */
object QrPairingValidator {

    /**
     * Validates a parsed QrIdentityPayload against local node identity and contact directory.
     */
    fun validate(
        payload: QrIdentityPayload,
        localNodeId: Int,
        contactRepository: ContactRepository? = null
    ): ValidationResult {
        // 1. Self-node detection
        if (payload.nodeId == localNodeId) {
            return ValidationResult.SelfNode(
                nodeId = payload.nodeId,
                userMessage = "This is this device (Local Node #${payload.nodeId}). You cannot pair your own node."
            )
        }

        // 2. Duplicate detection
        val existing = contactRepository?.getContact(payload.nodeId)
        if (existing != null) {
            return ValidationResult.Duplicate(
                existingContact = existing,
                payload = payload,
                userMessage = "Contact #${existing.nodeId} (${existing.callsign}) is already in your tactical contacts directory."
            )
        }

        return ValidationResult.Success(payload)
    }

    /**
     * Parses and validates raw QR string input.
     */
    fun validateScannedQr(
        rawPayload: String,
        localNodeId: Int,
        contactRepository: ContactRepository? = null
    ): ValidationResult {
        val decodeResult = QrIdentityCodec.tryDecode(rawPayload)
        return decodeResult.fold(
            onSuccess = { payload ->
                validate(payload, localNodeId, contactRepository)
            },
            onFailure = { error ->
                ValidationResult.Invalid(error.message ?: "Invalid QR identity format")
            }
        )
    }

    fun validateRaw(
        raw: String,
        localNodeId: Int,
        contactRepository: ContactRepository? = null
    ): ValidationResult = validateScannedQr(raw, localNodeId, contactRepository)
}
