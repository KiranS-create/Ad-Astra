package org.sih.itantra.core.protocol

/**
 * Deterministic offline tactical voice command enum.
 *
 * Maps explicit keyword phrases from the 10 supported Indic languages
 * to radio operation commands. No ML/NLP is used — only exact/high-confidence
 * keyword matching against compact phrase dictionaries.
 */
public enum class VoiceCommand {
    SEND,
    CANCEL,
    DISTRESS,
    ALERT,
    STATUS,
    PTT_MODE,
    CONTINUOUS_MODE,
    STOP,
    SWITCH_LANGUAGE,
    REPEAT,
    MUTE,
    UNMUTE,
    TOPOLOGY,
    DIAGNOSTICS;

    companion object {
        /**
         * Default command when no recognized command is matched.
         */
        fun unknown(): VoiceCommand = VoiceCommand.SEND

        /**
         * Returns the set of all supported command enums.
         */
        val all: Set<VoiceCommand> = setOf(
            SEND, CANCEL, DISTRESS, ALERT, STATUS,
            PTT_MODE, CONTINUOUS_MODE, STOP,
            SWITCH_LANGUAGE, REPEAT, MUTE, UNMUTE,
            TOPOLOGY, DIAGNOSTICS
        )
    }
}