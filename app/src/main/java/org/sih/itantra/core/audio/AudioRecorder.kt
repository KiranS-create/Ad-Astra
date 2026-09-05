package org.sih.itantra.core.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

interface AudioRecorder {
    val audioFlow: SharedFlow<ByteArray>
    val isRecording: Boolean
    fun startRecording(): Boolean
    fun stopRecording()
    fun release()
}

class AndroidAudioRecorder(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioRecorder {

    private val tag = "AndroidAudioRecorder"
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val _isRecording = AtomicBoolean(false)

    private val _audioFlow = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    override val audioFlow: SharedFlow<ByteArray> = _audioFlow.asSharedFlow()

    override val isRecording: Boolean
        get() = _isRecording.get()

    private val minBufferSize: Int by lazy {
        val size = AudioRecord.getMinBufferSize(
            AudioFormatConfig.SAMPLE_RATE_HZ,
            AudioFormatConfig.CHANNEL_CONFIG_IN,
            AudioFormatConfig.AUDIO_FORMAT
        )
        maxOf(size, AudioFormatConfig.FRAME_SIZE_BYTES * 4)
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(): Boolean {
        if (_isRecording.get()) return true

        try {
            audioRecord = AudioRecord(
                AudioFormatConfig.AUDIO_SOURCE,
                AudioFormatConfig.SAMPLE_RATE_HZ,
                AudioFormatConfig.CHANNEL_CONFIG_IN,
                AudioFormatConfig.AUDIO_FORMAT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.set(true)

            recordingJob = CoroutineScope(dispatcher).launch {
                val buffer = ByteArray(AudioFormatConfig.FRAME_SIZE_BYTES)
                while (isActive && _isRecording.get()) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val frame = buffer.copyOf(readBytes)
                        _audioFlow.emit(frame)
                    } else if (readBytes < 0) {
                        Log.w(tag, "AudioRecord read error code: $readBytes")
                        break
                    }
                }
            }

            Log.i(tag, "Audio recording started at ${AudioFormatConfig.SAMPLE_RATE_HZ}Hz Mono")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord", e)
            stopRecording()
            return false
        }
    }

    override fun stopRecording() {
        if (!_isRecording.getAndSet(false)) return
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            Log.i(tag, "Audio recording stopped")
        }
    }

    override fun release() {
        stopRecording()
    }
}
