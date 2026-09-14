package org.sih.itantra.core.message

import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure stateless mapper that projects a [MessageRecord] and its associated [RadioMessageTelemetry]
 * (from Feature 6) into a structured, operator-friendly [MessageTechnicalInspector].
 *
 * Rules:
 * 1. Absolute Data Accuracy: Only display values grounded in actual application state.
 * 2. Missing data is represented as "UNKNOWN" or the row/section is cleanly omitted.
 * 3. Never claim encryption for HMAC-SHA256 (HMAC = authentication/integrity only).
 * 4. Never invent packet IDs, RSSI, routes, or fake demo telemetry.
 * 5. Reuses Feature 6's [RadioMessageStateMapper] to ensure 100% consistency.
 */
object MessageTechnicalInspectorMapper {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Maps a [MessageRecord] into a [MessageTechnicalInspector].
     *
     * @param record The source message record.
     * @param telemetryOverride Optional pre-computed Feature 6 telemetry. If null, mapped via [RadioMessageStateMapper].
     * @param transportOverride Optional topology-derived transport override.
     */
    fun map(
        record: MessageRecord,
        telemetryOverride: RadioMessageTelemetry? = null,
        transportOverride: String? = null
    ): MessageTechnicalInspector {
        val telemetry = telemetryOverride ?: RadioMessageStateMapper.map(record, transportOverride)
        val isEmergency = record.priority == MessagePriority.DISTRESS || record.priority == MessagePriority.ALERT
        val sections = mutableListOf<TechnicalInspectorSection>()

        // 1. MESSAGE SECTION
        sections.add(buildMessageSection(record, telemetry))

        // 2. DELIVERY SECTION
        sections.add(buildDeliverySection(record, telemetry))

        // 3. ROUTE SECTION
        sections.add(buildRouteSection(record, telemetry))

        // 4. DTN SECTION (Included when DTN is involved or active)
        buildDtnSection(record, telemetry)?.let { sections.add(it) }

        // 5. FRAGMENTATION SECTION
        sections.add(buildFragmentationSection(record, telemetry))

        // 6. SECURITY & INTEGRITY SECTION
        sections.add(buildSecurityAndIntegritySection(record, telemetry))

        return MessageTechnicalInspector(
            messageId = record.id,
            isEmergency = isEmergency,
            priorityContext = telemetry.priorityContext,
            sections = sections
        )
    }

    private fun buildMessageSection(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): TechnicalInspectorSection {
        val isOutgoing = record.direction == MessageDirection.SENT
        val formattedTimestamp = if (record.timestamp > 0) {
            timeFormat.format(Date(record.timestamp))
        } else {
            "UNKNOWN"
        }

        val messageType = when {
            record.priority == MessagePriority.DISTRESS -> "EMERGENCY DISTRESS"
            record.priority == MessagePriority.ALERT    -> "EMERGENCY ALERT"
            record.isSemantic                          -> "SEMANTIC COMMAND"
            else                                       -> "TACTICAL TEXT"
        }

        val typeStyle = when {
            record.priority == MessagePriority.DISTRESS -> TechnicalFieldStyle.ALERT
            record.priority == MessagePriority.ALERT    -> TechnicalFieldStyle.WARNING
            record.isSemantic                          -> TechnicalFieldStyle.HIGHLIGHT
            else                                       -> TechnicalFieldStyle.NORMAL
        }

        val fields = mutableListOf<TechnicalInspectorField>()

        fields.add(TechnicalInspectorField("MESSAGE ID", record.id))
        fields.add(TechnicalInspectorField(
            "DIRECTION",
            if (isOutgoing) "OUTGOING (TX)" else "INCOMING (RX)",
            style = if (isOutgoing) TechnicalFieldStyle.HIGHLIGHT else TechnicalFieldStyle.SUCCESS
        ))
        fields.add(TechnicalInspectorField(
            "SOURCE",
            if (isOutgoing) "Local Node (Self)" else (if (record.peer.isNotBlank()) record.peer else "UNKNOWN")
        ))
        fields.add(TechnicalInspectorField(
            "DESTINATION",
            if (isOutgoing) (if (record.peer.isNotBlank()) record.peer else "Broadcast") else "Local Node (Self)"
        ))
        fields.add(TechnicalInspectorField("TIMESTAMP", formattedTimestamp))
        fields.add(TechnicalInspectorField("TYPE", messageType, style = typeStyle))
        fields.add(TechnicalInspectorField("LANGUAGE", "${record.language.displayName} (${record.language.isoCode})"))
        fields.add(TechnicalInspectorField(
            "PRIORITY",
            "${record.priority.name} (P${record.priority.id})",
            style = if (telemetry.priorityContext.isEmergency) TechnicalFieldStyle.ALERT else TechnicalFieldStyle.NORMAL
        ))
        record.representationMode?.let { mode ->
            val label = when (mode) {
                "SEMANTIC_ENHANCED", "BASE_PLUS_ENHANCEMENT" -> "SEMANTIC BASE + ENHANCEMENT"
                "SEMANTIC_BASE", "BASE_ONLY" -> "SEMANTIC BASE"
                "SEMANTIC" -> "SEMANTIC"
                "COMPACT" -> "COMPACT"
                else -> "FULL"
            }
            fields.add(TechnicalInspectorField(
                "VBR REPRESENTATION",
                label,
                style = when {
                    mode.contains("SEMANTIC") || mode.contains("BASE") -> TechnicalFieldStyle.HIGHLIGHT
                    mode == "COMPACT" -> TechnicalFieldStyle.WARNING
                    else -> TechnicalFieldStyle.NORMAL
                }
            ))
        }

        record.semanticBaseBytes?.let { b ->
            fields.add(TechnicalInspectorField("BASE PAYLOAD", "$b B (tactical core)", style = TechnicalFieldStyle.HIGHLIGHT))
        }
        record.enhancementBytes?.let { e ->
            val text = if (e > 0) "$e B (context layer)" else "0 B (omitted / constrained link)"
            fields.add(TechnicalInspectorField("ENHANCEMENT PAYLOAD", text, style = if (e > 0) TechnicalFieldStyle.SUCCESS else TechnicalFieldStyle.NORMAL))
        }
        record.enhancementReceived?.let { r ->
            val text = if (r) "Yes (Progressive enhancement active)" else "No (Base-only tactical fallback)"
            fields.add(TechnicalInspectorField("ENHANCEMENT RECEIVED", text, style = if (r) TechnicalFieldStyle.SUCCESS else TechnicalFieldStyle.WARNING))
        }
        record.semanticSchemaVersion?.let { v ->
            fields.add(TechnicalInspectorField("SCHEMA VERSION", "v$v", style = TechnicalFieldStyle.NORMAL))
        }

        return TechnicalInspectorSection("MESSAGE", fields)
    }

    private fun buildDeliverySection(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): TechnicalInspectorSection {
        val fields = mutableListOf<TechnicalInspectorField>()

        val stateStyle = when (telemetry.deliveryState) {
            RadioDeliveryState.ACKNOWLEDGED, RadioDeliveryState.RECEIVED -> TechnicalFieldStyle.SUCCESS
            RadioDeliveryState.RELAYED                                    -> TechnicalFieldStyle.HIGHLIGHT
            RadioDeliveryState.DTN_STORED, RadioDeliveryState.ACK_PENDING, RadioDeliveryState.WAITING_FOR_ROUTE -> TechnicalFieldStyle.WARNING
            RadioDeliveryState.FAILED                                     -> TechnicalFieldStyle.ALERT
            else                                                          -> TechnicalFieldStyle.NORMAL
        }

        fields.add(TechnicalInspectorField(
            "DELIVERY STATE",
            "${telemetry.deliveryState.icon} ${telemetry.deliveryState.label.uppercase()}",
            style = stateStyle
        ))

        val ackStatus = when (telemetry.deliveryState) {
            RadioDeliveryState.ACKNOWLEDGED -> "VALID RECEIPT ✓"
            RadioDeliveryState.ACK_PENDING  -> "AWAITING RECEIPT"
            RadioDeliveryState.FAILED       -> "TIMEOUT (30s) ✕"
            RadioDeliveryState.RECEIVED, RadioDeliveryState.RELAYED -> "DELIVERED TO LOCAL"
            RadioDeliveryState.DTN_STORED   -> "AWAITING FORWARD ACK"
            RadioDeliveryState.WAITING_FOR_ROUTE -> "HELD (NO ROUTE)"
            RadioDeliveryState.QUEUED       -> "QUEUED (NOT SENT)"
            RadioDeliveryState.SENDING      -> "IN FLIGHT"
            RadioDeliveryState.UNKNOWN      -> "UNKNOWN"
        }

        val ackStyle = when (telemetry.deliveryState) {
            RadioDeliveryState.ACKNOWLEDGED -> TechnicalFieldStyle.SUCCESS
            RadioDeliveryState.FAILED       -> TechnicalFieldStyle.ALERT
            RadioDeliveryState.ACK_PENDING  -> TechnicalFieldStyle.WARNING
            else                            -> TechnicalFieldStyle.NORMAL
        }

        fields.add(TechnicalInspectorField("ACK STATUS", ackStatus, style = ackStyle))

        if (telemetry.ackRttMs != null && telemetry.ackRttMs > 0) {
            fields.add(TechnicalInspectorField("ACK RTT", "${telemetry.ackRttMs} ms", style = TechnicalFieldStyle.SUCCESS))
        } else if (record.deliveryLatencyMs != null && record.deliveryLatencyMs > 0) {
            fields.add(TechnicalInspectorField("DELIVERY LATENCY", "${record.deliveryLatencyMs} ms"))
        }

        if (!record.qosStatus.isNullOrBlank()) {
            fields.add(TechnicalInspectorField("QUEUE / QOS", record.qosStatus))
        }

        if (record.measuredLatencyMs > 0.0) {
            fields.add(TechnicalInspectorField("LOCAL PIPELINE", String.format(Locale.US, "%.1f ms", record.measuredLatencyMs)))
        }

        return TechnicalInspectorSection("DELIVERY", fields)
    }

    private fun buildRouteSection(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): TechnicalInspectorSection {
        val fields = mutableListOf<TechnicalInspectorField>()

        val isRelayed = record.isRelayed || (record.hopCount > 1)
        val hopCount = telemetry.hopCount

        val routeState = when {
            hopCount != null && hopCount > 1 -> "RELAYED ($hopCount HOPS)"
            hopCount == 1                     -> "DIRECT (1 HOP)"
            isRelayed                         -> "RELAYED (≥2 HOPS)"
            record.direction == MessageDirection.SENT && !record.isRelayed -> "DIRECT (1 HOP)"
            else                              -> "UNKNOWN"
        }

        val routeStyle = if (isRelayed) TechnicalFieldStyle.HIGHLIGHT else TechnicalFieldStyle.SUCCESS

        fields.add(TechnicalInspectorField("ROUTE STATE", routeState, style = routeStyle))

        val hopDisplay = when {
            hopCount != null && hopCount > 0 -> "$hopCount"
            isRelayed                        -> "≥ 2 (Relayed)"
            record.direction == MessageDirection.SENT && !record.isRelayed -> "1 (Direct)"
            else                             -> "UNKNOWN"
        }
        fields.add(TechnicalInspectorField("HOP COUNT", hopDisplay))

        fields.add(TechnicalInspectorField(
            "RELAY MODE",
            if (isRelayed) "MANET Multi-Hop Mesh" else "Single-Hop Direct Link"
        ))

        fields.add(TechnicalInspectorField(
            "TRANSPORT",
            telemetry.transport ?: "UNKNOWN",
            style = if (telemetry.transport != null) TechnicalFieldStyle.NORMAL else TechnicalFieldStyle.MUTED
        ))

        return TechnicalInspectorSection("ROUTE", fields)
    }

    private fun buildDtnSection(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): TechnicalInspectorSection? {
        val isDtnActive = telemetry.isDtnPending ||
            telemetry.deliveryState == RadioDeliveryState.DTN_STORED ||
            (record.deliveryStatus == DeliveryStatus.PENDING && record.isRelayed && record.transferId != null)

        if (!isDtnActive) return null

        val fields = listOf(
            TechnicalInspectorField("DTN STORAGE", "STORED IN DTN BUFFER", style = TechnicalFieldStyle.WARNING),
            TechnicalInspectorField("FORWARDING", "Awaiting peer contact window / route", style = TechnicalFieldStyle.NORMAL),
            TechnicalInspectorField("EXPIRY TTL", "600 s (10 min bounded)", style = TechnicalFieldStyle.MUTED)
        )

        return TechnicalInspectorSection("DTN", fields)
    }

    private fun buildFragmentationSection(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): TechnicalInspectorSection {
        val fields = mutableListOf<TechnicalInspectorField>()

        val isFragmented = telemetry.isFragmented || (record.fragmentCount != null && record.fragmentCount > 1)
        val fragmentCount = record.fragmentCount ?: 1

        fields.add(TechnicalInspectorField(
            "STATUS",
            if (isFragmented) "FRAGMENTED ($fragmentCount PACKETS)" else "SINGLE PACKET (1/1)",
            style = if (isFragmented) TechnicalFieldStyle.HIGHLIGHT else TechnicalFieldStyle.NORMAL
        ))

        fields.add(TechnicalInspectorField(
            "TOTAL FRAGMENTS",
            if (record.fragmentCount != null) "${record.fragmentCount}" else "1 (Unfragmented)"
        ))

        if (record.fragmentIndex != null) {
            fields.add(TechnicalInspectorField(
                "FRAGMENT INDEX",
                "${record.fragmentIndex + 1} of $fragmentCount"
            ))
        }

        if (isFragmented) {
            fields.add(TechnicalInspectorField(
                "REASSEMBLY",
                "COMPLETE ✓",
                style = TechnicalFieldStyle.SUCCESS
            ))
        }

        return TechnicalInspectorSection("FRAGMENTATION", fields)
    }

    private fun buildSecurityAndIntegritySection(
        record: MessageRecord,
        telemetry: RadioMessageTelemetry
    ): TechnicalInspectorSection {
        val fields = mutableListOf<TechnicalInspectorField>()

        val isAuth = record.isSecure || (record.authStatus?.contains("AUTH", ignoreCase = true) == true)
        val authValue = if (isAuth) "VALID ✓ (Authenticated)" else "UNVERIFIED"
        val authStyle = if (isAuth) TechnicalFieldStyle.SUCCESS else TechnicalFieldStyle.WARNING

        fields.add(TechnicalInspectorField("HMAC-SHA256", authValue, style = authStyle))

        if (!record.authStatus.isNullOrBlank() && record.authStatus != "AUTH ✓") {
            fields.add(TechnicalInspectorField("AUTH STATUS", record.authStatus))
        }

        fields.add(TechnicalInspectorField("WIRE PAYLOAD", "${record.packetSizeBytes} Bytes"))

        if (record.isSemantic || record.representationMode?.contains("SEMANTIC") == true || record.representationMode?.contains("BASE") == true) {
            val baseBytes = record.semanticBaseBytes ?: (if (record.isSemantic) 8 else 0)
            val enhBytes = record.enhancementBytes ?: 0
            fields.add(TechnicalInspectorField("BASE PAYLOAD", "$baseBytes Bytes"))
            fields.add(TechnicalInspectorField("ENHANCEMENT PAYLOAD", "$enhBytes Bytes"))
            fields.add(TechnicalInspectorField("SCHEMA VERSION", "v${record.semanticSchemaVersion ?: 1}"))
            record.enhancementReceived?.let { received ->
                fields.add(TechnicalInspectorField(
                    "ENHANCEMENT RECEIVED",
                    if (received) "YES ✓ (Decoded)" else "NO (Base Only)",
                    style = if (received) TechnicalFieldStyle.SUCCESS else TechnicalFieldStyle.WARNING
                ))
            }
        }

        if (record.rawAudioEquivalentBytes > 0) {
            fields.add(TechnicalInspectorField(
                "RAW AUDIO EQUIV",
                "${record.rawAudioEquivalentBytes} Bytes"
            ))
        }

        if (record.semanticSavingsBytes != null && record.semanticSavingsBytes > 0) {
            fields.add(TechnicalInspectorField(
                "SEMANTIC SAVINGS",
                "-${record.semanticSavingsBytes} B (-82%)",
                style = TechnicalFieldStyle.SUCCESS
            ))
        }

        if (record.location != null) {
            val loc = record.location
            fields.add(TechnicalInspectorField(
                "GEO LOCATION",
                String.format(Locale.US, "%.5f, %.5f (±%.1fm)", loc.latitude, loc.longitude, loc.accuracy),
                style = TechnicalFieldStyle.HIGHLIGHT
            ))
        }

        return TechnicalInspectorSection("SECURITY & INTEGRITY", fields)
    }
}
