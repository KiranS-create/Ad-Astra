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
    private var tts: OfflineTts? = null
    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()
    private val alertToneGenerator = AlertToneGenerator()
    private var isInitialized = false

    init {
        initEngine()
    }

    @Synchronized
    fun initEngine(): Boolean {
        if (isInitialized && tts != null) return true

        return try {
            if (!modelAssetManager.isHindiTtsReady()) {
                Log.w(tag, "VITS Piper model files not ready on disk yet")
                return false
            }

            val vitsConfig = OfflineTtsVitsModelConfig().apply {
                model = modelAssetManager.vitsModelFile.absolutePath
                tokens = modelAssetManager.vitsTokensFile.absolutePath
                dataDir = modelAssetManager.vitsDataDir.absolutePath
                noiseScale = 0.667f
                noiseScaleW = 0.8f
                lengthScale = 1.0f
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

            tts = OfflineTts(assetManager = null, config = ttsConfig)
            isInitialized = true
            Log.i(tag, "Sherpa-ONNX VITS Piper TTS initialized successfully for Hindi! SampleRate: ${tts?.sampleRate()}")
            true
        } catch (e: Throwable) {
            Log.e(tag, "Failed to initialize SherpaOnnxTtsEngine", e)
            tts = null
            isInitialized = false
            false
        }
    }

    override suspend fun synthesize(text: String, language: IndicLanguage, isUrgent: Boolean): Boolean =
        withContext(dispatcher) {
            if (tts == null) {
                initEngine()
            }

            val currentTts = tts
            if (currentTts == null || text.isBlank()) {
                Log.w(tag, "TTS engine not ready or text blank")
                return@withContext false
            }

            try {
                if (isUrgent) {
                    alertToneGenerator.playAlertTone()
                }

                _ttsState.value = TtsState.SYNTHESIZING
                Log.i(tag, "Synthesizing Hindi text: '$text'")

                val audio: GeneratedAudio = currentTts.generate(text = text, sid = 0, speed = 1.0f)
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
                Log.e(tag, "Sherpa-ONNX TTS synthesis error", e)
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
        try {
            tts?.release()
        } catch (e: Throwable) {
            Log.w(tag, "Error releasing TTS", e)
        }
        tts = null
        isInitialized = false
    }
}
