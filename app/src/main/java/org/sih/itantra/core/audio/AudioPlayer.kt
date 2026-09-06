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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class PlaybackTiming(
    val trackPrepStartNanos: Long,
    val trackPrepEndNanos: Long,
    val firstFrameWrittenNanos: Long,
    val playbackCompleteNanos: Long
)

interface AudioPlayer {
    val isPlaying: Boolean
    fun playPcm(pcmBytes: ByteArray, isUrgent: Boolean = false)
    fun playPcm(pcmBytes: ByteArray, sampleRate: Int, isUrgent: Boolean = false)
    suspend fun playPcmAwait(
        pcmBytes: ByteArray,
        sampleRate: Int,
        isUrgent: Boolean = false,
        onTiming: ((PlaybackTiming) -> Unit)? = null
    ): Boolean
    fun stopPlayback()
    fun release()
}

class AndroidAudioPlayer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioPlayer {

    private val tag = "AndroidAudioPlayer"
    private val trackLock = Any()
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 0
    private var currentIsUrgent: Boolean = false
    private var currentBufferSize: Int = 0
    private var playJob: Job? = null
    private val _isPlaying = AtomicBoolean(false)

    override val isPlaying: Boolean
        get() = _isPlaying.get()

    override fun playPcm(pcmBytes: ByteArray, isUrgent: Boolean) {
        playPcm(pcmBytes, AudioFormatConfig.SAMPLE_RATE_HZ, isUrgent)
    }

    override fun playPcm(pcmBytes: ByteArray, sampleRate: Int, isUrgent: Boolean) {
        CoroutineScope(dispatcher).launch {
            playPcmAwait(pcmBytes, sampleRate, isUrgent)
        }
    }

    override suspend fun playPcmAwait(
        pcmBytes: ByteArray,
        sampleRate: Int,
        isUrgent: Boolean,
        onTiming: ((PlaybackTiming) -> Unit)?
    ): Boolean = withContext(dispatcher) {
        if (pcmBytes.isEmpty()) return@withContext false

        val tTrackPrepStart = System.nanoTime()

        if (isUrgent && _isPlaying.get()) {
            stopPlayback()
        }

        _isPlaying.set(true)
        var tFirstFrameWritten = 0L
        var tTrackPrepEnd = 0L

        try {
            val track = synchronized(trackLock) {
                getOrCreateAudioTrackLocked(sampleRate, isUrgent)
            }
            tTrackPrepEnd = System.nanoTime()

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            val totalBytes = pcmBytes.size
            var offset = 0
            val chunkSize = 8192 // 8KB chunks for efficient JNI streaming

            while (offset < totalBytes && _isPlaying.get()) {
                val bytesToWrite = minOf(chunkSize, totalBytes - offset)
                val written = track.write(pcmBytes, offset, bytesToWrite, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.w(tag, "AudioTrack.write error code: $written")
                    break
                }
                if (tFirstFrameWritten == 0L && written > 0) {
                    tFirstFrameWritten = System.nanoTime()
                }
                offset += written
            }

            if (tFirstFrameWritten == 0L) {
                tFirstFrameWritten = System.nanoTime()
            }

            // In streaming mode, stop() transitions track to draining mode
            // Playing continues until internal buffer empties
            try {
                track.stop()
            } catch (e: Exception) {
                Log.w(tag, "Error calling stop() on AudioTrack", e)
            }

            // Estimate acoustic drain time based on remaining samples in buffer:
            // totalFrames = totalBytes / 2 (16-bit mono)
            val totalFrames = totalBytes / 2
            val totalDurationMs = (totalFrames * 1000L) / sampleRate

            // Wait for head position or duration with tight 10ms polling to eliminate completion jitter
            val playStartTime = System.currentTimeMillis()
            while (_isPlaying.get() && (System.currentTimeMillis() - playStartTime) < (totalDurationMs + 100L)) {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_STOPPED) {
                        break
                    }
                } catch (_: Exception) {
                    break
                }
                delay(10)
            }

            val tPlaybackComplete = System.nanoTime()

            val timing = PlaybackTiming(
                trackPrepStartNanos = tTrackPrepStart,
                trackPrepEndNanos = tTrackPrepEnd,
                firstFrameWrittenNanos = tFirstFrameWritten,
                playbackCompleteNanos = tPlaybackComplete
            )
            onTiming?.invoke(timing)
            true
        } catch (e: Exception) {
            Log.e(tag, "PCM playback error", e)
            synchronized(trackLock) {
                releaseAudioTrackLocked()
            }
            false
        } finally {
            _isPlaying.set(false)
        }
    }

    private fun getOrCreateAudioTrackLocked(sampleRate: Int, isUrgent: Boolean): AudioTrack {
        val existing = audioTrack
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormatConfig.CHANNEL_CONFIG_OUT,
            AudioFormatConfig.AUDIO_FORMAT
        )
        val desiredBufferSize = maxOf(minBuf * 4, 16384)

        if (existing != null &&
            currentSampleRate == sampleRate &&
            currentIsUrgent == isUrgent &&
            existing.state == AudioTrack.STATE_INITIALIZED
        ) {
            // Reusing existing initialized track
            return existing
        }

        // Release old track if sample rate or usage changed
        releaseAudioTrackLocked()

        val usage = if (isUrgent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormatConfig.CHANNEL_CONFIG_OUT)
            .setEncoding(AudioFormatConfig.AUDIO_FORMAT)
            .build()

        val newTrack = AudioTrack(
            attributes,
            format,
            desiredBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack = newTrack
        currentSampleRate = sampleRate
        currentIsUrgent = isUrgent
        currentBufferSize = desiredBufferSize
        Log.i(tag, "Created and cached reusable AudioTrack ($sampleRate Hz, buffer=$desiredBufferSize bytes)")
        return newTrack
    }

    private fun releaseAudioTrackLocked() {
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        currentSampleRate = 0
    }

    override fun stopPlayback() {
        _isPlaying.set(false)
        synchronized(trackLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (e: Exception) {
                Log.w(tag, "Error flushing AudioTrack", e)
                releaseAudioTrackLocked()
            }
        }
    }

    override fun release() {
        stopPlayback()
        synchronized(trackLock) {
            releaseAudioTrackLocked()
        }
    }
}
