package org.sih.itantra.core.benchmark.corpus

import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand

/**
 * Structured tactical ground-truth facts for evaluating representation fidelity.
 */
data class TacticalGroundTruth(
    val id: String,
    val text: String,
    val category: EmergencyCategory,
    val subtype: EmergencySubtype,
    val severity: EmergencySeverity,
    val count: Int,
    val sector: Short,
    val requiredAction: String,
    val isDeltaUpdate: Boolean = false,
    val baseMessageId: String? = null
) {
    fun toSemanticCommand(): SemanticCommand {
        return SemanticCommand(
            category = category,
            subtype = subtype,
            severity = severity,
            count = count,
            parameter = sector
        )
    }
}

/**
 * Standard multi-message tactical benchmark corpus supporting comparative analysis
 * across FULL, COMPACT, SEMANTIC_BASE, SEMANTIC_ENHANCED, and CONTEXT_DELTA.
 */
object TacticalCorpus {

    // Medical emergency update sequence (from Section 5 of prompt)
    val MSG_MED_INITIAL = TacticalGroundTruth(
        id = "TAC-MED-01",
        text = "Medical emergency, 3 people injured, sector 4, ambulance required.",
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.INJURED,
        severity = EmergencySeverity.CRITICAL,
        count = 3,
        sector = 4,
        requiredAction = "AMBULANCE_REQUIRED"
    )

    val MSG_MED_UPDATE_1 = TacticalGroundTruth(
        id = "TAC-MED-02",
        text = "Medical emergency, 4 people injured, sector 4, ambulance required.",
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.INJURED,
        severity = EmergencySeverity.CRITICAL,
        count = 4,
        sector = 4,
        requiredAction = "AMBULANCE_REQUIRED",
        isDeltaUpdate = true,
        baseMessageId = "TAC-MED-01"
    )

    val MSG_MED_UPDATE_2 = TacticalGroundTruth(
        id = "TAC-MED-03",
        text = "Medical emergency, 5 people injured, sector 4, ambulance required.",
        category = EmergencyCategory.MEDICAL,
        subtype = EmergencySubtype.INJURED,
        severity = EmergencySeverity.CRITICAL,
        count = 5,
        sector = 4,
        requiredAction = "AMBULANCE_REQUIRED",
        isDeltaUpdate = true,
        baseMessageId = "TAC-MED-01"
    )

    // Fire outbreak incident
    val MSG_FIRE = TacticalGroundTruth(
        id = "TAC-FIRE-01",
        text = "Structure fire reported, 2 buildings involved, sector 7, fire team required.",
        category = EmergencyCategory.FIRE,
        subtype = EmergencySubtype.BUILDING,
        severity = EmergencySeverity.ALERT,
        count = 2,
        sector = 7,
        requiredAction = "FIRE_TEAM_REQUIRED"
    )

    // Ammo / Logistics resupply request
    val MSG_AMMO = TacticalGroundTruth(
        id = "TAC-SUPPLY-01",
        text = "Ammunition resupply requested, 500 rounds 7.62mm, sector 2, transport required.",
        category = EmergencyCategory.SUPPLY,
        subtype = EmergencySubtype.NONE,
        severity = EmergencySeverity.IMPORTANT,
        count = 255, // 1-byte count max in SemanticCommand is 255
        sector = 2,
        requiredAction = "TRANSPORT_REQUIRED"
    )

    // Mass evacuation order
    val MSG_EVAC = TacticalGroundTruth(
        id = "TAC-EVAC-01",
        text = "Immediate evacuation ordered, 12 personnel, sector 9, helicopter required.",
        category = EmergencyCategory.EVACUATION,
        subtype = EmergencySubtype.NONE,
        severity = EmergencySeverity.CRITICAL,
        count = 12,
        sector = 9,
        requiredAction = "HELICOPTER_REQUIRED"
    )

    // Complete representative corpus
    val ALL_MESSAGES = listOf(
        MSG_MED_INITIAL,
        MSG_MED_UPDATE_1,
        MSG_MED_UPDATE_2,
        MSG_FIRE,
        MSG_AMMO,
        MSG_EVAC
    )
}
