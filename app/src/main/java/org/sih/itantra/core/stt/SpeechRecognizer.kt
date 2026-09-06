package org.sih.itantra.core.stt

import kotlinx.coroutines.flow.SharedFlow
import org.sih.itantra.core.common.IndicLanguage

interface SpeechRecognizer {
    val results: SharedFlow<SpeechResult>
    suspend fun processAudioSegment(pcmBytes: ByteArray, language: IndicLanguage): SpeechResult
    fun startListening(language: IndicLanguage)
    fun stopListening()
    fun prepareLanguage(language: IndicLanguage) {}
    fun isReadyForLanguage(language: IndicLanguage): Boolean = true
    suspend fun awaitReady(language: IndicLanguage, timeoutMs: Long = 15000L): Boolean = true
    fun release()
}
