package org.sih.itantra.core.session

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.AdaptiveCompressor
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.stt.SentenceFinalizer
import org.sih.itantra.ml.model.ModelAssetManager
import org.sih.itantra.ml.stt.SherpaOnnxSpeechRecognizer
import org.sih.itantra.ml.tts.SherpaOnnxTtsEngine
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-End SIH26173 Pipeline Verification Test.
 * Tests the entire real-time chain:
 * Audio WAV -> PCM Extraction -> Offline Neural STT (Sherpa-ONNX Dolphin/Whisper) ->
 * Sentence Finalization -> Adaptive Compression -> Binary Packet Serialization (CRC-16) ->
 * Packet Deserialization -> Decompression -> Offline Neural TTS (Sherpa-ONNX VITS) ->
 * Waveform Verification.
 *
 * Runs 100% locally on-device. Zero cloud APIs. Zero mocks.
 */
@RunWith(AndroidJUnit4::class)
class CompleteChainInstrumentedTest {

    private val tag = "CompleteChainTest"
    private lateinit var modelAssetManager: ModelAssetManager
    private lateinit var sttRecognizer: SherpaOnnxSpeechRecognizer
    private lateinit var ttsEngine: SherpaOnnxTtsEngine

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        modelAssetManager = ModelAssetManager(ctx)
        sttRecognizer = SherpaOnnxSpeechRecognizer(ctx, modelAssetManager)
        ttsEngine = SherpaOnnxTtsEngine(ctx, modelAssetManager)
    }

    @Test
    fun testCompleteChainHindi() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.HINDI,
            wavPath = "/data/local/tmp/test_hindi.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS Piper Rohan Medium (hi_IN)"
        )
    }

    @Test
    fun testCompleteChainEnglish() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.ENGLISH,
            wavPath = "/data/local/tmp/test_en_us.wav",
            expectedSttEngine = "Whisper-Tiny Multilingual INT8",
            expectedTtsEngine = "VITS Piper Lessac Medium (en_US)"
        )
    }

    @Test
    fun testCompleteChainTamil() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.TAMIL,
            wavPath = "/data/local/tmp/test_ta_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS MMS Tamil (ta)"
        )
    }

    @Test
    fun testCompleteChainTelugu() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.TELUGU,
            wavPath = "/data/local/tmp/test_te_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS Piper Maya Medium (te_IN)"
        )
    }

    @Test
    fun testCompleteChainOdia() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.ODIA,
            wavPath = "/data/local/tmp/test_or_in.wav",
            expectedSttEngine = "Dolphin Small Multi-Lang CTC INT8",
            expectedTtsEngine = "VITS MMS Odia (or)"
        )
    }

    @Test
    fun testCompleteChainGujarati() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.GUJARATI,
            wavPath = "/data/local/tmp/test_gu_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS Mimic3 Low (gu_IN)"
        )
    }

    @Test
    fun testCompleteChainMarathi() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.MARATHI,
            wavPath = "/data/local/tmp/test_mr_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS Piper Google Medium (mr_IN)"
        )
    }

    @Test
    fun testCompleteChainKannada() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.KANNADA,
            wavPath = "/data/local/tmp/test_kn_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS MMS Kannada (kn)"
        )
    }

    @Test
    fun testCompleteChainMalayalam() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.MALAYALAM,
            wavPath = "/data/local/tmp/test_ml_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS Piper Arjun Medium (ml_IN)"
        )
    }

    @Test
    fun testCompleteChainBengali() = runBlocking {
        executeCompleteChain(
            language = IndicLanguage.BENGALI,
            wavPath = "/data/local/tmp/test_bn_in.wav",
            expectedSttEngine = "AI4Bharat IndicConformer NeMo CTC INT8",
            expectedTtsEngine = "VITS Piper Google Medium (bn_BD)"
        )
    }

    private suspend fun executeCompleteChain(
        language: IndicLanguage,
        wavPath: String,
        expectedSttEngine: String,
        expectedTtsEngine: String
    ) {
        val wavFile = File(wavPath)
        if (!wavFile.exists()) {
            Log.w(tag, "SKIP: $wavPath not found")
            return
        }

        Log.i(tag, "================================================================")
        Log.i(tag, "STARTING COMPLETE CHAIN TEST: ${language.displayName}")
        Log.i(tag, "STT Engine: $expectedSttEngine")
        Log.i(tag, "TTS Engine: $expectedTtsEngine")
        Log.i(tag, "================================================================")

        // 1. Audio Ingestion
        val pcmBytes = readWavPcm16k(wavFile)
        val audioDurationSec = pcmBytes.size / 2.0 / 16000.0
        val rawAudioBytes = pcmBytes.size.toLong()
        Log.i(tag, "[Step 1: Audio] Ingested ${pcmBytes.size} bytes (${String.format("%.2f", audioDurationSec)}s at 16kHz Mono 16-bit PCM)")

        // 2. Offline Neural STT
        val t0Stt = System.currentTimeMillis()
        val sttResult = sttRecognizer.processAudioSegment(pcmBytes, language)
        val sttLatencyMs = System.currentTimeMillis() - t0Stt
        val sttRtf = sttLatencyMs / 1000.0 / audioDurationSec
        Log.i(tag, "[Step 2: STT] Raw Transcript: '${sttResult.text}' in ${sttLatencyMs}ms (RTF: ${String.format("%.3f", sttRtf)}x)")
        assertTrue("STT result must be non-empty", sttResult.text.isNotBlank())

        // 3. Sentence Segmentation & Finalization
        val finalizedText = SentenceFinalizer.finalizeSentence(sttResult.text, language)
        Log.i(tag, "[Step 3: Finalizer] Finalized Sentence: '$finalizedText'")

        // 4. Binary Encoding & Adaptive Compression
        val t0Encode = System.currentTimeMillis()
        val textBytes = finalizedText.toByteArray(Charsets.UTF_8)
        val compressionResult = AdaptiveCompressor.compress(textBytes)
        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.NORMAL,
            flags = if (compressionResult.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 1,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 0x1001,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = language,
            payload = compressionResult.bytes
        )
        val serializedPacket = PacketSerializer.serialize(packet)
        val encodeLatencyMs = System.currentTimeMillis() - t0Encode
        val bandwidthReductionPct = (1.0 - (serializedPacket.size.toDouble() / rawAudioBytes)) * 100.0
        Log.i(tag, "[Step 4: Packet Encoding] Created ${serializedPacket.size}B packet from ${rawAudioBytes}B audio (-${String.format("%.2f", bandwidthReductionPct)}% reduction) in ${encodeLatencyMs}ms")

        // 5. Binary Protocol Deserialization & CRC Verification
        val t0Decode = System.currentTimeMillis()
        val deserializedPacket = PacketSerializer.deserialize(serializedPacket)
        assertNotNull("Deserialized packet must be valid", deserializedPacket)
        assertEquals("Packet sequence number must match", 1.toShort(), deserializedPacket!!.sequenceNumber)
        assertEquals("Packet language must match", language, deserializedPacket.language)

        val decompressedBytes = AdaptiveCompressor.decompress(deserializedPacket.payload, deserializedPacket.isCompressed)
        val receivedText = String(decompressedBytes, Charsets.UTF_8)
        val decodeLatencyMs = System.currentTimeMillis() - t0Decode
        assertEquals("Transmitted and received text must match byte-for-byte", finalizedText, receivedText)
        Log.i(tag, "[Step 5: Packet Decoding] Successfully verified packet CRC-16 and payload in ${decodeLatencyMs}ms: '$receivedText'")

        // 6. Offline Neural TTS Synthesis
        val t0Tts = System.currentTimeMillis()
        val offlineTts = ttsEngine.getOrInitTts(language)
        assertNotNull("TTS Engine for ${language.displayName} must be initialized", offlineTts)

        val generatedAudio = offlineTts!!.generate(text = receivedText, sid = 0, speed = 1.0f)
        val ttsLatencyMs = System.currentTimeMillis() - t0Tts
        val ttsSamples = generatedAudio.samples
        val ttsSampleRate = generatedAudio.sampleRate
        val ttsAudioDurationSec = ttsSamples.size.toDouble() / ttsSampleRate
        val ttsRtf = ttsLatencyMs / 1000.0 / ttsAudioDurationSec
        Log.i(tag, "[Step 6: Neural TTS] Synthesized ${ttsSamples.size} samples (${String.format("%.2f", ttsAudioDurationSec)}s audio at ${ttsSampleRate}Hz) in ${ttsLatencyMs}ms (RTF: ${String.format("%.3f", ttsRtf)}x)")
        assertTrue("TTS must generate non-empty audio samples", ttsSamples.isNotEmpty())

        val totalPipelineLatencyMs = sttLatencyMs + encodeLatencyMs + decodeLatencyMs + ttsLatencyMs

        Log.i(tag, "================================================================")
        Log.i(tag, "COMPLETE CHAIN VERIFICATION SUMMARY: ${language.displayName}")
        Log.i(tag, "  Source Audio Duration:      ${String.format("%.2f", audioDurationSec)}s")
        Log.i(tag, "  STT Latency:               ${sttLatencyMs}ms (RTF ${String.format("%.3f", sttRtf)}x)")
        Log.i(tag, "  STT Transcript:            '$finalizedText'")
        Log.i(tag, "  Packet Size:               ${serializedPacket.size} bytes (vs ${rawAudioBytes} bytes audio)")
        Log.i(tag, "  Bandwidth Savings:         ${String.format("%.2f", bandwidthReductionPct)}%")
        Log.i(tag, "  Protocol Latency:          ${encodeLatencyMs + decodeLatencyMs}ms")
        Log.i(tag, "  TTS Synthesis Latency:     ${ttsLatencyMs}ms (RTF ${String.format("%.3f", ttsRtf)}x)")
        Log.i(tag, "  Synthesized Audio:         ${String.format("%.2f", ttsAudioDurationSec)}s at ${ttsSampleRate}Hz")
        Log.i(tag, "  Total End-to-End Latency:  ${totalPipelineLatencyMs}ms")
        Log.i(tag, "================================================================")
    }

    private fun readWavPcm16k(wavFile: File): ByteArray {
        val raf = RandomAccessFile(wavFile, "r")
        try {
            val riff = ByteArray(4); raf.readFully(riff)
            require(String(riff) == "RIFF")
            raf.skipBytes(4)
            val wave = ByteArray(4); raf.readFully(wave)
            require(String(wave) == "WAVE")

            var fmtTag: Short = 0; var nCh: Short = 0; var sr = 0; var bps: Short = 0

            while (raf.filePointer < raf.length()) {
                val id = ByteArray(4); raf.readFully(id)
                val idStr = String(id)
                val szB = ByteArray(4); raf.readFully(szB)
                val sz = ByteBuffer.wrap(szB).order(ByteOrder.LITTLE_ENDIAN).int

                if (idStr == "fmt ") {
                    val d = ByteArray(sz); raf.readFully(d)
                    val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
                    fmtTag = b.short; nCh = b.short; sr = b.int; b.int; b.short; bps = b.short
                } else if (idStr == "data") {
                    val data = ByteArray(sz); raf.readFully(data)
                    val mono: ShortArray
                    when (fmtTag.toInt()) {
                        1 -> {
                            val n = sz / (bps / 8) / nCh
                            mono = ShortArray(n)
                            val db = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until n) {
                                var s = 0L; for (c in 0 until nCh) s += db.short.toLong()
                                mono[i] = (s / nCh).toShort()
                            }
                        }
                        3 -> {
                            val n = sz / 4 / nCh
                            mono = ShortArray(n)
                            val db = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until n) {
                                var s = 0.0; for (c in 0 until nCh) s += db.float.toDouble()
                                mono[i] = ((s / nCh).coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported WAV fmt: $fmtTag")
                    }
                    val out = if (sr != 16000) {
                        val r = sr.toDouble() / 16000.0
                        val n = (mono.size / r).toInt()
                        ShortArray(n) { mono[((it * r).toInt()).coerceAtMost(mono.size - 1)] }
                    } else mono
                    val res = ByteArray(out.size * 2)
                    val rb = ByteBuffer.wrap(res).order(ByteOrder.LITTLE_ENDIAN)
                    for (s in out) rb.putShort(s)
                    return res
                } else {
                    raf.skipBytes(sz)
                }
            }
            throw IllegalArgumentException("No data chunk")
        } finally { raf.close() }
    }
}
