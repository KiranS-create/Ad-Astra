package org.sih.itantra.ml.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.stt.SentenceFinalizer
import org.sih.itantra.core.stt.SpeechRecognizer
import org.sih.itantra.core.stt.SpeechResult
import org.sih.itantra.ml.model.ModelAssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real On-Device Neural Speech Recognizer powered by Sherpa-ONNX Whisper-Tiny (quantized INT8).
 * Executes genuine offline inference without cloud APIs or network requests.
 */
class SherpaOnnxSpeechRecognizer(
    private val context: Context,
    val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SpeechRecognizer {

    private val tag = "SherpaOnnxSTT"
    private var recognizer: OfflineRecognizer? = null
    private val _results = MutableSharedFlow<SpeechResult>(replay = 1, extraBufferCapacity = 16)
    override val results: SharedFlow<SpeechResult> = _results.asSharedFlow()
    private val isListening = AtomicBoolean(false)
    private var isInitialized = false

    init {
        initEngine()
    }

    @Synchronized
    fun initEngine(): Boolean {
        if (isInitialized && recognizer != null) return true

        return try {
            if (!modelAssetManager.isHindiSttReady()) {
                Log.w(tag, "Whisper model files not ready on disk yet")
                return false
            }

            val whisperConfig = OfflineWhisperModelConfig().apply {
                encoder = modelAssetManager.whisperEncoderFile.absolutePath
                decoder = modelAssetManager.whisperDecoderFile.absolutePath
                language = "hi"
                task = "transcribe"
                tailPaddings = -1
            }

            val modelConfig = OfflineModelConfig().apply {
                whisper = whisperConfig
                tokens = modelAssetManager.whisperTokensFile.absolutePath
                numThreads = 2
                debug = false
                provider = "cpu"
            }

            val featConfig = FeatureConfig().apply {
                sampleRate = 16000
                featureDim = 80
            }

            val config = OfflineRecognizerConfig().apply {
                this.featConfig = featConfig
                this.modelConfig = modelConfig
                this.decodingMethod = "greedy_search"
            }

            recognizer = OfflineRecognizer(assetManager = null, config = config)
            isInitialized = true
            Log.i(tag, "Sherpa-ONNX Whisper STT initialized successfully for Hindi!")
            true
        } catch (e: Throwable) {
            Log.e(tag, "Failed to initialize SherpaOnnxSpeechRecognizer", e)
            recognizer = null
            isInitialized = false
            false
        }
    }

    override suspend fun processAudioSegment(pcmBytes: ByteArray, language: IndicLanguage): SpeechResult =
        withContext(dispatcher) {
            if (recognizer == null) {
                initEngine()
            }

            val currentRecognizer = recognizer
            if (currentRecognizer == null || pcmBytes.isEmpty()) {
                Log.w(tag, "Recognizer not initialized or audio empty")
                return@withContext SpeechResult(text = "", isFinal = true, language = language)
            }

            try {
                // Convert 16-bit Mono PCM bytes (16kHz, little-endian) to FloatArray normalized [-1.0, 1.0]
                val numSamples = pcmBytes.size / 2
                val samples = FloatArray(numSamples)
                val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                for (i in 0 until numSamples) {
                    samples[i] = buffer.get(i) / 32768.0f
                }

                val stream = currentRecognizer.createStream()
                stream.acceptWaveform(samples, 16000)
                currentRecognizer.decode(stream)
                val rawResult = currentRecognizer.getResult(stream)
                val rawText = rawResult.text.trim()
                stream.release()

                val finalText = SentenceFinalizer.finalizeSentence(rawText, language)
                val result = SpeechResult(
                    text = finalText,
                    isFinal = true,
                    language = language
                )
                _results.tryEmit(result)
                Log.i(tag, "Sherpa-ONNX Hindi STT Decoded: '$finalText' from ${samples.size} samples")
                result
            } catch (e: Throwable) {
                Log.e(tag, "Sherpa-ONNX Hindi STT decoding error", e)
                SpeechResult(text = "", isFinal = true, language = language)
            }
        }

    override fun startListening(language: IndicLanguage) {
        isListening.set(true)
    }

    override fun stopListening() {
        isListening.set(false)
    }

    override fun release() {
        try {
            recognizer?.release()
        } catch (e: Throwable) {
            Log.w(tag, "Error releasing recognizer", e)
        }
        recognizer = null
        isInitialized = false
    }
}
