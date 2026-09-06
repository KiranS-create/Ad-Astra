package org.sih.itantra.core.tts

import kotlinx.coroutines.flow.StateFlow
import org.sih.itantra.core.common.IndicLanguage

interface TextSynthesizer {
    val ttsState: StateFlow<TtsState>
    suspend fun synthesize(text: String, language: IndicLanguage, isUrgent: Boolean = false): Boolean
    fun stop()
    fun prepareLanguage(language: IndicLanguage) {}
    fun isReadyForLanguage(language: IndicLanguage): Boolean = true
    suspend fun awaitReady(language: IndicLanguage, timeoutMs: Long = 15000L): Boolean = true
    fun release()
}
