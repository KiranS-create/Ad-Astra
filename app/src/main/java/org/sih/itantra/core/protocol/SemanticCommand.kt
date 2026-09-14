package org.sih.itantra.core.protocol

import org.sih.itantra.core.common.IndicLanguage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Emergency Category identifier for structured commands.
 */
enum class EmergencyCategory(val id: Byte, val label: String) {
    OTHER(0, "OTHER"),
    MEDICAL(1, "MEDICAL"),
    FIRE(2, "FIRE"),
    TRAPPED(3, "TRAPPED"),
    RESCUE(4, "RESCUE"),
    SUPPLY(5, "SUPPLY"),
    EVACUATION(6, "EVACUATION"),
    SECURITY(7, "SECURITY"),
    HAZARD(8, "HAZARD");

    companion object {
        fun fromId(id: Byte): EmergencyCategory =
            entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/**
 * Emergency Subtype identifier for structured commands.
 */
enum class EmergencySubtype(val id: Byte, val label: String) {
    NONE(0, "NONE"),
    AMBULANCE(1, "AMBULANCE"),
    UNCONSCIOUS(2, "UNCONSCIOUS"),
    INJURED(3, "INJURED"),
    BUILDING(4, "BUILDING"),
    TEAM(5, "TEAM"),
    FOOD(6, "FOOD"),
    WATER(7, "WATER"),
    ROAD(8, "ROAD"),
    COLLAPSE(9, "COLLAPSE");

    companion object {
        fun fromId(id: Byte): EmergencySubtype =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Severity level of the structured emergency command.
 */
enum class EmergencySeverity(val id: Byte, val label: String) {
    NORMAL(0, "NORMAL"),
    IMPORTANT(1, "IMPORTANT"),
    ALERT(2, "ALERT"),
    CRITICAL(3, "CRITICAL");

    companion object {
        fun fromId(id: Byte): EmergencySeverity =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/**
 * Deterministic Structured Emergency Command.
 *
 * Wire encoding: Exactly 6 bytes:
 * - byte 0: category (1B)
 * - byte 1: subtype (1B)
 * - byte 2: severity (1B)
 * - byte 3: count (1B: 0 = unspecified, 1..255)
 * - byte 4..5: parameter (2B short)
 */
data class SemanticCommand(
    val category: EmergencyCategory,
    val subtype: EmergencySubtype = EmergencySubtype.NONE,
    val count: Int = 0,
    val severity: EmergencySeverity = EmergencySeverity.CRITICAL,
    val parameter: Short = 0
) {
    val sector: Int get() = parameter.toInt()

    /**
     * Serialize into ultra-compact 6-byte binary payload.
     */
    fun serialize(): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        buffer.put(category.id)
        buffer.put(subtype.id)
        buffer.put(severity.id)
        buffer.put((count.coerceIn(0, 255)).toByte())
        buffer.putShort(parameter)
        return buffer.array()
    }

    /**
     * Single-line compact badge label.
     * E.g. "SEMANTIC • MEDICAL • 3 • AMBULANCE"
     */
    fun toBadgeString(): String {
        val parts = mutableListOf("SEMANTIC", category.label)
        if (count > 0) parts.add(count.toString())
        if (parameter > 0) parts.add("SECTOR $parameter")
        if (subtype != EmergencySubtype.NONE) parts.add(subtype.label)
        return parts.joinToString(" • ")
    }

    /**
     * Human-readable structured card format for receiver UI.
     * E.g.:
     * 🚨 MEDICAL EMERGENCY
     * 3 PEOPLE
     * AMBULANCE REQUIRED
     */
    fun toDisplayString(): String {
        val header = when (category) {
            EmergencyCategory.MEDICAL -> "🚨 MEDICAL EMERGENCY"
            EmergencyCategory.FIRE -> "🚨 FIRE EMERGENCY"
            EmergencyCategory.TRAPPED -> "🚨 TRAPPED EMERGENCY"
            EmergencyCategory.RESCUE -> "🚨 RESCUE OPERATION"
            EmergencyCategory.SUPPLY -> "⚠️ SUPPLY REQUEST"
            EmergencyCategory.EVACUATION -> "🚨 EVACUATION ORDER"
            EmergencyCategory.SECURITY -> "⚠️ SECURITY ALERT"
            EmergencyCategory.HAZARD -> "⚠️ HAZARD WARNING"
            EmergencyCategory.OTHER -> "🚨 EMERGENCY ALERT"
        }

        val details = mutableListOf<String>()
        if (count > 0) {
            val peopleLabel = if (count == 1) "1 PERSON" else "$count PEOPLE"
            when (subtype) {
                EmergencySubtype.INJURED -> details.add("$peopleLabel INJURED")
                EmergencySubtype.UNCONSCIOUS -> details.add("$peopleLabel UNCONSCIOUS")
                else -> details.add(peopleLabel)
            }
        }

        if (parameter > 0) {
            details.add("SECTOR $parameter")
        }

        when (subtype) {
            EmergencySubtype.AMBULANCE -> details.add("AMBULANCE REQUIRED")
            EmergencySubtype.UNCONSCIOUS -> if (count == 0) details.add("PERSON UNCONSCIOUS")
            EmergencySubtype.INJURED -> if (count == 0) details.add("INJURIES REPORTED")
            EmergencySubtype.BUILDING -> details.add("BUILDING INVOLVED")
            EmergencySubtype.COLLAPSE -> details.add("STRUCTURAL COLLAPSE")
            EmergencySubtype.TEAM -> details.add("RESCUE TEAM REQUIRED")
            EmergencySubtype.FOOD -> details.add("FOOD REQUIRED")
            EmergencySubtype.WATER -> details.add("WATER REQUIRED")
            EmergencySubtype.ROAD -> details.add("ROAD BLOCKED")
            EmergencySubtype.NONE -> {}
        }

        if (category == EmergencyCategory.EVACUATION && severity == EmergencySeverity.CRITICAL) {
            details.add("EVACUATE IMMEDIATELY")
        }

        return if (details.isEmpty()) header else "$header\n${details.joinToString(" • ")}"
    }

    /**
     * Localized speech synthesis text for local TTS playback on receiving node.
     */
    fun toTtsText(language: IndicLanguage): String {
        return if (language == IndicLanguage.TAMIL) {
            when (category) {
                EmergencyCategory.MEDICAL -> when (subtype) {
                    EmergencySubtype.AMBULANCE -> "மருத்துவ அவசரம். ஆம்புலன்ஸ் உடனடியாக தேவை."
                    EmergencySubtype.UNCONSCIOUS -> "மருத்துவ அவசரம். நோயாளி மயக்கமடைந்துள்ளார்."
                    EmergencySubtype.INJURED -> "மருத்துவ அவசரம். காயமடைந்தவர்களுக்கு சிகிச்சை தேவை."
                    else -> "மருத்துவ அவசரம்."
                }
                EmergencyCategory.FIRE -> "தீ விபத்து ஏற்பட்டுள்ளது. எச்சரிக்கையாக இருக்கவும்."
                EmergencyCategory.TRAPPED -> "மக்கள் சிக்கியுள்ளனர். உடனடியாக மீட்கவும்."
                EmergencyCategory.RESCUE -> "மீட்புக் குழு உடனடியாக தேவை."
                EmergencyCategory.SUPPLY -> when (subtype) {
                    EmergencySubtype.WATER -> "குடிநீர் விநியோகம் உடனடியாக தேவை."
                    EmergencySubtype.FOOD -> "உணவு விநியோகம் உடனடியாக தேவை."
                    else -> "அத்தியாவசிய பொருட்கள் தேவை."
                }
                EmergencyCategory.EVACUATION -> "உடனடியாக அந்த இடத்தை விட்டு வெளியேறவும்."
                else -> "அவசர உதவி தேவை."
            }
        } else {
            // Default English speech synthesis
            when (category) {
                EmergencyCategory.MEDICAL -> when (subtype) {
                    EmergencySubtype.AMBULANCE -> "Medical emergency. Ambulance required."
                    EmergencySubtype.UNCONSCIOUS -> "Medical emergency. Person is unconscious."
                    EmergencySubtype.INJURED -> "Medical emergency. Injuries reported."
                    else -> "Medical emergency reported."
                }
                EmergencyCategory.FIRE -> "Fire emergency reported. Building involved."
                EmergencyCategory.TRAPPED -> if (count > 0) "$count people trapped. Immediate rescue required." else "People trapped. Immediate rescue required."
                EmergencyCategory.RESCUE -> "Rescue operation needed. Send rescue team."
                EmergencyCategory.SUPPLY -> when (subtype) {
                    EmergencySubtype.WATER -> "Supply request. Water required."
                    EmergencySubtype.FOOD -> "Supply request. Food required."
                    else -> "Emergency supplies required."
                }
                EmergencyCategory.EVACUATION -> "Evacuation order. Evacuate immediately."
                else -> "Emergency assistance required."
            }
        }
    }

    companion object {
        const val SIZE_BYTES = 6

        /**
         * Deserialize from raw binary bytes. Returns null if buffer is too small.
         */
        fun deserialize(bytes: ByteArray): SemanticCommand? {
            if (bytes.size < SIZE_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val cat = EmergencyCategory.fromId(buffer.get())
            val sub = EmergencySubtype.fromId(buffer.get())
            val sev = EmergencySeverity.fromId(buffer.get())
            val cnt = buffer.get().toInt() and 0xFF
            val param = buffer.short
            return SemanticCommand(cat, sub, cnt, sev, param)
        }
    }
}
