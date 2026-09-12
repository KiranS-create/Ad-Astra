package org.sih.itantra.core.tts

/**
 * Lifecycle states for language-aware TTS playback of a specific message.
 */
enum class TtsPlaybackStatus {
    IDLE,
    LOADING_VOICE,
    SYNTHESIZING,
    PLAYING,
    COMPLETED,
    UNAVAILABLE,
    FAILED
}

/**
 * Immutable state tracking the active voice playback in the chat / transceiver UI.
 */
data class MessagePlaybackState(
    val messageId: String? = null,
    val status: TtsPlaybackStatus = TtsPlaybackStatus.IDLE,
    val language: TtsLanguage = TtsLanguage.UNKNOWN,
    val error: String? = null
) {
    val isActive: Boolean
        get() = status == TtsPlaybackStatus.LOADING_VOICE ||
                status == TtsPlaybackStatus.SYNTHESIZING ||
                status == TtsPlaybackStatus.PLAYING

    val isPlaying: Boolean
        get() = status == TtsPlaybackStatus.PLAYING

    val isFailed: Boolean
        get() = status == TtsPlaybackStatus.UNAVAILABLE || status == TtsPlaybackStatus.FAILED

    companion object {
        val IDLE = MessagePlaybackState()
    }
}
