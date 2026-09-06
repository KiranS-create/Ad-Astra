package org.sih.itantra.ml.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.sih.itantra.core.audio.AndroidAudioPlayer
import org.sih.itantra.core.audio.AudioPlayer
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.tts.AlertToneGenerator
import org.sih.itantra.core.tts.TextSynthesizer
import org.sih.itantra.core.tts.TtsState
import org.sih.itantra.ml.model.ModelAssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real On-Device Neural Text-to-Speech engine powered by Sherpa-ONNX VITS Piper Rohan (Hindi).
 * Generates natural 22.05 kHz voice audio completely offline.
 */
class SherpaOnnxTtsEngine(
    private val context: Context,
    val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
    private val player: AudioPlayer = AndroidAudioPlayer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TextSynthesizer {

    private val tag = "SherpaOnnxTTS"
    private val modelLock = Any()
    private var activeLanguage: IndicLanguage? = null
    private var activeTts: OfflineTts? = null

    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()
    private val alertToneGenerator = AlertToneGenerator()
    private var isInitialized = false

    private data class TtsModelSpec(
        val modelFile: java.io.File,
        val tokensFile: java.io.File,
        val noiseScale: Float = 0.667f,
        val noiseScaleW: Float = 0.8f,
        val lengthScale: Float = 1.0f
    )

    fun getActiveLanguage(): IndicLanguage? = synchronized(modelLock) { activeLanguage }

    fun getOrInitTts(language: IndicLanguage): OfflineTts? = synchronized(modelLock) {
        getOrInitTtsLocked(language)
    }

    private fun getOrInitTtsLocked(language: IndicLanguage): OfflineTts? {
        if (activeLanguage == language && activeTts != null) {
            return activeTts
        }

        // Evict previous active model before initializing new one
        if (activeTts != null) {
            Log.i(tag, "Evicting previous TTS model (${activeLanguage?.displayName ?: "unknown"}) to maintain single-active resident model")
            try {
                activeTts?.release()
            } catch (e: Throwable) {
                Log.w(tag, "Error releasing previous TTS model", e)
            } finally {
                activeTts = null
                activeLanguage = null
            }
            System.gc()
        }

        val spec = when (language) {
            IndicLanguage.HINDI -> {
                if (!modelAssetManager.isHindiTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.vitsModelFile,
                    tokensFile = modelAssetManager.vitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.GUJARATI -> {
                if (!modelAssetManager.isGujaratiTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.guVitsModelFile,
                    tokensFile = modelAssetManager.guVitsTokensFile,
                    noiseScale = 0.333f,
                    noiseScaleW = 0.333f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.MARATHI -> {
                if (!modelAssetManager.isMarathiTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.mrVitsModelFile,
                    tokensFile = modelAssetManager.mrVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.KANNADA -> {
                if (!modelAssetManager.isKannadaTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.knVitsModelFile,
                    tokensFile = modelAssetManager.knVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.MALAYALAM -> {
                if (!modelAssetManager.isMalayalamTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.mlVitsModelFile,
                    tokensFile = modelAssetManager.mlVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.TAMIL -> {
                if (!modelAssetManager.isTamilTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.taVitsModelFile,
                    tokensFile = modelAssetManager.taVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.TELUGU -> {
                if (!modelAssetManager.isTeluguTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.teVitsModelFile,
                    tokensFile = modelAssetManager.teVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.ODIA -> {
                if (!modelAssetManager.isOdiaTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.orVitsModelFile,
                    tokensFile = modelAssetManager.orVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.BENGALI -> {
                if (!modelAssetManager.isBengaliTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.bnVitsModelFile,
                    tokensFile = modelAssetManager.bnVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            IndicLanguage.ENGLISH -> {
                if (!modelAssetManager.isEnglishTtsReady()) return null
                TtsModelSpec(
                    modelFile = modelAssetManager.enVitsModelFile,
                    tokensFile = modelAssetManager.enVitsTokensFile,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }
            else -> return null
        }

        return try {
            val vitsConfig = OfflineTtsVitsModelConfig().apply {
                model = spec.modelFile.absolutePath
                tokens = spec.tokensFile.absolutePath
                dataDir = modelAssetManager.sharedEspeakDataDir.absolutePath
                noiseScale = spec.noiseScale
                noiseScaleW = spec.noiseScaleW
                lengthScale = spec.lengthScale
            }

            val modelConfig = OfflineTtsModelConfig().apply {
                vits = vitsConfig
                numThreads = 2
                debug = false
                provider = "cpu"
            }

            val ttsConfig = OfflineTtsConfig().apply {
                this.model = modelConfig
                this.maxNumSentences = 1
                this.silenceScale = 0.2f
            }

            val engine = OfflineTts(assetManager = null, config = ttsConfig)
            activeTts = engine
            activeLanguage = language
            isInitialized = true
            Log.i(tag, "Sherpa-ONNX VITS TTS initialized successfully for ${language.displayName}! SampleRate: ${engine.sampleRate()} (single active model resident)")
            engine
        } catch (e: Throwable) {
            Log.e(tag, "Failed to initialize SherpaOnnxTtsEngine for ${language.displayName}", e)
            null
        }
    }

    fun initEngine(language: IndicLanguage = IndicLanguage.HINDI): Boolean = synchronized(modelLock) {
        val tts = getOrInitTtsLocked(language)
        if (tts != null) {
            Log.i(tag, "${language.displayName} TTS ready")
        }
        tts != null
    }

    override fun prepareLanguage(language: IndicLanguage) {
        initEngine(language)
    }

    override fun isReadyForLanguage(language: IndicLanguage): Boolean = synchronized(modelLock) {
        activeLanguage == language && activeTts != null
    }

    override suspend fun awaitReady(language: IndicLanguage, timeoutMs: Long): Boolean {
        if (isReadyForLanguage(language)) return true
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isReadyForLanguage(language)) return true
            delay(50)
        }
        return isReadyForLanguage(language)
    }

    override suspend fun synthesize(text: String, language: IndicLanguage, isUrgent: Boolean): Boolean =
        withContext(dispatcher) {
            if (text.isBlank()) return@withContext false

            try {
                if (isUrgent) {
                    alertToneGenerator.playAlertTone()
                }

                _ttsState.value = TtsState.SYNTHESIZING
                Log.i(tag, "Synthesizing ${language.displayName} text: '$text'")

                val audio: GeneratedAudio? = synchronized(modelLock) {
                    val currentTts = getOrInitTtsLocked(language) ?: getOrInitTtsLocked(IndicLanguage.HINDI)
                    if (currentTts == null) {
                        Log.w(tag, "TTS engine not ready for ${language.displayName}")
                        null
                    } else {
                        currentTts.generate(text = text, sid = 0, speed = 1.0f)
                    }
                }

                if (audio == null) {
                    _ttsState.value = TtsState.IDLE
                    return@withContext false
                }

                val samples = audio.samples
                if (samples.isEmpty()) {
                    Log.w(tag, "TTS generated 0 samples for text: '$text'")
                    _ttsState.value = TtsState.IDLE
                    return@withContext false
                }

                val sampleRate = audio.sampleRate
                Log.i(tag, "Generated ${samples.size} samples at ${sampleRate}Hz")

                // Convert FloatArray [-1.0, 1.0] to 16-bit PCM ByteArray
                val pcmBytes = ByteArray(samples.size * 2)
                val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) {
                    val clamped = s.coerceIn(-1.0f, 1.0f)
                    val shortVal = (clamped * 32767.0f).toInt().toShort()
                    buffer.putShort(shortVal)
                }

                _ttsState.value = TtsState.PLAYING
                player.playPcm(pcmBytes, sampleRate = sampleRate, isUrgent = isUrgent)

                // Wait for playback to finish
                while (player.isPlaying) {
                    delay(50)
                }

                _ttsState.value = TtsState.COMPLETED
                _ttsState.value = TtsState.IDLE
                true
            } catch (e: Throwable) {
                Log.e(tag, "Sherpa-ONNX ${language.displayName} TTS synthesis error", e)
                _ttsState.value = TtsState.ERROR
                false
            }
        }

    override fun stop() {
        player.stopPlayback()
        _ttsState.value = TtsState.IDLE
    }

    override fun release() {
        stop()
        synchronized(modelLock) {
            try {
                activeTts?.release()
            } catch (e: Throwable) {
                Log.w(tag, "Error releasing active TTS engine", e)
            } finally {
                activeTts = null
                activeLanguage = null
                isInitialized = false
            }
            System.gc()
        }
    }
}
