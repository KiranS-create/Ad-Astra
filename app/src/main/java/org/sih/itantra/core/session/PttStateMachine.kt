package org.sih.itantra.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PttStateMachine {

    private val _state = MutableStateFlow(PttState.IDLE)
    val state: StateFlow<PttState> = _state.asStateFlow()

    var onStateChanged: ((PttState) -> Unit)? = null

    fun transitionTo(nextState: PttState): Boolean {
        val current = _state.value
        // Verify valid transition
        val isValid = when (nextState) {
            PttState.IDLE -> true
            PttState.PTT_PRESSED -> current == PttState.IDLE
            PttState.RECORDING -> current == PttState.PTT_PRESSED || current == PttState.IDLE
            PttState.SPEECH_DETECTED -> current == PttState.RECORDING || current == PttState.PTT_PRESSED
            PttState.STT_PROCESSING -> current == PttState.SPEECH_DETECTED || current == PttState.RECORDING
            PttState.MESSAGE_ENCODED -> current == PttState.STT_PROCESSING
            PttState.TRANSMITTING -> current == PttState.MESSAGE_ENCODED
            PttState.RECEIVED -> true
            PttState.TTS_PROCESSING -> current == PttState.RECEIVED
            PttState.PLAYING -> current == PttState.TTS_PROCESSING
            PttState.ERROR -> true
        }

        if (isValid) {
            _state.value = nextState
            onStateChanged?.invoke(nextState)
        }
        return isValid
    }

    fun reset() {
        _state.value = PttState.IDLE
        onStateChanged?.invoke(PttState.IDLE)
    }
}
