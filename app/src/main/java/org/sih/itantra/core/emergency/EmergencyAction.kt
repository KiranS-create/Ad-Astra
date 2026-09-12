package org.sih.itantra.core.emergency

import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand

/**
 * Predefined tactical emergency actions matching iTantra semantic command categories.
 */
enum class EmergencyAction(
    val actionId: String,
    val label: String,
    val defaultText: String,
    val category: EmergencyCategory,
    val subtype: EmergencySubtype,
    val severity: EmergencySeverity
) {
    MEDICAL(
        actionId = "MEDICAL",
        label = "MEDICAL",
        defaultText = "Medical emergency hospital needed immediately.",
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.NONE,
        severity = EmergencySeverity.CRITICAL
    ),
    INJURED(
        actionId = "INJURED",
        label = "INJURED",
        defaultText = "Officer injured urgent first aid needed.",
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.INJURED,
        severity = EmergencySeverity.CRITICAL
    ),
    TRAPPED(
        actionId = "TRAPPED",
        label = "TRAPPED",
        defaultText = "Personnel trapped under debris collapse.",
        category = EmergencyCategory.TRAPPED,
        subtype = EmergencySubtype.COLLAPSE,
        severity = EmergencySeverity.CRITICAL
    ),
    ATTACK(
        actionId = "ATTACK",
        label = "ATTACK",
        defaultText = "Under attack security threat immediate support required.",
        category = EmergencyCategory.SECURITY,
        subtype = EmergencySubtype.NONE,
        severity = EmergencySeverity.CRITICAL
    ),
    FIRE(
        actionId = "FIRE",
        label = "FIRE",
        defaultText = "Fire reported building hazard evacuate immediately.",
        category = EmergencyCategory.FIRE,
        subtype = EmergencySubtype.BUILDING,
        severity = EmergencySeverity.CRITICAL
    ),
    EVACUATION(
        actionId = "EVACUATION",
        label = "EVACUATION",
        defaultText = "Immediate evacuation order all units fall back.",
        category = EmergencyCategory.EVACUATION,
        subtype = EmergencySubtype.NONE,
        severity = EmergencySeverity.CRITICAL
    ),
    NEED_EXTRACTION(
        actionId = "NEED_EXTRACTION",
        label = "EXTRACTION",
        defaultText = "Send rescue team urgent tactical extraction needed.",
        category = EmergencyCategory.RESCUE,
        subtype = EmergencySubtype.TEAM,
        severity = EmergencySeverity.CRITICAL
    ),
    LOCATION(
        actionId = "LOCATION",
        label = "LOCATION",
        defaultText = "Tactical beacon location broadcast. Requesting status update.",
        category = EmergencyCategory.OTHER,
        subtype = EmergencySubtype.NONE,
        severity = EmergencySeverity.ALERT
    );

    fun toSemanticCommand(count: Int = 1): SemanticCommand =
        SemanticCommand(
            category = category,
            subtype = subtype,
            count = count,
            severity = severity
        )

    companion object {
        fun fromId(id: String): EmergencyAction? =
            entries.firstOrNull { it.actionId.equals(id, ignoreCase = true) }
    }
}
