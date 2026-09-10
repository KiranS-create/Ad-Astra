package org.sih.itantra.core.message

/**
 * Visual styling hint for an individual technical inspector field row.
 */
enum class TechnicalFieldStyle {
    NORMAL,
    HIGHLIGHT,
    SUCCESS,
    WARNING,
    ALERT,
    MUTED
}

/**
 * A single key-value telemetry field in the message technical inspector.
 *
 * @param label Human-readable technical label (e.g. "MESSAGE ID", "HMAC-SHA256").
 * @param value Factual value supported by application state (e.g. "VALID ✓", "UNKNOWN").
 * @param style Visual highlight style.
 */
data class TechnicalInspectorField(
    val label: String,
    val value: String,
    val style: TechnicalFieldStyle = TechnicalFieldStyle.NORMAL
)

/**
 * A grouped technical section in the inspector (e.g. MESSAGE, DELIVERY, ROUTE, DTN).
 *
 * @param title Section title (e.g. "MESSAGE", "DELIVERY", "ROUTE").
 * @param fields List of key-value telemetry fields.
 */
data class TechnicalInspectorSection(
    val title: String,
    val fields: List<TechnicalInspectorField>
)

/**
 * Immutable UI-ready projection model for an individual message's technical inspector.
 *
 * Ground truth rules:
 * - Every value originates from genuine application data (MessageRecord, RadioMessageTelemetry).
 * - Unavailable fields strictly show "UNKNOWN" or are omitted.
 * - Never claims encryption for HMAC-SHA256 (authentication only).
 * - Read-only; inspection triggers no network traffic, side-effects, or state mutations.
 *
 * @param messageId Unique message identifier.
 * @param isEmergency True if the message priority is DISTRESS or ALERT.
 * @param priorityContext Priority context derived from Feature 6.
 * @param sections Grouped technical sections.
 */
data class MessageTechnicalInspector(
    val messageId: String,
    val isEmergency: Boolean,
    val priorityContext: RadioPriorityContext,
    val sections: List<TechnicalInspectorSection>
)
