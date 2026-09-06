package org.sih.itantra.core.session

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sih.itantra.core.audio.AndroidAudioPlayer
import org.sih.itantra.core.audio.AndroidAudioRecorder
import org.sih.itantra.core.audio.AudioPlayer
import org.sih.itantra.core.audio.AudioRecorder
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.diagnostics.BandwidthMetrics
import org.sih.itantra.core.diagnostics.BenchmarkClock
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.diagnostics.LatencyMetrics
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.AdaptiveCompressor
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.stt.OfflineSpeechRecognizer
import org.sih.itantra.core.stt.SentenceFinalizer
import org.sih.itantra.core.stt.SpeechRecognizer
import org.sih.itantra.core.transport.TransportManager
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.core.tts.OfflineTtsEngine
import org.sih.itantra.core.tts.TextSynthesizer
import org.sih.itantra.core.vad.VadDetector
import org.sih.itantra.core.vad.VadState
import org.sih.itantra.ml.model.ModelAssetManager
import org.sih.itantra.ml.stt.NeuralSpeechRouter
import org.sih.itantra.ml.tts.NeuralTtsRouter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Master Transceiver Coordinator for iTantra.
 * Bridges: Audio Capture -> VAD -> STT -> Protocol -> Transport -> Remote TTS -> Playback
 */
class TransceiverCoordinator(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val recorder: AudioRecorder = AndroidAudioRecorder(dispatcher),
    val player: AudioPlayer = AndroidAudioPlayer(dispatcher),
    val vad: VadDetector = VadDetector(),
    val modelAssetManager: ModelAssetManager = ModelAssetManager(context),
    val stt: SpeechRecognizer = NeuralSpeechRouter(context, modelAssetManager),
    val tts: TextSynthesizer = NeuralTtsRouter(context, modelAssetManager),
    val transportManager: TransportManager = TransportManager(context)
) {
    private val tag = "TransceiverCoordinator"
    private val scope = CoroutineScope(dispatcher)

    val stateMachine = PttStateMachine()
    private val sequenceCounter = AtomicInteger(1)
    private val localDeviceId: Int = (Math.random() * 900000 + 100000).toInt()

    private val _activeLanguage = MutableStateFlow(IndicLanguage.HINDI)
    val activeLanguage: StateFlow<IndicLanguage> = _activeLanguage.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _lastTranscribedText = MutableStateFlow("")
    val lastTranscribedText: StateFlow<String> = _lastTranscribedText.asStateFlow()

    private val _lastReceivedText = MutableStateFlow("")
    val lastReceivedText: StateFlow<String> = _lastReceivedText.asStateFlow()

    private var audioCollectJob: Job? = null
    private var rxCollectJob: Job? = null
    private var tAudioCaptureStart: Long = 0L

    init {
        setupVadCallbacks()
        setupRxPipeline()
    }

    private fun setupVadCallbacks() {
        vad.onSpeechStateChanged = { vadState ->
            if (vadState == VadState.SPEECH_START || vadState == VadState.SPEECH_ACTIVE) {
                stateMachine.transitionTo(PttState.SPEECH_DETECTED)
            }
        }

        vad.onSpeechSegmentFinalized = { segment, durationMs ->
            scope.launch {
                handleSpeechSegment(segment, durationMs, MessagePriority.NORMAL)
            }
        }
    }

    private fun setupRxPipeline() {
        rxCollectJob = scope.launch {
            transportManager.receivedPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    suspend fun start() {
        scope.launch {
            modelAssetManager.ensureModelsReady()
        }
        transportManager.start()
    }

    suspend fun testSynthesizeAndPlay(text: String, language: IndicLanguage = _activeLanguage.value): Boolean {
        return tts.synthesize(text, language, false)
    }

    suspend fun stop() {
        stopPtt()
        transportManager.stop()
        recorder.release()
        player.release()
        stt.release()
        tts.release()
    }

    fun setLanguage(language: IndicLanguage) {
        _activeLanguage.value = language
        scope.launch {
            stt.prepareLanguage(language)
            tts.prepareLanguage(language)
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousMode.value = enabled
        if (enabled) {
            startContinuousListening()
        } else {
            stopContinuousListening()
        }
    }

    fun startPtt(priority: MessagePriority = MessagePriority.NORMAL) {
        if (stateMachine.state.value != PttState.IDLE) return

        stateMachine.transitionTo(PttState.PTT_PRESSED)
        tAudioCaptureStart = BenchmarkClock.nowNanos()
        vad.reset()

        if (recorder.startRecording()) {
            stateMachine.transitionTo(PttState.RECORDING)
            audioCollectJob?.cancel()
            audioCollectJob = scope.launch {
                recorder.audioFlow.collect { pcmFrame ->
                    vad.processFrame(pcmFrame)
                }
            }
        } else {
            stateMachine.transitionTo(PttState.ERROR)
            stateMachine.reset()
        }
    }

    fun stopPtt() {
        if (stateMachine.state.value == PttState.RECORDING || stateMachine.state.value == PttState.SPEECH_DETECTED) {
            audioCollectJob?.cancel()
            audioCollectJob = null
            recorder.stopRecording()
            vad.forceFinalize()
            if (vad.vadState.value == VadState.IDLE && stateMachine.state.value != PttState.STT_PROCESSING) {
                stateMachine.reset()
            }
        } else {
            stateMachine.reset()
        }
    }

    private fun startContinuousListening() {
        tAudioCaptureStart = BenchmarkClock.nowNanos()
        vad.reset()
        recorder.startRecording()
        stateMachine.transitionTo(PttState.RECORDING)

        audioCollectJob?.cancel()
        audioCollectJob = scope.launch {
            recorder.audioFlow.collect { pcmFrame ->
                // Do not process microphone input while playing received audio (avoids feedback loop)
                if (stateMachine.state.value != PttState.PLAYING) {
                    vad.processFrame(pcmFrame)
                }
            }
        }
    }

    private fun stopContinuousListening() {
        audioCollectJob?.cancel()
        audioCollectJob = null
        recorder.stopRecording()
        stateMachine.reset()
    }

    /**
     * Outgoing Pipeline: VAD Segment -> STT -> Segment -> Encode -> Transmit
     */
    private suspend fun handleSpeechSegment(pcmBytes: ByteArray, durationMs: Long, priority: MessagePriority) {
        stateMachine.transitionTo(PttState.STT_PROCESSING)
        val tSttStart = BenchmarkClock.nowNanos()

        val lang = _activeLanguage.value
        val sttResult = stt.processAudioSegment(pcmBytes, lang)
        val tSttEnd = BenchmarkClock.nowNanos()
        val sttLatencyMs = BenchmarkClock.elapsedMs(tSttStart, tSttEnd)

        val finalText = SentenceFinalizer.finalizeSentence(sttResult.text, lang)
        _lastTranscribedText.value = finalText

        if (finalText.isBlank()) {
            stateMachine.reset()
            return
        }

        stateMachine.transitionTo(PttState.MESSAGE_ENCODED)
        val tEncodeStart = BenchmarkClock.nowNanos()

        val rawBytes = finalText.toByteArray(Charsets.UTF_8)
        val compressionResult = AdaptiveCompressor.compress(rawBytes)

        val flags = if (compressionResult.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte()
        val seq = sequenceCounter.getAndIncrement().toShort()

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = if (priority.isEmergency) Packet.TYPE_ALERT else Packet.TYPE_TEXT,
            priority = priority,
            flags = flags,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = localDeviceId,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = lang,
            payload = compressionResult.bytes
        )

        val tEncodeEnd = BenchmarkClock.nowNanos()
        val encodingLatencyMs = BenchmarkClock.elapsedMs(tEncodeStart, tEncodeEnd)

        stateMachine.transitionTo(PttState.TRANSMITTING)
        val tSendStart = BenchmarkClock.nowNanos()
        val sentSuccess = transportManager.send(packet)
        val tSendEnd = BenchmarkClock.nowNanos()
        val transportLatencyMs = BenchmarkClock.elapsedMs(tSendStart, tSendEnd)

        val serializedBytes = PacketSerializer.serialize(packet)
        val rawAudioBytes = (durationMs * 32000L) / 1000L

        val latencyMetrics = LatencyMetrics(
            audioDurationMs = durationMs,
            sttLatencyMs = sttLatencyMs,
            encodingLatencyMs = encodingLatencyMs,
            transportLatencyMs = transportLatencyMs,
            language = lang
        )

        val bandwidthMetrics = BandwidthMetrics.fromAudioDuration(
            durationMs = durationMs,
            packetBytes = serializedBytes.size,
            text = finalText,
            compressed = compressionResult.isCompressed
        )

        DiagnosticsRepository.recordTransmission(
            packetBytes = serializedBytes.size,
            rawAudioBytes = rawAudioBytes,
            latency = latencyMetrics,
            bandwidth = bandwidthMetrics
        )

        MessageHistoryStore.addRecord(
            MessageRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.SENT,
                language = lang,
                priority = priority,
                text = finalText,
                peer = "Broadcast",
                packetSizeBytes = serializedBytes.size,
                rawAudioEquivalentBytes = rawAudioBytes,
                measuredLatencyMs = sttLatencyMs + encodingLatencyMs + transportLatencyMs
            )
        )

        Log.i(tag, "Transmitted '${finalText}' ($lang) | Packet: ${serializedBytes.size}B vs Audio: ${rawAudioBytes}B (-${bandwidthMetrics.bandwidthReductionPercent}%)")

        if (_isContinuousMode.value) {
            stateMachine.transitionTo(PttState.RECORDING)
        } else {
            stateMachine.reset()
        }
    }

    /**
     * Incoming Pipeline: Transport -> Protocol Decode -> TTS Synthesize -> Playback
     */
    private suspend fun handleIncomingPacket(packet: Packet) {
        // Drop self-broadcast packets to prevent transmitter from playing its own speech
        if (packet.sourceDeviceId == localDeviceId) {
            return
        }

        val tRx = BenchmarkClock.nowNanos()
        stateMachine.transitionTo(PttState.RECEIVED)

        val decompressedBytes = AdaptiveCompressor.decompress(packet.payload, packet.isCompressed)
        val text = String(decompressedBytes, Charsets.UTF_8)
        _lastReceivedText.value = text

        val isUrgent = packet.priority.isEmergency
        stateMachine.transitionTo(PttState.TTS_PROCESSING)

        val tTtsStart = BenchmarkClock.nowNanos()
        stateMachine.transitionTo(PttState.PLAYING)
        tts.synthesize(text, packet.language, isUrgent)
        val tTtsEnd = BenchmarkClock.nowNanos()

        val ttsLatencyMs = BenchmarkClock.elapsedMs(tTtsStart, tTtsEnd)
        DiagnosticsRepository.recordReception(packet.payload.size + Packet.HEADER_SIZE_BYTES + Packet.CRC_SIZE_BYTES)

        MessageHistoryStore.addRecord(
            MessageRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.RECEIVED,
                language = packet.language,
                priority = packet.priority,
                text = text,
                peer = "Node #${packet.sourceDeviceId}",
                packetSizeBytes = packet.payload.size + Packet.MIN_PACKET_SIZE,
                rawAudioEquivalentBytes = (text.length * 200L * 32L), // approx equivalent
                measuredLatencyMs = ttsLatencyMs
            )
        )

        Log.i(tag, "Received '${text}' (${packet.language}) from Node #${packet.sourceDeviceId} (Priority: ${packet.priority})")

        if (_isContinuousMode.value) {
            stateMachine.transitionTo(PttState.RECORDING)
        } else {
            stateMachine.reset()
        }
    }

    /**
     * Sends an immediate high-priority Emergency Distress / Alert broadcast.
     */
    fun sendAlert(alertText: String, isDistress: Boolean = false) {
        scope.launch {
            val priority = if (isDistress) MessagePriority.DISTRESS else MessagePriority.ALERT
            val lang = _activeLanguage.value
            val cleanText = SentenceFinalizer.finalizeSentence(alertText, lang)
            val rawBytes = cleanText.toByteArray(Charsets.UTF_8)
            val compression = AdaptiveCompressor.compress(rawBytes)

            val packet = Packet(
                version = Packet.PROTOCOL_VERSION,
                msgType = Packet.TYPE_ALERT,
                priority = priority,
                flags = if (compression.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
                sequenceNumber = sequenceCounter.getAndIncrement().toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = localDeviceId,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = lang,
                payload = compression.bytes
            )

            transportManager.send(packet)
            MessageHistoryStore.addRecord(
                MessageRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    direction = MessageDirection.SENT,
                    language = lang,
                    priority = priority,
                    text = cleanText,
                    peer = "Emergency Broadcast",
                    packetSizeBytes = packet.payload.size + Packet.MIN_PACKET_SIZE,
                    rawAudioEquivalentBytes = (cleanText.length * 200L * 32L),
                    measuredLatencyMs = 12.0
                )
            )
        }
    }
}
