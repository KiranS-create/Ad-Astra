package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.audio.AcousticFilterMode
import org.sih.itantra.core.audio.AcousticFrontEnd
import org.sih.itantra.core.audio.AudioMetrics
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AcousticFrontEndTest {

    private lateinit var afe: AcousticFrontEnd

    @Before
    fun setUp() {
        afe = AcousticFrontEnd(mode = AcousticFilterMode.TACTICAL_AGGRESSIVE)
    }

    private fun generateSinePcm(freqHz: Double, durationMs: Int, amplitude: Short, sampleRate: Int = 16000): ByteArray {
        val numSamples = (sampleRate * durationMs) / 1000
        val bytes = ByteArray(numSamples * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            val sample = (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toInt().toShort()
            buf.put(sample)
        }
        return bytes
    }

    @Test
    fun testBypassModeLeavesAudioUnaltered() {
        val afeBypass = AcousticFrontEnd(mode = AcousticFilterMode.BYPASS)
        val original = generateSinePcm(1000.0, 50, 10000)
        val processed = afeBypass.process(original)

        assertEquals(original.size, processed.size)
        assertTrue("Bypass mode must preserve identical PCM bytes", original.contentEquals(processed))
    }

    @Test
    fun testHighPassFilterAttenuatesLowFrequencyRumble() {
        // 50 Hz sub-vocal machinery/rotor thrum
        val rumblePcm = generateSinePcm(50.0, 100, 15000)
        val rumbleInRms = AudioMetrics.calculateRms(rumblePcm)

        val processed = afe.process(rumblePcm)
        val rumbleOutRms = AudioMetrics.calculateRms(processed)

        assertTrue(
            "HPF at 200 Hz must significantly attenuate 50 Hz rumble (in: $rumbleInRms, out: $rumbleOutRms)",
            rumbleOutRms < rumbleInRms * 0.4
        )
    }

    @Test
    fun testLowPassFilterAttenuatesHighFrequencyHiss() {
        // 5500 Hz high frequency static/squeal (above 3600 Hz cutoff)
        val hissPcm = generateSinePcm(5500.0, 100, 15000)
        val hissInRms = AudioMetrics.calculateRms(hissPcm)

        val processed = afe.process(hissPcm)
        val hissOutRms = AudioMetrics.calculateRms(processed)

        assertTrue(
            "LPF at 3600 Hz must attenuate 5500 Hz carrier hiss (in: $hissInRms, out: $hissOutRms)",
            hissOutRms < hissInRms * 0.6
        )
    }

    @Test
    fun testVoiceBandSpeechPreservation() {
        // 1000 Hz human vowel core formant at healthy speech level (RMS ~ 7000)
        val voicePcm = generateSinePcm(1000.0, 100, 10000)
        val voiceInRms = AudioMetrics.calculateRms(voicePcm)

        // Process a couple frames so gain follower opens
        afe.process(voicePcm)
        val processed = afe.process(voicePcm)
        val voiceOutRms = AudioMetrics.calculateRms(processed)

        assertTrue(
            "Human speech frequency in passband should retain robust signal energy (in: $voiceInRms, out: $voiceOutRms)",
            voiceOutRms > voiceInRms * 0.70
        )
    }

    @Test
    fun testNoiseExpanderSuppressionOnStationaryLowNoise() {
        // Very low amplitude ambient hiss (RMS ~ 150)
        val lowNoisePcm = generateSinePcm(1000.0, 100, 200)
        val noiseInRms = AudioMetrics.calculateRms(lowNoisePcm)

        val processed = afe.process(lowNoisePcm)
        val noiseOutRms = AudioMetrics.calculateRms(processed)

        assertTrue(
            "Downward expander should strongly attenuate ambient noise below speech threshold (in: $noiseInRms, out: $noiseOutRms)",
            noiseOutRms < noiseInRms * 0.5
        )
    }

    @Test
    fun testResetClearsInternalState() {
        val rumblePcm = generateSinePcm(50.0, 100, 15000)
        afe.process(rumblePcm)

        afe.reset()
        assertEquals(250.0, afe.currentNoiseFloor, 0.01)
        assertEquals(1.0f, afe.currentGain, 0.01f)
    }
}
