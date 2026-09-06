package org.sih.itantra.ml.stt

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.OfflineSpeechRecognizer
import org.sih.itantra.core.stt.SpeechRecognizer
import org.sih.itantra.core.stt.SpeechResult
import org.sih.itantra.ml.model.ModelAssetManager

/**
 * High-performance neural STT router.
 * Routes Hindi to local onnx neural SherpaOnnxSpeechRecognizer as primary,
 * and seamlessly provides fallback to Android system speech recognizer.
 */
class NeuralSpeechRouter(
    private val context: Context,
    val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
    val sherpaStt: SherpaOnnxSpeechRecognizer = SherpaOnnxSpeechRecognizer(context, modelAssetManager),
    val platformStt: OfflineSpeechRecognizer = OfflineSpeechRecognizer(context)
) : SpeechRecognizer {

    private val _results = MutableSharedFlow<SpeechResult>(replay = 1, extraBufferCapacity = 16)
    override val results: SharedFlow<SpeechResult> = _results.asSharedFlow()

    private fun isNeuralSttSupported(language: IndicLanguage): Boolean {
        return when (language) {
            IndicLanguage.HINDI -> modelAssetManager.isHindiSttReady()
            IndicLanguage.GUJARATI -> modelAssetManager.isGujaratiSttReady()
            IndicLanguage.MARATHI -> modelAssetManager.isMarathiSttReady()
            IndicLanguage.KANNADA -> modelAssetManager.isKannadaSttReady()
            IndicLanguage.MALAYALAM -> modelAssetManager.isMalayalamSttReady()
            IndicLanguage.TAMIL -> modelAssetManager.isTamilSttReady()
            IndicLanguage.TELUGU -> modelAssetManager.isTeluguSttReady()
            IndicLanguage.ODIA -> modelAssetManager.isOdiaSttReady()
            IndicLanguage.BENGALI -> modelAssetManager.isBengaliSttReady()
            IndicLanguage.ENGLISH -> modelAssetManager.isEnglishSttReady()
        }
    }

    override suspend fun processAudioSegment(pcmBytes: ByteArray, language: IndicLanguage): SpeechResult {
        return if (isNeuralSttSupported(language)) {
            val result = sherpaStt.processAudioSegment(pcmBytes, language)
            if (result.text.isNotBlank()) {
                _results.tryEmit(result)
                result
            } else {
                val fbResult = platformStt.processAudioSegment(pcmBytes, language)
                _results.tryEmit(fbResult)
                fbResult
            }
        } else {
            val result = platformStt.processAudioSegment(pcmBytes, language)
            _results.tryEmit(result)
            result
        }
    }

    override fun startListening(language: IndicLanguage) {
        if (isNeuralSttSupported(language)) {
            sherpaStt.startListening(language)
        } else {
            platformStt.startListening(language)
        }
    }

    override fun stopListening() {
        sherpaStt.stopListening()
        platformStt.stopListening()
    }

    override fun prepareLanguage(language: IndicLanguage) {
        if (isNeuralSttSupported(language)) {
            sherpaStt.initEngine(language)
        }
    }

    override fun isReadyForLanguage(language: IndicLanguage): Boolean {
        return if (isNeuralSttSupported(language)) {
            sherpaStt.isReadyForLanguage(language)
        } else {
            true
        }
    }

    override suspend fun awaitReady(language: IndicLanguage, timeoutMs: Long): Boolean {
        return if (isNeuralSttSupported(language)) {
            sherpaStt.awaitReady(language, timeoutMs)
        } else {
            true
        }
    }

    override fun release() {
        sherpaStt.release()
        platformStt.release()
    }
}
