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
    private val pendingUtterances = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CancellableContinuation<Boolean>>()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupUtteranceListener()
            Log.i(tag, "Offline TTS Engine initialized successfully")
        } else {
            Log.e(tag, "Offline TTS Engine initialization failed with status $status")
            _ttsState.value = TtsState.ERROR
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                _ttsState.value = TtsState.PLAYING
            }

            override fun onDone(id: String?) {
                val cont = id?.let { pendingUtterances.remove(it) }
                if (cont != null && cont.isActive) cont.resume(true)
                if (pendingUtterances.isEmpty()) {
                    _ttsState.value = TtsState.IDLE
                }
            }

            override fun onError(id: String?) {
                val cont = id?.let { pendingUtterances.remove(it) }
                if (cont != null && cont.isActive) cont.resume(false)
                if (pendingUtterances.isEmpty()) {
                    _ttsState.value = TtsState.IDLE
                }
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                val cont = id?.let { pendingUtterances.remove(it) }
                if (cont != null && cont.isActive) cont.resume(false)
                if (pendingUtterances.isEmpty()) {
                    _ttsState.value = TtsState.IDLE
                }
            }
        })
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

            suspendCancellableCoroutine { continuation ->
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
                pendingUtterances[utteranceId] = continuation
                continuation.invokeOnCancellation {
                    pendingUtterances.remove(utteranceId)
                }

                val params = Bundle().apply {
                    if (isUrgent) {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    } else {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    }
                }

                _ttsState.value = TtsState.SYNTHESIZING
                if (isUrgent) {
                    // Preempt any queued non-urgent utterances
                    for ((id, cont) in pendingUtterances) {
                        if (id != utteranceId && cont.isActive) cont.resume(false)
                    }
                    pendingUtterances.keys.retainAll { it == utteranceId }
                }

                val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = tts?.speak(text, queueMode, params, utteranceId)

                if (result != TextToSpeech.SUCCESS) {
                    pendingUtterances.remove(utteranceId)
                    _ttsState.value = TtsState.ERROR
                    if (continuation.isActive) continuation.resume(false)
                    _ttsState.value = TtsState.IDLE
                }
            }
        }

    override fun stop() {
        for ((_, cont) in pendingUtterances) {
            if (cont.isActive) cont.resume(false)
        }
        pendingUtterances.clear()
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
