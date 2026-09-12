package org.sih.itantra.core.pairing

import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactValidator

/**
 * Deterministic versioned encoder and parser for tactical iTantra node identities.
 *
 * Wire format:
 * ITANTRA:<version>|<nodeId>|<callsign>|<displayName>|<supportedLanguages>
 *
 * Example:
 * ITANTRA:1|477124|NODE BRAVO|Recon Patrol|hi,mr,en
 */
object QrIdentityCodec {

    private const val HEADER_DELIMITER = ":"
    private const val FIELD_DELIMITER = "|"
    private const val ESCAPED_FIELD_DELIMITER = "\\|"
    private const val PIPE_REPLACEMENT = "/"

    /**
     * Deterministically encodes public node identity into a compact versioned QR string.
     */
    fun encode(payload: QrIdentityPayload): String {
        val cleanCallsign = payload.callsign.trim().uppercase()
        val cleanDisplayName = payload.displayName?.trim()?.replace(FIELD_DELIMITER, PIPE_REPLACEMENT).orEmpty()
        val cleanLanguages = payload.supportedLanguages
            .ifEmpty { listOf(IndicLanguage.HINDI) }
            .joinToString(",") { it.isoCode.lowercase() }

        val serialized = "${QrIdentityPayload.SCHEME}$HEADER_DELIMITER${payload.version}$FIELD_DELIMITER" +
                "${payload.nodeId}$FIELD_DELIMITER" +
                "$cleanCallsign$FIELD_DELIMITER" +
                "$cleanDisplayName$FIELD_DELIMITER" +
                cleanLanguages

        val bytes = serialized.toByteArray(Charsets.UTF_8)
        require(bytes.size <= QrIdentityPayload.MAX_PAYLOAD_BYTES) {
            "Serialized payload exceeds maximum limit of ${QrIdentityPayload.MAX_PAYLOAD_BYTES} bytes (was ${bytes.size} bytes)"
        }

        return serialized
    }

    /**
     * Parses and strictly validates a raw QR code string.
     * Throws IllegalArgumentException on invalid, malformed, or unsupported payloads.
     */
    fun decodeOrThrow(raw: String): QrIdentityPayload {
        val trimmed = raw.trim()
        val bytes = trimmed.toByteArray(Charsets.UTF_8)
        require(bytes.size <= QrIdentityPayload.MAX_PAYLOAD_BYTES) {
            "Payload exceeds maximum allowed size of ${QrIdentityPayload.MAX_PAYLOAD_BYTES} bytes"
        }
        require(trimmed.isNotEmpty()) { "QR payload cannot be empty" }

        // 1. Verify scheme and header
        val headerIdx = trimmed.indexOf(HEADER_DELIMITER)
        require(headerIdx != -1) { "Malformed payload: missing scheme header" }

        val scheme = trimmed.substring(0, headerIdx).trim().uppercase()
        require(scheme == QrIdentityPayload.SCHEME) {
            "Invalid payload scheme: expected ${QrIdentityPayload.SCHEME} but was $scheme"
        }

        val body = trimmed.substring(headerIdx + 1)
        val fields = body.split(FIELD_DELIMITER)
        require(fields.size >= 4) {
            "Malformed payload: insufficient fields (found ${fields.size}, minimum 4 required)"
        }

        // 2. Version validation
        val version = fields[0].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid version format: '${fields[0]}'")
        require(version == QrIdentityPayload.CURRENT_VERSION) {
            "Unsupported QR payload version: $version (supported: ${QrIdentityPayload.CURRENT_VERSION})"
        }

        // 3. Node ID validation
        val rawNodeId = fields[1].trim()
        val (nodeIdValid, nodeIdErr) = ContactValidator.validateNodeId(rawNodeId)
        require(nodeIdValid) { nodeIdErr ?: "Invalid Node ID: '$rawNodeId'" }
        val nodeId = rawNodeId.removePrefix("#").toInt()

        // 4. Callsign validation
        val rawCallsign = fields[2].trim()
        val (callsignValid, callsignErr) = ContactValidator.validateCallsign(rawCallsign)
        require(callsignValid) { callsignErr ?: "Invalid Callsign: '$rawCallsign'" }
        val callsign = rawCallsign.uppercase()

        // 5. Display Name
        val displayName = fields[3].trim()

        // 6. Supported Languages
        val langs = if (fields.size > 4 && fields[4].isNotBlank()) {
            fields[4].split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { IndicLanguage.fromIsoCode(it) }
                .distinct()
                .ifEmpty { listOf(IndicLanguage.HINDI) }
        } else {
            listOf(IndicLanguage.HINDI)
        }

        return QrIdentityPayload(
            nodeId = nodeId,
            callsign = callsign,
            displayName = displayName,
            supportedLanguages = langs,
            version = version
        )
    }

    /**
     * Non-throwing decoder that returns null on invalid or malformed input.
     */
    fun decode(raw: String): QrIdentityPayload? = runCatching {
        decodeOrThrow(raw)
    }.getOrNull()

    /**
     * Non-throwing decoder that wraps outcome in a Kotlin Result.
     */
    fun tryDecode(raw: String): Result<QrIdentityPayload> = runCatching {
        decodeOrThrow(raw)
    }

    /**
     * Non-throwing decoder that returns null on failure.
     */
    fun decodeOrNull(raw: String): QrIdentityPayload? = decode(raw)
}

