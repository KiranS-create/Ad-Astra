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
    private val recognizers = java.util.concurrent.ConcurrentHashMap<IndicLanguage, OfflineRecognizer>()
    private val _results = MutableSharedFlow<SpeechResult>(replay = 1, extraBufferCapacity = 16)
    override val results: SharedFlow<SpeechResult> = _results.asSharedFlow()
    private val isListening = AtomicBoolean(false)
    private var isInitialized = false

    init {
        initEngine(IndicLanguage.HINDI)
    }

    @Synchronized
    fun getOrInitRecognizer(language: IndicLanguage): OfflineRecognizer? {
        recognizers[language]?.let { return it }

        if (!modelAssetManager.isWhisperSttReady()) {
            Log.w(tag, "Whisper model files not ready on disk yet")
            return null
        }

        return try {
            val whisperConfig = OfflineWhisperModelConfig().apply {
                encoder = modelAssetManager.whisperEncoderFile.absolutePath
                decoder = modelAssetManager.whisperDecoderFile.absolutePath
                this.language = language.isoCode
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

            val rec = OfflineRecognizer(assetManager = null, config = config)
            recognizers[language] = rec
            isInitialized = true
            Log.i(tag, "Sherpa-ONNX Whisper STT initialized successfully for ${language.displayName} (${language.isoCode})!")
            rec
        } catch (e: Throwable) {
            Log.e(tag, "Failed to initialize SherpaOnnxSpeechRecognizer for ${language.displayName}", e)
            null
        }
    }

    @Synchronized
    fun initEngine(language: IndicLanguage = IndicLanguage.HINDI): Boolean {
        return getOrInitRecognizer(language) != null
    }

    override suspend fun processAudioSegment(pcmBytes: ByteArray, language: IndicLanguage): SpeechResult =
        withContext(dispatcher) {
            val currentRecognizer = getOrInitRecognizer(language) ?: getOrInitRecognizer(IndicLanguage.HINDI)
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
                Log.i(tag, "Sherpa-ONNX ${language.displayName} STT Decoded: '$finalText' from ${samples.size} samples")
                result
            } catch (e: Throwable) {
                Log.e(tag, "Sherpa-ONNX ${language.displayName} STT decoding error", e)
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
        for ((_, rec) in recognizers) {
            try {
                rec.release()
            } catch (e: Throwable) {
                Log.w(tag, "Error releasing recognizer", e)
            }
        }
        recognizers.clear()
        isInitialized = false
    }
}
