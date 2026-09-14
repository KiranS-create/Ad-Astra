package org.sih.itantra.core.vbr

/**
 * Feature 16B: Application-level Adaptive Representation Modes for Two-Pass Semantic VBR.
 *
 * Distinct representation modes for tactical communications:
 * 1. [FULL]: Verbatim natural language text.
 * 2. [COMPACT]: Deterministic tactical shorthand with conversational fillers removed.
 * 3. [SEMANTIC]: Ultra-compact 6-byte binary payload for structured emergency commands.
 * 4. [UNKNOWN]: Fallback/unspecified representation.
 */
enum class AdaptiveRepresentationMode(val label: String) {
    FULL("FULL"),
    COMPACT("COMPACT"),
    SEMANTIC("SEMANTIC"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String?): AdaptiveRepresentationMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
