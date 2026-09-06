package org.sih.itantra.core.stt

import kotlinx.coroutines.flow.SharedFlow
import org.sih.itantra.core.common.IndicLanguage

interface SpeechRecognizer {
    val results: SharedFlow<SpeechResult>
    suspend fun processAudioSegment(pcmBytes: ByteArray, language: IndicLanguage): SpeechResult
    fun startListening(language: IndicLanguage)
    fun stopListening()
    fun prepareLanguage(language: IndicLanguage) {}
    fun release()
}
