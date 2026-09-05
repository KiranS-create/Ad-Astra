package org.sih.itantra.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.sih.itantra.core.audio.AudioFormatConfig
import kotlin.math.sin

/**
 * Direct mathematical PCM synthesizer for emergency warning alarms and distress tones.
 * Provides instant (<5ms) audible alert response without waiting for neural models to load.
 */
class AlertToneGenerator {

    fun playAlertTone(durationMs: Int = 400, highFreqHz: Double = 1200.0, lowFreqHz: Double = 800.0) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sampleRate = AudioFormatConfig.SAMPLE_RATE_HZ
                val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val pcmData = ByteArray(totalSamples * 2)

                val periodSamples = sampleRate / 10 // Toggle frequency every 100ms
                for (i in 0 until totalSamples) {
                    val currentFreq = if ((i / periodSamples) % 2 == 0) highFreqHz else lowFreqHz
                    val angle = 2.0 * Math.PI * i * currentFreq / sampleRate
                    val sample = (sin(angle) * 28000.0).toInt().toShort()

                    pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
                    pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                val track = AudioTrack(
                    attributes,
                    format,
                    pcmData.size,
                    AudioTrack.MODE_STATIC,
                    android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                track.write(pcmData, 0, pcmData.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 50L)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }
}
