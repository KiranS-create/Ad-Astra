package org.sih.itantra.ml.tts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.tts.OfflineTtsEngine
import org.sih.itantra.core.tts.TextSynthesizer
import org.sih.itantra.core.tts.TtsState
import org.sih.itantra.ml.model.ModelAssetManager

/**
 * High-performance neural TTS router.
 * Routes Hindi to local onnx neural SherpaOnnxTtsEngine as primary,
 * and seamlessly provides fallback to Android system TextToSpeech.
 */
class NeuralTtsRouter(
    private val context: Context,
    val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
    val sherpaTts: SherpaOnnxTtsEngine = SherpaOnnxTtsEngine(context, modelAssetManager),
    val platformTts: OfflineTtsEngine = OfflineTtsEngine(context)
) : TextSynthesizer {

    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private fun isNeuralTtsSupported(language: IndicLanguage): Boolean {
        return when (language) {
            IndicLanguage.HINDI -> modelAssetManager.isHindiTtsReady()
            IndicLanguage.GUJARATI -> modelAssetManager.isGujaratiTtsReady()
            else -> false
        }
    }

    override suspend fun synthesize(text: String, language: IndicLanguage, isUrgent: Boolean): Boolean {
        return if (isNeuralTtsSupported(language)) {
            _ttsState.value = TtsState.SYNTHESIZING
            val success = sherpaTts.synthesize(text, language, isUrgent)
            _ttsState.value = sherpaTts.ttsState.value
            if (!success) {
                // Fallback to platform if neural synthesis failed
                platformTts.synthesize(text, language, isUrgent)
            } else {
                true
            }
        } else {
            platformTts.synthesize(text, language, isUrgent)
        }
    }

    override fun stop() {
        sherpaTts.stop()
        platformTts.stop()
        _ttsState.value = TtsState.IDLE
    }

    override fun release() {
        sherpaTts.release()
        platformTts.release()
        _ttsState.value = TtsState.IDLE
    }
}
