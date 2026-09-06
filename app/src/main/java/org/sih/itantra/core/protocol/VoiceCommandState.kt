package org.sih.itantra.core.protocol

/**
 * Voice Command Control State Machine.
 *
 * Small deterministic state machine for radio voice command handling.
 * States represent the lifecycle of a voice command from detection to execution.
 */
public enum class VoiceCommandState(val displayName: String) {
    /**
     * Idle - voice control is active but not listening for commands.
     */
    IDLE("Ready"),

    /**
     * Listening - actively capturing audio for command recognition.
     */
    LISTENING("Listening"),

    /**
     * Command Detected - a known phrase matched, awaiting confirmation if required.
     */
    COMMAND_DETECTED("Command Detected"),

    /**
     * Awaiting Confirmation - safety-critical commands (DISTRESS, ALERT, STOP/CANCEL) require explicit confirmation.
     */
    AWAITING_CONFIRMATION("Confirm?"),

    /**
     * Executing - command is being executed (e.g., sending packet, switching language, opening screen).
     */
    EXECUTING("Executing"),

    /**
     * Error - command failed or was rejected.
     */
    ERROR("Error");

    companion object {
        /**
         * Returns whether a state is terminal (no further transitions expected).
         */
        fun isTerminal(state: VoiceCommandState): Boolean = state == IDLE || state == ERROR
    }
}