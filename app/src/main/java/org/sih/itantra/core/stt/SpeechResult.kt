package org.sih.itantra.core.stt

import org.sih.itantra.core.common.IndicLanguage

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val language: IndicLanguage,
    val latencyMs: Long = 0L,
    val audioDurationMs: Long = 0L
)
