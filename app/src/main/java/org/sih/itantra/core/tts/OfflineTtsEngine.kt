package org.sih.itantra.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.sih.itantra.core.common.IndicLanguage
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * On-device offline Text-to-Speech engine supporting all 10 Indic languages.
 * Operates purely on local device voice data with zero internet requests.
 */
class OfflineTtsEngine(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : TextSynthesizer, TextToSpeech.OnInitListener {

    private val tag = "OfflineTtsEngine"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _ttsState = MutableStateFlow(TtsState.IDLE)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val alertToneGenerator = AlertToneGenerator()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.i(tag, "Offline TTS Engine initialized successfully")
        } else {
            Log.e(tag, "Offline TTS Engine initialization failed with status $status")
            _ttsState.value = TtsState.ERROR
        }
    }

    override suspend fun synthesize(text: String, language: IndicLanguage, isUrgent: Boolean): Boolean =
        withContext(dispatcher) {
            if (!isInitialized || tts == null) {
                Log.w(tag, "TTS engine not ready")
                return@withContext false
            }

            if (isUrgent) {
                // Play emergency acoustic siren alert before speaking
                alertToneGenerator.playAlertTone()
            }

            return@withContext suspendCancellableCoroutine { continuation ->
                val locale = when (language) {
                    IndicLanguage.HINDI -> Locale("hi", "IN")
                    IndicLanguage.GUJARATI -> Locale("gu", "IN")
                    IndicLanguage.MARATHI -> Locale("mr", "IN")
                    IndicLanguage.KANNADA -> Locale("kn", "IN")
                    IndicLanguage.MALAYALAM -> Locale("ml", "IN")
                    IndicLanguage.TAMIL -> Locale("ta", "IN")
                    IndicLanguage.TELUGU -> Locale("te", "IN")
                    IndicLanguage.ODIA -> Locale("or", "IN")
                    IndicLanguage.BENGALI -> Locale("bn", "IN")
                    IndicLanguage.ENGLISH -> Locale("en", "IN")
                }

                tts?.language = locale

                val utteranceId = UUID.randomUUID().toString()
                val params = Bundle().apply {
                    if (isUrgent) {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    } else {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    }
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        _ttsState.value = TtsState.PLAYING
                    }

                    override fun onDone(id: String?) {
                        _ttsState.value = TtsState.COMPLETED
                        if (continuation.isActive) continuation.resume(true)
                        _ttsState.value = TtsState.IDLE
                    }

                    override fun onError(id: String?) {
                        _ttsState.value = TtsState.ERROR
                        if (continuation.isActive) continuation.resume(false)
                        _ttsState.value = TtsState.IDLE
                    }
                })

                _ttsState.value = TtsState.SYNTHESIZING
                val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = tts?.speak(text, queueMode, params, utteranceId)

                if (result != TextToSpeech.SUCCESS) {
                    _ttsState.value = TtsState.ERROR
                    if (continuation.isActive) continuation.resume(false)
                    _ttsState.value = TtsState.IDLE
                }
            }
        }

    override fun stop() {
        tts?.stop()
        _ttsState.value = TtsState.IDLE
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
