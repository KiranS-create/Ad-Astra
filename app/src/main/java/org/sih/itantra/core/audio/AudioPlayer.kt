package org.sih.itantra.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

interface AudioPlayer {
    val isPlaying: Boolean
    fun playPcm(pcmBytes: ByteArray, isUrgent: Boolean = false)
    fun playPcm(pcmBytes: ByteArray, sampleRate: Int, isUrgent: Boolean = false)
    fun stopPlayback()
    fun release()
}

class AndroidAudioPlayer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioPlayer {

    private val tag = "AndroidAudioPlayer"
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val _isPlaying = AtomicBoolean(false)

    override val isPlaying: Boolean
        get() = _isPlaying.get()

    override fun playPcm(pcmBytes: ByteArray, isUrgent: Boolean) {
        playPcm(pcmBytes, AudioFormatConfig.SAMPLE_RATE_HZ, isUrgent)
    }

    override fun playPcm(pcmBytes: ByteArray, sampleRate: Int, isUrgent: Boolean) {
        if (pcmBytes.isEmpty()) return

        // Urgent alerts interrupt currently playing audio
        if (isUrgent && _isPlaying.get()) {
            stopPlayback()
        }

        playJob?.cancel()
        playJob = CoroutineScope(dispatcher).launch {
            try {
                _isPlaying.set(true)
                val bufferSize = maxOf(
                    pcmBytes.size,
                    AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormatConfig.CHANNEL_CONFIG_OUT,
                        AudioFormatConfig.AUDIO_FORMAT
                    )
                )

                val usage = if (isUrgent) {
                    AudioAttributes.USAGE_ALARM
                } else {
                    AudioAttributes.USAGE_MEDIA
                }

                val attributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormatConfig.CHANNEL_CONFIG_OUT)
                    .setEncoding(AudioFormatConfig.AUDIO_FORMAT)
                    .build()

                audioTrack = AudioTrack(
                    attributes,
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack?.play()
                var offset = 0
                val chunkSize = 2048
                while (offset < pcmBytes.size && _isPlaying.get()) {
                    val bytesToWrite = minOf(chunkSize, pcmBytes.size - offset)
                    val written = audioTrack?.write(pcmBytes, offset, bytesToWrite) ?: -1
                    if (written < 0) break
                    offset += written
                }

                // Wait for playback buffer to drain
                audioTrack?.stop()
            } catch (e: Exception) {
                Log.e(tag, "PCM playback error", e)
            } finally {
                try {
                    audioTrack?.release()
                } catch (_: Exception) {}
                audioTrack = null
                _isPlaying.set(false)
            }
        }
    }

    override fun stopPlayback() {
        _isPlaying.set(false)
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    override fun release() {
        stopPlayback()
    }
}
