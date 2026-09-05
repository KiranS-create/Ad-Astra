package org.sih.itantra.core.vad

/**
 * Lifecycle states of Voice Activity Detection (VAD).
 */
enum class VadState {
    IDLE,
    SPEECH_START,
    SPEECH_ACTIVE,
    SPEECH_END
}
