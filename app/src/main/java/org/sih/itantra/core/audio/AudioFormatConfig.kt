package org.sih.itantra.core.audio

import android.media.AudioFormat
import android.media.MediaRecorder

/**
 * Audio capture and playback parameters optimized for on-device speech processing.
 * 16 kHz, 16-bit Mono PCM is the universal standard for Whisper, Sherpa-ONNX, and VAD models.
 */
object AudioFormatConfig {
    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION

    const val BYTES_PER_SAMPLE = 2
    const val FRAME_SIZE_SAMPLES = 512 // 32ms at 16kHz
    const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * BYTES_PER_SAMPLE // 1024 bytes

    const val RAW_AUDIO_BITRATE_BPS = SAMPLE_RATE_HZ * BYTES_PER_SAMPLE * 8 // 256,000 bps
    const val RAW_AUDIO_BYTES_PER_SEC = SAMPLE_RATE_HZ * BYTES_PER_SAMPLE // 32,000 B/s
}
