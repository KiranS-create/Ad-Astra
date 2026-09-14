package org.sih.itantra.core.vbr

/**
 * Feature 16B & Feature 18: Application-level Adaptive Representation Modes for Two-Pass Semantic VBR.
 *
 * Distinct representation modes for tactical communications:
 * 1. [FULL]: Verbatim natural language text.
 * 2. [COMPACT]: Deterministic tactical shorthand with conversational fillers removed.
 * 3. [SEMANTIC]: Structured emergency command (generic/unspecified layer).
 * 4. [SEMANTIC_BASE]: Feature 18 Base-only representation (compact binary survival payload).
 * 5. [SEMANTIC_ENHANCED]: Feature 18 Base + Enhancement layer (structured tactical base with optional context).
 * 6. [UNKNOWN]: Fallback/unspecified representation.
 */
enum class AdaptiveRepresentationMode(val label: String, val badgeLabel: String = label) {
    FULL("FULL", "FULL"),
    COMPACT("COMPACT", "COMPACT"),
    SEMANTIC("SEMANTIC", "SEMANTIC"),
    SEMANTIC_BASE("SEMANTIC_BASE", "BASE ONLY"),
    SEMANTIC_ENHANCED("SEMANTIC_ENHANCED", "BASE+ENH"),
    CONTEXT_DELTA("CONTEXT_DELTA", "CTX DELTA"),
    UNKNOWN("UNKNOWN", "UNKNOWN");

    val isSemantic: Boolean
        get() = this == SEMANTIC || this == SEMANTIC_BASE || this == SEMANTIC_ENHANCED || this == CONTEXT_DELTA

    companion object {
        fun fromString(value: String?): AdaptiveRepresentationMode {
            if (value == null) return UNKNOWN
            return when (value.uppercase()) {
                "FULL" -> FULL
                "COMPACT" -> COMPACT
                "SEMANTIC" -> SEMANTIC
                "SEMANTIC_BASE", "BASE_ONLY" -> SEMANTIC_BASE
                "SEMANTIC_ENHANCED", "BASE_PLUS_ENHANCEMENT", "BASE_ENHANCED" -> SEMANTIC_ENHANCED
                "CONTEXT_DELTA", "DELTA", "CTX_DELTA", "CTX DELTA" -> CONTEXT_DELTA
                else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
            }
        }
    }
}

