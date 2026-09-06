package org.sih.itantra.core.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice Command State Machine.
 *
 * Manages state transitions for the voice command control layer.
 * Follows a simple deterministic flow:
 * IDLE -> LISTENING -> COMMAND_DETECTED -> [AWAITING_CONFIRMATION] -> EXECUTING -> IDLE
 *                              -> ERROR -> IDLE (on reset)
 */
public class VoiceCommandStateMachine {

    private val _state = MutableStateFlow(VoiceCommandState.IDLE)
    val state: StateFlow<VoiceCommandState> = _state.asStateFlow()

    var onStateChanged: ((VoiceCommandState) -> Unit)? = null

    /**
     * Commands that require explicit confirmation before execution.
     * These are safety-critical commands that could trigger emergency actions.
     */
    private val CONFIRMATION_REQUIRED = setOf(
        VoiceCommand.DISTRESS,
        VoiceCommand.ALERT,
        VoiceCommand.STOP,
        VoiceCommand.CANCEL
    )

    /**
     * Attempts to transition to the next state.
     * Returns true if transition was valid and executed.
     */
    fun transitionTo(nextState: VoiceCommandState): Boolean {
        val current = _state.value
        val isValid = when (nextState) {
            VoiceCommandState.IDLE -> true // Can always go to IDLE
            VoiceCommandState.LISTENING -> current == VoiceCommandState.IDLE
            VoiceCommandState.COMMAND_DETECTED -> current == VoiceCommandState.LISTENING
            VoiceCommandState.AWAITING_CONFIRMATION -> current == VoiceCommandState.COMMAND_DETECTED
            VoiceCommandState.EXECUTING -> current == VoiceCommandState.COMMAND_DETECTED ||
                current == VoiceCommandState.AWAITING_CONFIRMATION
            VoiceCommandState.ERROR -> true // Can always go to ERROR
        }

        if (isValid) {
            _state.value = nextState
            onStateChanged?.invoke(nextState)
        }
        return isValid
    }

    /**
     * Called when a command is detected during LISTENING state.
     * Determines if confirmation is required based on command type.
     */
    fun onCommandDetected(command: VoiceCommand): VoiceCommandState {
        val nextState = if (command in CONFIRMATION_REQUIRED) {
            VoiceCommandState.AWAITING_CONFIRMATION
        } else {
            VoiceCommandState.EXECUTING
        }
        _state.value = nextState
        onStateChanged?.invoke(nextState)
        return nextState
    }

    /**
     * Called when user confirms a command that required confirmation.
     * Transitions from AWAITING_CONFIRMATION to EXECUTING.
     */
    fun confirm(): Boolean {
        return if (_state.value == VoiceCommandState.AWAITING_CONFIRMATION) {
            transitionTo(VoiceCommandState.EXECUTING)
        } else {
            false
        }
    }

    /**
     * Called when user rejects a command that required confirmation.
     * Transitions back to IDLE.
     */
    fun reject(): Boolean {
        return if (_state.value == VoiceCommandState.AWAITING_CONFIRMATION) {
            transitionTo(VoiceCommandState.IDLE)
        } else {
            false
        }
    }

    /**
     * Called when command execution completes (success or failure).
     * Returns to IDLE state.
     */
    fun complete(success: Boolean = true) {
        _state.value = if (success) VoiceCommandState.IDLE else VoiceCommandState.ERROR
        onStateChanged?.invoke(_state.value)
    }

    /**
     * Resets the state machine to IDLE.
     */
    fun reset() {
        _state.value = VoiceCommandState.IDLE
        onStateChanged?.invoke(VoiceCommandState.IDLE)
    }

    /**
     * Sets an error state with automatic reset after delay.
     */
    fun error() {
        _state.value = VoiceCommandState.ERROR
        onStateChanged?.invoke(VoiceCommandState.ERROR)
    }

    /**
     * Returns whether the state machine is currently in a state
     * where it can accept a new command (i.e., not in confirmation or executing).
     */
    fun isReadyForCommand(): Boolean {
        return _state.value == VoiceCommandState.IDLE || _state.value == VoiceCommandState.LISTENING
    }

    /**
     * Returns whether the current state requires user confirmation.
     */
    fun requiresConfirmation(): Boolean {
        return _state.value == VoiceCommandState.AWAITING_CONFIRMATION
    }

    /**
     * Returns whether currently executing a command.
     */
    fun isExecuting(): Boolean {
        return _state.value == VoiceCommandState.EXECUTING
    }

    /**
     * Returns whether currently listening for commands.
     */
    fun isListening(): Boolean {
        return _state.value == VoiceCommandState.LISTENING
    }
}