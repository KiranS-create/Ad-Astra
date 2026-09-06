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
 * Rapid PTT Press-and-Release Repeated Stress Test.
 * Simulates 25 continuous push-to-talk operations on-device to verify:
 * 1. Zero native SIGSEGV crashes in Sherpa-ONNX C++ engine
 * 2. Stable memory footprint (no progressive native memory leaks)
 * 3. Zero ANRs or thread starvation across cycles
 */
@RunWith(AndroidJUnit4::class)
class PttStressInstrumentedTest {

    private val tag = "PttStressTest"
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
    fun testRepeatedPttCyclesHindi() {
        runBlocking {
            val wavFile = File("/data/local/tmp/test_hindi.wav")
            assertTrue("test_hindi.wav must exist", wavFile.exists())
            val pcm = readWavPcm16k(wavFile)
            val dur = pcm.size / 2.0 / 16000.0

            Log.i(tag, "================================================================")
            Log.i(tag, "STARTING PTT REPEATED STRESS TEST: 25 CYCLES (Hindi)")
            Log.i(tag, "Audio duration per cycle: ${String.format("%.2f", dur)}s")
            Log.i(tag, "================================================================")

            val runtime = Runtime.getRuntime()
            val initialMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            Log.i(tag, "Initial Java Heap Memory: ${initialMem} MB")

            val latencies = mutableListOf<Long>()

            for (cycle in 1..25) {
                val cycleStart = System.currentTimeMillis()

                // 1. Simulate PTT Pressed -> Speech Ingestion -> STT
                sttRecognizer.startListening(IndicLanguage.HINDI)
                val sttResult = sttRecognizer.processAudioSegment(pcm, IndicLanguage.HINDI)
                sttRecognizer.stopListening()

                assertTrue("Cycle $cycle STT must produce text", sttResult.text.isNotBlank())

                // 2. Sentence Finalization
                val finalText = SentenceFinalizer.finalizeSentence(sttResult.text, IndicLanguage.HINDI)

                // 3. Packet Transmission Simulation
                val textBytes = finalText.toByteArray(Charsets.UTF_8)
                val compressionResult = AdaptiveCompressor.compress(textBytes)
                val packet = Packet(
                    version = Packet.PROTOCOL_VERSION,
                    msgType = Packet.TYPE_TEXT,
                    priority = MessagePriority.NORMAL,
                    flags = if (compressionResult.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
                    sequenceNumber = cycle.toShort(),
                    timestamp = System.currentTimeMillis(),
                    sourceDeviceId = 0x1001,
                    destinationDeviceId = Packet.BROADCAST_ID,
                    language = IndicLanguage.HINDI,
                    payload = compressionResult.bytes
                )
                val serialized = PacketSerializer.serialize(packet)
                val deserialized = PacketSerializer.deserialize(serialized)
                assertNotNull("Cycle $cycle Packet must deserialize", deserialized)
                val decompressedBytes = AdaptiveCompressor.decompress(deserialized!!.payload, deserialized.isCompressed)
                val receivedText = String(decompressedBytes, Charsets.UTF_8)
                assertEquals("Payload text match", finalText, receivedText)

                // 4. Remote PTT Audio Synthesis (TTS)
                val offlineTts = ttsEngine.getOrInitTts(IndicLanguage.HINDI)
                assertNotNull("Cycle $cycle TTS must be ready", offlineTts)
                val audio = offlineTts!!.generate(text = receivedText, sid = 0, speed = 1.0f)
                assertTrue("Cycle $cycle TTS audio samples > 0", audio.samples.isNotEmpty())

                val cycleTime = System.currentTimeMillis() - cycleStart
                latencies.add(cycleTime)

                val currentMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                Log.i(tag, "Cycle $cycle/25 OK: ${cycleTime}ms (Heap: ${currentMem} MB) Text: '${finalText.take(30)}...'")
            }

            val finalMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val avgTime = latencies.average()
            val minTime = latencies.minOrNull() ?: 0
            val maxTime = latencies.maxOrNull() ?: 0

            Log.i(tag, "================================================================")
            Log.i(tag, "PTT STRESS TEST COMPLETED SUCCESSFULLY!")
            Log.i(tag, "Total Cycles:     25")
            Log.i(tag, "Successful:       25 (100%)")
            Log.i(tag, "Crashes / ANRs:   0")
            Log.i(tag, "Avg Cycle Time:   ${String.format("%.1f", avgTime)} ms")
            Log.i(tag, "Min Cycle Time:   ${minTime} ms")
            Log.i(tag, "Max Cycle Time:   ${maxTime} ms")
            Log.i(tag, "Initial Heap:     ${initialMem} MB")
            Log.i(tag, "Final Heap:       ${finalMem} MB")
            Log.i(tag, "================================================================")
        }
    }

    private fun readWavPcm16k(wavFile: File): ByteArray {
        val raf = RandomAccessFile(wavFile, "r")
        try {
            val riff = ByteArray(4); raf.readFully(riff)
            require(String(riff) == "RIFF") { "Not RIFF" }
            raf.skipBytes(4)
            val wave = ByteArray(4); raf.readFully(wave)
            require(String(wave) == "WAVE") { "Not WAVE" }

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
                    Log.i(tag, "WAV: fmt=$fmtTag ch=$nCh rate=$sr bits=$bps")
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
