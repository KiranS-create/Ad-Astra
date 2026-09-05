package org.sih.itantra.core.session

/**
 * 10-state lifecycle machine for iTantra Push-To-Talk and continuous speech transceiver.
 */
enum class PttState(val displayName: String) {
    IDLE("Ready"),
    PTT_PRESSED("PTT Engaged"),
    RECORDING("Listening…"),
    SPEECH_DETECTED("Speech Active"),
    STT_PROCESSING("Transcribing…"),
    MESSAGE_ENCODED("Encoding Packet"),
    TRANSMITTING("Transmitting…"),
    RECEIVED("Packet Received"),
    TTS_PROCESSING("Synthesizing…"),
    PLAYING("Playing Speech"),
    ERROR("Error")
}
