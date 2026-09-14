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
import kotlinx.coroutines.withContext
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
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.network.AdaptiveNetworkMode
import org.sih.itantra.core.vbr.AdaptiveMessageRepresentation
import org.sih.itantra.core.vbr.AdaptiveRepresentationMode
import org.sih.itantra.core.vbr.AdaptiveRepresentationPolicy
import org.sih.itantra.core.vbr.CompactTextGenerator
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.SemanticEmergencyClassifier
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
import org.sih.itantra.core.mesh.PacketRelayRouter
import org.sih.itantra.core.mesh.RelayAction
import org.sih.itantra.core.mesh.ManetRouter
import org.sih.itantra.core.protocol.DeliveryReceipt
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.protocol.PacketFragment
import org.sih.itantra.core.protocol.PacketFragmenter
import org.sih.itantra.core.protocol.PendingTransferTracker
import org.sih.itantra.core.protocol.ReassemblyBuffer
import org.sih.itantra.core.protocol.ReassemblyResult
import org.sih.itantra.core.crypto.AntiReplayFilter
import org.sih.itantra.core.crypto.AuthStatus
import org.sih.itantra.core.crypto.NetworkKeyManager
import org.sih.itantra.core.crypto.PacketAuthenticator
import org.sih.itantra.core.qos.TacticalPacketScheduler
import org.sih.itantra.core.qos.CongestionState
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.sih.itantra.core.protocol.VoiceCommandEngine
import org.sih.itantra.core.protocol.VoiceCommandResult

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

    enum class SecurityMode {
        STRICT,
        COMPATIBILITY
    }

    private val _securityMode = MutableStateFlow(SecurityMode.STRICT)
    val securityMode: StateFlow<SecurityMode> = _securityMode.asStateFlow()
    fun setSecurityMode(mode: SecurityMode) {
        _securityMode.value = mode
    }

    val antiReplayFilter = AntiReplayFilter()

    val stateMachine = PttStateMachine()
    private val sequenceCounter = AtomicInteger(1)
    private val transferSequence = AtomicInteger(1)
    private val localDeviceId: Int = org.sih.itantra.service.ManetNodePreference.lastNodeId(context).let { stored ->
        if (stored != 0) stored
        else {
            val generated = (Math.random() * 900_000 + 100_000).toInt()
            org.sih.itantra.service.ManetNodePreference.setLastNodeId(context, generated)
            generated
        }
    }

    private val deliveredMessagesLock = Any()
    private val deliveredMessages = object : java.util.LinkedHashMap<String, Long>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 500
        }
    }

    private fun isAlreadyDelivered(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(deliveredMessagesLock) {
            val prev = deliveredMessages[key]
            if (prev != null && (now - prev) < 60_000L) {
                return true
            }
            deliveredMessages[key] = now
            return false
        }
    }

    val relayRouter = PacketRelayRouter(localDeviceId)
    val manetRouter = ManetRouter(
        context          = context,
        localNodeId      = localDeviceId,
        transportManager = transportManager,
        relayRouter      = relayRouter,
        scope            = scope
    )

    val qosScheduler = TacticalPacketScheduler(
        maxCapacity = TacticalPacketScheduler.MAX_OUTBOUND_QUEUE,
        starvationThresholdMs = TacticalPacketScheduler.DEFAULT_STARVATION_THRESHOLD_MS,
        scope = scope,
        transmitter = { packet ->
            if (manetRouter.isEnabled.value) {
                manetRouter.routeAndSend(packet)
            } else {
                transportManager.send(packet)
            }
        }
    )

    val fragmenter = PacketFragmenter(Packet.MAX_FRAGMENT_PAYLOAD)
    val reassemblyBuffer = ReassemblyBuffer()
    val pendingTransferTracker = PendingTransferTracker()

    private val _activeLanguage = MutableStateFlow(IndicLanguage.HINDI)
    val activeLanguage: StateFlow<IndicLanguage> = _activeLanguage.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _lastTranscribedText = MutableStateFlow("")
    val lastTranscribedText: StateFlow<String> = _lastTranscribedText.asStateFlow()

    private val _lastReceivedText = MutableStateFlow("")
    val lastReceivedText: StateFlow<String> = _lastReceivedText.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    var onPacketActivity: ((type: String, description: String, sourceId: Int?, destId: Int?, priority: String?, rawPacket: Packet?) -> Unit)? = null

    /** Callback fired when a deterministic voice command is detected in an STT transcript.
     *  The ViewModel registers here to execute the command and update UI state.
     *  Returns true if the command was handled (suppress normal TX), false to transmit normally. */
    var onVoiceCommandResult: ((result: VoiceCommandResult) -> Boolean)? = null

    private var activePrepJob: Job? = null
    private val prepLock = Any()

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
            prepareLanguageInternal(_activeLanguage.value)
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

    private suspend fun prepareLanguageInternal(language: IndicLanguage) = withContext(dispatcher) {
        val job: Job
        synchronized(prepLock) {
            activePrepJob?.cancel()
            job = scope.launch {
                _isModelReady.value = false
                stt.prepareLanguage(language)
                tts.prepareLanguage(language)
                val sttOk = stt.awaitReady(language, 15000L)
                val ttsOk = tts.awaitReady(language, 15000L)
                if (sttOk && ttsOk) {
                    _isModelReady.value = true
                    Log.i(tag, "full application ready: ${language.displayName}")
                } else {
                    Log.w(tag, "Model preparation incomplete for ${language.displayName}: sttOk=$sttOk, ttsOk=$ttsOk")
                }
            }
            activePrepJob = job
        }
        job.join()
    }

    fun setLanguage(language: IndicLanguage) {
        _activeLanguage.value = language
        scope.launch {
            prepareLanguageInternal(language)
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
     * Resolves the active network condition mode for adaptive VBR representation selection.
     */
    fun resolveCurrentNetworkMode(): AdaptiveNetworkMode {
        val congestion = qosScheduler.getCongestionState()
        if (congestion == org.sih.itantra.core.qos.CongestionState.CONGESTED) return AdaptiveNetworkMode.CONGESTED
        val wifiState = transportManager.wifiTransport.state.value
        val btState = transportManager.bluetoothTransport.state.value
        val isWifiActive = wifiState == org.sih.itantra.core.transport.TransportState.CONNECTED || wifiState == org.sih.itantra.core.transport.TransportState.LISTENING
        val isBtActive = btState == org.sih.itantra.core.transport.TransportState.CONNECTED || btState == org.sih.itantra.core.transport.TransportState.LISTENING
        if (!isWifiActive && !isBtActive) return AdaptiveNetworkMode.OFFLINE
        val dtnSize = manetRouter.dtnStore.size()
        if (dtnSize > 0) return AdaptiveNetworkMode.DTN_STORED
        if (!isWifiActive || !isBtActive) return AdaptiveNetworkMode.LIMITED
        return AdaptiveNetworkMode.HEALTHY
    }

    /**
     * Outgoing Pipeline: VAD Segment -> STT -> Segment -> Encode -> Transmit
     */
    private suspend fun handleSpeechSegment(pcmBytes: ByteArray, durationMs: Long, priority: MessagePriority) {
        stateMachine.transitionTo(PttState.STT_PROCESSING)
        val tSttStart = BenchmarkClock.nowNanos()

        val lang = _activeLanguage.value
        if (!stt.isReadyForLanguage(lang)) {
            Log.i(tag, "PTT input received while STT model for ${lang.displayName} is initializing. Awaiting readiness...")
            val ready = stt.awaitReady(lang, 15000L)
            if (!ready) {
                Log.w(tag, "STT model not ready within timeout for ${lang.displayName}. Discarding segment safely without crash.")
                stateMachine.reset()
                return
            }
        }

        val sttResult = stt.processAudioSegment(pcmBytes, lang)
        val tSttEnd = BenchmarkClock.nowNanos()
        val sttLatencyMs = BenchmarkClock.elapsedMs(tSttStart, tSttEnd)

        val finalText = SentenceFinalizer.finalizeSentence(sttResult.text, lang)
        _lastTranscribedText.value = finalText

        if (finalText.isBlank()) {
            stateMachine.reset()
            return
        }

        // Voice command interception: check if the transcript is a deterministic radio command
        // before normal message encoding/transmission. Never interferes with semantic emergency path.
        val commandHandler = onVoiceCommandResult
        if (commandHandler != null) {
            val cmdResult = VoiceCommandEngine.processTranscript(finalText, lang)
            if (cmdResult != null) {
                val handled = commandHandler.invoke(cmdResult)
                if (handled) {
                    // Command consumed — skip normal transmission and return to idle/continuous
                    Log.i(tag, "Voice command '${cmdResult.command}' handled; skipping TX for: '$finalText'")
                    if (_isContinuousMode.value) {
                        stateMachine.transitionTo(PttState.RECORDING)
                    } else {
                        stateMachine.reset()
                    }
                    return
                }
            }
        }

        stateMachine.transitionTo(PttState.MESSAGE_ENCODED)
        val tEncodeStart = BenchmarkClock.nowNanos()

        val rawBytes = finalText.toByteArray(Charsets.UTF_8)
        val currentNetMode = resolveCurrentNetworkMode()
        val representation = AdaptiveRepresentationPolicy.select(
            text = finalText,
            networkMode = currentNetMode,
            language = lang,
            semanticConfidence = 0.90f
        )

        val isSemantic = representation.mode == AdaptiveRepresentationMode.SEMANTIC
        val semanticCmd = representation.semanticCommand
        val semanticSummary = semanticCmd?.toBadgeString()
        val semanticSavings = if (semanticCmd != null) (rawBytes.size - SemanticCommand.SIZE_BYTES).coerceAtLeast(0) else null

        val effectivePriority = when {
            semanticCmd != null -> when (semanticCmd.severity) {
                EmergencySeverity.CRITICAL -> MessagePriority.DISTRESS
                EmergencySeverity.ALERT -> MessagePriority.ALERT
                EmergencySeverity.IMPORTANT -> MessagePriority.IMPORTANT
                EmergencySeverity.NORMAL -> priority
            }
            else -> priority
        }

        val flagVal: Byte = when (representation.mode) {
            AdaptiveRepresentationMode.SEMANTIC -> Packet.FLAG_SEMANTIC.toByte()
            AdaptiveRepresentationMode.COMPACT -> {
                val base = Packet.FLAG_COMPACT
                val comp = if (representation.isCompressed) Packet.FLAG_COMPRESSED else 0
                (base or comp).toByte()
            }
            AdaptiveRepresentationMode.FULL, AdaptiveRepresentationMode.UNKNOWN -> {
                (if (representation.isCompressed) Packet.FLAG_COMPRESSED else 0).toByte()
            }
        }
        val packetPayload = representation.payloadBytes
        val flags = flagVal

        Log.i(tag, "ADAPTIVE VBR SELECTION [${representation.mode}]: '$finalText' -> '${representation.text}' (${rawBytes.size}B -> ${packetPayload.size}B, net=$currentNetMode)")

        val messageId = UUID.randomUUID().toString()
        val isFragmented = PacketFragmenter.needsFragmentation(packetPayload.size)
        val transferId = (transferSequence.getAndIncrement() and 0x7FFF).toShort()
        val originalMsgType = if (effectivePriority.isEmergency) Packet.TYPE_ALERT else Packet.TYPE_TEXT

        val fragments = if (isFragmented) {
            fragmenter.fragment(
                payload = packetPayload,
                transferId = transferId,
                originalMsgType = originalMsgType,
                originalFlags = flags
            )
        } else emptyList()

        val tEncodeEnd = BenchmarkClock.nowNanos()
        val encodingLatencyMs = BenchmarkClock.elapsedMs(tEncodeStart, tEncodeEnd)

        stateMachine.transitionTo(PttState.TRANSMITTING)
        val tSendStart = BenchmarkClock.nowNanos()
        var totalWireBytes = 0

        val sentSuccess = if (isFragmented) {
            val key = NetworkKeyManager.getKey()
            val signedPackets = ArrayList<Packet>(fragments.size)
            for (frag in fragments) {
                val fragPacket = Packet(
                    version = Packet.PROTOCOL_VERSION,
                    msgType = originalMsgType,
                    priority = effectivePriority,
                    flags = (Packet.FLAG_FRAGMENTED or Packet.FLAG_REQUIRES_ACK).toByte(),
                    sequenceNumber = sequenceCounter.getAndIncrement().toShort(),
                    timestamp = System.currentTimeMillis(),
                    sourceDeviceId = localDeviceId,
                    destinationDeviceId = Packet.BROADCAST_ID,
                    language = lang,
                    payload = frag.toPayload()
                )
                val signedPacket = if (key != null) {
                    val (signed, genNanos) = PacketAuthenticator.signWithLatency(fragPacket, key)
                    DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                    signed
                } else fragPacket
                signedPackets.add(signedPacket)
                totalWireBytes += PacketSerializer.serialize(signedPacket).size
            }
            pendingTransferTracker.registerTransfer(
                transferId = transferId,
                messageId = messageId,
                destinationDeviceId = Packet.BROADCAST_ID,
                fragmentCount = fragments.size,
                payloadBytes = packetPayload.size,
                totalWireBytes = totalWireBytes
            )
            DiagnosticsRepository.recordFragmentsSent(
                count = fragments.size,
                payloadBytes = packetPayload.size,
                wireBytes = totalWireBytes,
                transferId = transferId
            )
            onPacketActivity?.invoke("FRAG", "FRAG ${fragments.size} pkts (0x${Integer.toHexString(transferId.toInt() and 0xFFFF)})", localDeviceId, Packet.BROADCAST_ID, effectivePriority.name, signedPackets.firstOrNull())
            qosScheduler.sendBatch(signedPackets)
        } else {
            val key = NetworkKeyManager.getKey()
            val rawPacket = Packet(
                version = Packet.PROTOCOL_VERSION,
                msgType = originalMsgType,
                priority = effectivePriority,
                flags = flags,
                sequenceNumber = transferId,
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = localDeviceId,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = lang,
                payload = packetPayload,
                semanticCommand = semanticCmd
            )
            val signedPacket = if (key != null) {
                val (signed, genNanos) = PacketAuthenticator.signWithLatency(rawPacket, key)
                DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                signed
            } else rawPacket
            totalWireBytes = PacketSerializer.serialize(signedPacket).size
            onPacketActivity?.invoke("TX", "TX: ${finalText.take(20)} (0x${Integer.toHexString(transferId.toInt() and 0xFFFF)})", localDeviceId, Packet.BROADCAST_ID, effectivePriority.name, signedPacket)
            qosScheduler.send(signedPacket)
        }

        val tSendEnd = BenchmarkClock.nowNanos()
        val transportLatencyMs = BenchmarkClock.elapsedMs(tSendStart, tSendEnd)
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
            packetBytes = totalWireBytes,
            text = finalText,
            compressed = (flags.toInt() and Packet.FLAG_COMPRESSED) != 0
        )

        DiagnosticsRepository.recordTransmission(
            packetBytes = totalWireBytes,
            rawAudioBytes = rawAudioBytes,
            latency = latencyMetrics,
            bandwidth = bandwidthMetrics
        )

        val hasAuthKey = NetworkKeyManager.hasKey()
        val cState = qosScheduler.getCongestionState()
        val qosStatus = when {
            cState == CongestionState.CONGESTED && effectivePriority == MessagePriority.NORMAL -> "DEFERRED — CONGESTION"
            cState == CongestionState.BUSY && effectivePriority == MessagePriority.NORMAL -> "QUEUED"
            else -> null
        }

        MessageHistoryStore.addRecord(
            MessageRecord(
                id = messageId,
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.SENT,
                language = lang,
                priority = effectivePriority,
                text = if (isSemantic) semanticCmd!!.toDisplayString() else representation.text,
                peer = "Broadcast",
                packetSizeBytes = totalWireBytes,
                rawAudioEquivalentBytes = rawAudioBytes,
                measuredLatencyMs = sttLatencyMs + encodingLatencyMs + transportLatencyMs,
                isSemantic = isSemantic,
                semanticSummary = semanticSummary,
                semanticSavingsBytes = semanticSavings,
                deliveryStatus = if (isFragmented) DeliveryStatus.PENDING else DeliveryStatus.NONE,
                transferId = if (isFragmented) transferId else null,
                fragmentCount = if (isFragmented) fragments.size else null,
                isSecure = hasAuthKey,
                authStatus = if (hasAuthKey) "AUTH ✓" else "UNVERIFIED",
                qosStatus = qosStatus,
                representationMode = representation.mode.name
            )
        )

        Log.i(tag, "Transmitted '${finalText}' ($lang, frags=${if (isFragmented) fragments.size else 1}) | Wire: ${totalWireBytes}B vs Audio: ${rawAudioBytes}B (-${bandwidthMetrics.bandwidthReductionPercent}%)")

        if (_isContinuousMode.value) {
            stateMachine.transitionTo(PttState.RECORDING)
        } else {
            stateMachine.reset()
        }
    }

    /**
     * Emergency Distress Transmission:
     * Broadcasts an emergency distress signal with highest priority over MANET/transport.
     * Attaches compact GeoLocation metadata if available.
     */
    suspend fun sendEmergencyDistress(messageText: String, location: org.sih.itantra.core.protocol.GeoLocation?): Boolean {
        val lang = _activeLanguage.value
        val rawBytes = messageText.toByteArray(Charsets.UTF_8)
        val semanticCmd = SemanticEmergencyClassifier.classify(messageText)

        val isSemantic = semanticCmd != null
        val semanticSummary = semanticCmd?.toBadgeString()
        val semanticSavings = if (semanticCmd != null) (rawBytes.size - SemanticCommand.SIZE_BYTES).coerceAtLeast(0) else null

        val hasLoc = location != null
        val (payloadBytes, flagMask) = if (semanticCmd != null) {
            Pair(semanticCmd.serialize(), Packet.FLAG_SEMANTIC)
        } else {
            val compressionResult = AdaptiveCompressor.compress(rawBytes)
            Pair(compressionResult.bytes, if (compressionResult.isCompressed) Packet.FLAG_COMPRESSED else 0)
        }

        val flags = (flagMask or (if (hasLoc) Packet.FLAG_HAS_LOCATION else 0)).toByte()
        val messageId = UUID.randomUUID().toString()
        val isFragmented = PacketFragmenter.needsFragmentation(payloadBytes.size)
        val transferId = (transferSequence.getAndIncrement() and 0x7FFF).toShort()

        val fragments = if (isFragmented) {
            fragmenter.fragment(
                payload = payloadBytes,
                transferId = transferId,
                originalMsgType = Packet.TYPE_DISTRESS,
                originalFlags = flags
            )
        } else emptyList()

        var totalWireBytes = 0
        val tSendStart = BenchmarkClock.nowNanos()

        val sentSuccess = if (isFragmented) {
            val key = NetworkKeyManager.getKey()
            val signedPackets = ArrayList<Packet>(fragments.size)
            for (frag in fragments) {
                val fragPacket = Packet(
                    version = Packet.PROTOCOL_VERSION,
                    msgType = Packet.TYPE_DISTRESS,
                    priority = MessagePriority.DISTRESS,
                    flags = (Packet.FLAG_FRAGMENTED or Packet.FLAG_REQUIRES_ACK or (if (hasLoc) Packet.FLAG_HAS_LOCATION else 0)).toByte(),
                    sequenceNumber = sequenceCounter.getAndIncrement().toShort(),
                    timestamp = System.currentTimeMillis(),
                    sourceDeviceId = localDeviceId,
                    destinationDeviceId = Packet.BROADCAST_ID,
                    language = lang,
                    payload = frag.toPayload(),
                    location = location
                )
                val signedFragPacket = if (key != null) {
                    val (signed, genNanos) = PacketAuthenticator.signWithLatency(fragPacket, key)
                    DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                    signed
                } else fragPacket
                signedPackets.add(signedFragPacket)
                totalWireBytes += PacketSerializer.serialize(signedFragPacket).size
            }
            pendingTransferTracker.registerTransfer(
                transferId = transferId,
                messageId = messageId,
                destinationDeviceId = Packet.BROADCAST_ID,
                fragmentCount = fragments.size,
                payloadBytes = payloadBytes.size,
                totalWireBytes = totalWireBytes
            )
            DiagnosticsRepository.recordFragmentsSent(
                count = fragments.size,
                payloadBytes = payloadBytes.size,
                wireBytes = totalWireBytes,
                transferId = transferId
            )
            qosScheduler.sendBatch(signedPackets)
        } else {
            val key = NetworkKeyManager.getKey()
            val rawPacket = Packet(
                version = Packet.PROTOCOL_VERSION,
                msgType = Packet.TYPE_DISTRESS,
                priority = MessagePriority.DISTRESS,
                flags = flags,
                sequenceNumber = transferId,
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = localDeviceId,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = lang,
                payload = payloadBytes,
                location = location,
                semanticCommand = semanticCmd
            )
            val signedPacket = if (key != null) {
                val (signed, genNanos) = PacketAuthenticator.signWithLatency(rawPacket, key)
                DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                signed
            } else rawPacket
            val serializedBytes = PacketSerializer.serialize(signedPacket)
            totalWireBytes = serializedBytes.size
            onPacketActivity?.invoke("DISTRESS", "DISTRESS SENT (P3, loc=$hasLoc)", localDeviceId, Packet.BROADCAST_ID, MessagePriority.DISTRESS.name, signedPacket)
            qosScheduler.send(signedPacket)
        }

        val tSendEnd = BenchmarkClock.nowNanos()
        val transportLatencyMs = BenchmarkClock.elapsedMs(tSendStart, tSendEnd)

        DiagnosticsRepository.recordTransmission(
            packetBytes = totalWireBytes,
            rawAudioBytes = 0L,
            latency = LatencyMetrics(transportLatencyMs = transportLatencyMs, language = lang),
            bandwidth = BandwidthMetrics(
                transmittedPacketBytes = totalWireBytes.toLong(),
                utf8Bytes = rawBytes.size,
                isCompressed = (flags.toInt() and Packet.FLAG_COMPRESSED) != 0
            )
        )
        DiagnosticsRepository.recordDistressSent(hasLocation = hasLoc, seq = transferId)

        val hasAuthKey = NetworkKeyManager.hasKey()
        MessageHistoryStore.addRecord(
            MessageRecord(
                id = messageId,
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.SENT,
                language = lang,
                priority = MessagePriority.DISTRESS,
                text = if (isSemantic) semanticCmd!!.toDisplayString() else messageText,
                peer = "Broadcast",
                packetSizeBytes = totalWireBytes,
                rawAudioEquivalentBytes = 0L,
                measuredLatencyMs = transportLatencyMs,
                location = location,
                isSemantic = isSemantic,
                semanticSummary = semanticSummary,
                semanticSavingsBytes = semanticSavings,
                deliveryStatus = if (isFragmented) DeliveryStatus.PENDING else DeliveryStatus.NONE,
                transferId = if (isFragmented) transferId else null,
                fragmentCount = if (isFragmented) fragments.size else null,
                isSecure = hasAuthKey,
                authStatus = if (hasAuthKey) "AUTH ✓" else "UNVERIFIED",
                qosStatus = "PRIORITY 1",
                representationMode = if (isSemantic) "SEMANTIC" else "FULL"
            )
        )

        Log.i(tag, "Transmitted DISTRESS: '$messageText' (semantic=$isSemantic, locAttached=$hasLoc, frags=${if (isFragmented) fragments.size else 1}) | Wire: ${totalWireBytes}B")
        return sentSuccess
    }

    /**
     * Incoming Pipeline: Transport -> Mesh Relay / Protocol Decode -> TTS Synthesize -> Playback
     */
    private suspend fun handleIncomingPacket(packet: Packet) {
        // Drop self packets immediately (local echo / reflected broadcast)
        if (packet.sourceDeviceId == localDeviceId) {
            return
        }

        // 0. Periodically prune expired outgoing and reassembly transfers
        val timedOutTransfers = pendingTransferTracker.pruneExpired()
        for (t in timedOutTransfers) {
            MessageHistoryStore.updateRecordDelivery(t.transferId, DeliveryStatus.TIMEOUT)
        }
        val timedOutReassemblies = reassemblyBuffer.pruneExpired()
        if (timedOutReassemblies > 0) {
            DiagnosticsRepository.recordReassemblyTimeout()
        }

        // 1. Security Verification: Authentication & Anti-Replay Filter
        val key = NetworkKeyManager.getKey()
        val isAuthPacket = packet.isAuthenticated

        val (isVerified, authStatusLabel) = when {
            isAuthPacket -> {
                val authResult = PacketAuthenticator.verify(packet, key)
                when (authResult.status) {
                    AuthStatus.VALID -> {
                        val verifyMicros = authResult.elapsedNanos / 1000.0
                        DiagnosticsRepository.recordAuthPacketReceived(verifyMicros)
                        Pair(true, "AUTH ✓")
                    }
                    AuthStatus.INVALID_TAG -> {
                        DiagnosticsRepository.recordAuthFailure()
                        Log.w(tag, "AUTH FAILED: Bad HMAC tag from source=${packet.sourceDeviceId}, seq=${packet.sequenceNumber}")
                        return
                    }
                    AuthStatus.MALFORMED_TAG -> {
                        DiagnosticsRepository.recordAuthFailure()
                        Log.w(tag, "MALFORMED AUTH: Invalid tag length from source=${packet.sourceDeviceId}")
                        return
                    }
                    AuthStatus.UNKNOWN_KEY -> {
                        DiagnosticsRepository.recordUnknownKeyDrop()
                        Log.w(tag, "AUTH FAILED: Unknown key / unprovisioned node")
                        return
                    }
                    AuthStatus.MISSING_TAG -> {
                        DiagnosticsRepository.recordAuthFailure()
                        Log.w(tag, "AUTH FAILED: Missing auth tag")
                        return
                    }
                }
            }
            else -> {
                if (_securityMode.value == SecurityMode.STRICT) {
                    DiagnosticsRepository.recordAuthFailure()
                    Log.w(tag, "AUTH FAILED: Unauthenticated packet rejected in STRICT security mode (source=${packet.sourceDeviceId}, seq=${packet.sequenceNumber})")
                    return
                } else {
                    Pair(false, "UNVERIFIED")
                }
            }
        }

        // Anti-Replay Sliding Window Check
        val replayResult = antiReplayFilter.checkAndRecord(packet.sourceDeviceId, packet.sequenceNumber)
        if (!replayResult.isAccepted) {
            DiagnosticsRepository.recordReplayDrop()
            Log.w(tag, "REPLAY DROPPED: status=${replayResult.status} source=${packet.sourceDeviceId} seq=${packet.sequenceNumber}")
            return
        }

        // 2. DELIVERY RECEIPT ACK handling — if packet is TYPE_ACK, correlate and consume
        if (packet.msgType == Packet.TYPE_ACK) {
            val receipt = DeliveryReceipt.deserialize(packet.payload)
            if (receipt != null) {
                manetRouter.dtnStore.remove(receipt.transferId)
                val tracked = pendingTransferTracker.onReceiptReceived(receipt)
                if (tracked != null) {
                    MessageHistoryStore.updateRecordDelivery(receipt.transferId, DeliveryStatus.DELIVERED, tracked.deliveryRttMs)
                    DiagnosticsRepository.recordDeliveryAckReceived(receipt.transferId, tracked.deliveryRttMs ?: 0L)
                    onPacketActivity?.invoke("ACK", "ACK <- Node ${packet.sourceDeviceId} (0x${Integer.toHexString(receipt.transferId.toInt() and 0xFFFF)})", packet.sourceDeviceId, localDeviceId, null, packet)
                    Log.i(tag, "Delivery ACK confirmed for transfer 0x${Integer.toHexString(receipt.transferId.toInt() and 0xFFFF)} in ${tracked.deliveryRttMs}ms")
                }
            }
            return
        }

        // 3. MANET control packets — dispatch and return; do NOT deliver as voice/TTS
        val isControl = manetRouter.handleControlPacket(packet)
        if (isControl) return

        // 4. DATA / ALERT / DISTRESS — relay evaluation
        val decision = relayRouter.evaluatePacket(packet)
        when (decision) {
            is RelayAction.DropSelf -> {
                return
            }
            is RelayAction.DropDuplicate -> {
                DiagnosticsRepository.recordRelayDuplicateDrop()
                return
            }
            is RelayAction.ForwardAndDeliver -> {
                DiagnosticsRepository.recordRelayForward()
                onPacketActivity?.invoke("RELAY", "RELAY Node ${packet.sourceDeviceId} -> ${packet.destinationDeviceId} (ttl=${decision.forwardedPacket.ttl})", packet.sourceDeviceId, packet.destinationDeviceId, packet.priority.name, decision.forwardedPacket)
                // Asynchronously forward the packet to the next hop over active transport
                scope.launch {
                    try {
                        transportManager.send(decision.forwardedPacket)
                        Log.i(tag, "Relayed packet forwarded: seq=${decision.forwardedPacket.sequenceNumber}, ttl=${decision.forwardedPacket.ttl}")
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to relay forwarded packet: ${e.message}", e)
                    }
                }
                // Proceed to local playback and transcript
            }
            is RelayAction.DeliverLocalOnly -> {
                if (packet.ttl <= 1) {
                    DiagnosticsRepository.recordRelayTtlExpired()
                }
                // Proceed to local playback and transcript
            }
        }

        // 5. Fragmentation Reassembly or Single-Packet Extraction
        val effectivePayload: ByteArray
        val effectiveFlags: Byte
        val effectiveMsgType: Byte
        val fragmentCountForLog: Int?

        val deliveryKey: String

        if (packet.isFragmented) {
            DiagnosticsRepository.recordFragmentReceived()
            val fragment = PacketFragment.fromPayload(packet.payload)
            if (fragment == null) {
                Log.w(tag, "Malformed fragment payload rejected from Node #${packet.sourceDeviceId}")
                return
            }
            val result = reassemblyBuffer.addFragment(packet.sourceDeviceId, fragment)
            if (result == null) {
                // Incomplete transfer; awaiting remaining fragments
                return
            }
            effectivePayload = result.payload
            effectiveFlags = result.originalFlags
            effectiveMsgType = result.originalMsgType
            fragmentCountForLog = result.fragmentCount
            deliveryKey = "${packet.sourceDeviceId}_transfer_${result.transferId}"

            DiagnosticsRepository.recordMessageReassembled(result.fragmentCount, result.reassemblyTimeMs.toDouble(), result.transferId)

            // Send single DELIVERY_RECEIPT ACK packet back to sender
            val ackPayload = DeliveryReceipt(result.transferId, DeliveryReceipt.STATUS_DELIVERED).serialize()
            val ackPacket = Packet(
                version = Packet.PROTOCOL_VERSION,
                msgType = Packet.TYPE_ACK,
                priority = packet.priority,
                flags = 0,
                sequenceNumber = sequenceCounter.getAndIncrement().toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = localDeviceId,
                destinationDeviceId = packet.sourceDeviceId,
                language = packet.language,
                payload = ackPayload
            )
            val signedAckPacket = if (key != null) {
                val (signed, genNanos) = PacketAuthenticator.signWithLatency(ackPacket, key)
                DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                signed
            } else ackPacket
            scope.launch {
                try {
                    val ok = qosScheduler.send(signedAckPacket)
                    if (ok) {
                        DiagnosticsRepository.recordDeliveryAckSent()
                    }
                    Log.i(tag, "Sent DELIVERY_RECEIPT for transfer 0x${Integer.toHexString(result.transferId.toInt() and 0xFFFF)} to Node #${packet.sourceDeviceId}")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send delivery receipt: ${e.message}", e)
                }
            }
        } else {
            effectivePayload = packet.payload
            effectiveFlags = packet.flags
            effectiveMsgType = packet.msgType
            fragmentCountForLog = null
            deliveryKey = "${packet.sourceDeviceId}_seq_${packet.sequenceNumber}"

            if (packet.requiresAck) {
                val ackPayload = DeliveryReceipt(packet.sequenceNumber, DeliveryReceipt.STATUS_DELIVERED).serialize()
                val ackPacket = Packet(
                    version = Packet.PROTOCOL_VERSION,
                    msgType = Packet.TYPE_ACK,
                    priority = packet.priority,
                    flags = 0,
                    sequenceNumber = sequenceCounter.getAndIncrement().toShort(),
                    timestamp = System.currentTimeMillis(),
                    sourceDeviceId = localDeviceId,
                    destinationDeviceId = packet.sourceDeviceId,
                    language = packet.language,
                    payload = ackPayload
                )
                val signedAckPacket = if (key != null) {
                    val (signed, genNanos) = PacketAuthenticator.signWithLatency(ackPacket, key)
                    DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                    signed
                } else ackPacket
                scope.launch {
                    try {
                        val ok = qosScheduler.send(signedAckPacket)
                        if (ok) {
                            DiagnosticsRepository.recordDeliveryAckSent()
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to send unfragmented delivery receipt: ${e.message}", e)
                    }
                }
            }
        }

        if (isAlreadyDelivered(deliveryKey)) {
            Log.i(tag, "Message $deliveryKey already delivered locally — suppressed duplicate TTS and transcript")
            return
        }

        val tRx = BenchmarkClock.nowNanos()
        stateMachine.transitionTo(PttState.RECEIVED)

        val isSemantic: Boolean
        val displayText: String
        val ttsSpeechText: String
        val semanticSummary: String?

        val hasSemantic = (effectiveFlags.toInt() and 0xFF and Packet.FLAG_SEMANTIC) != 0 || packet.semanticCommand != null
        val isCompact = (effectiveFlags.toInt() and 0xFF and Packet.FLAG_COMPACT) != 0
        val isCompressed = (effectiveFlags.toInt() and 0xFF and Packet.FLAG_COMPRESSED) != 0

        val repMode: String
        if (hasSemantic) {
            val cmd = packet.semanticCommand ?: SemanticCommand.deserialize(effectivePayload)
            if (cmd != null) {
                isSemantic = true
                displayText = cmd.toDisplayString()
                ttsSpeechText = cmd.toTtsText(packet.language)
                semanticSummary = cmd.toBadgeString()
                repMode = "SEMANTIC"
            } else {
                val decompressedBytes = AdaptiveCompressor.decompress(effectivePayload, isCompressed)
                displayText = String(decompressedBytes, Charsets.UTF_8)
                ttsSpeechText = displayText
                isSemantic = false
                semanticSummary = null
                repMode = if (isCompact) "COMPACT" else "FULL"
            }
        } else if (isCompact) {
            val decompressedBytes = AdaptiveCompressor.decompress(effectivePayload, isCompressed)
            displayText = String(decompressedBytes, Charsets.UTF_8)
            ttsSpeechText = displayText
            isSemantic = false
            semanticSummary = null
            repMode = "COMPACT"
        } else {
            val decompressedBytes = AdaptiveCompressor.decompress(effectivePayload, isCompressed)
            displayText = String(decompressedBytes, Charsets.UTF_8)
            ttsSpeechText = displayText
            isSemantic = false
            semanticSummary = null
            repMode = "FULL"
        }

        _lastReceivedText.value = displayText

        val isUrgent = packet.priority.isEmergency
        stateMachine.transitionTo(PttState.TTS_PROCESSING)

        if (!tts.isReadyForLanguage(packet.language)) {
            Log.i(tag, "Incoming packet for ${packet.language.displayName} received while TTS is initializing. Awaiting readiness...")
            tts.awaitReady(packet.language, 15000L)
        }

        val tTtsStart = BenchmarkClock.nowNanos()
        stateMachine.transitionTo(PttState.PLAYING)
        tts.synthesize(ttsSpeechText, packet.language, isUrgent)
        val tTtsEnd = BenchmarkClock.nowNanos()

        val ttsLatencyMs = BenchmarkClock.elapsedMs(tTtsStart, tTtsEnd)
        DiagnosticsRepository.recordReception(effectivePayload.size + Packet.HEADER_SIZE_BYTES + Packet.CRC_SIZE_BYTES + (if (packet.isAuthenticated) Packet.AUTH_TAG_SIZE_BYTES else 0))
        onPacketActivity?.invoke("RX", "RX <- Node ${packet.sourceDeviceId} (${packet.priority.name})", packet.sourceDeviceId, localDeviceId, packet.priority.name, packet)

        val hopCount = (Packet.DEFAULT_TTL - packet.ttl).coerceAtLeast(0)
        val peerLabel = if (packet.isForwarded || hopCount > 0) {
            "Node #${packet.sourceDeviceId} [HOP $hopCount]"
        } else {
            "Node #${packet.sourceDeviceId}"
        }

        if (packet.msgType == Packet.TYPE_DISTRESS || packet.priority == MessagePriority.DISTRESS) {
            DiagnosticsRepository.recordDistressReceived(
                hasLocation = packet.hasLocation,
                source = packet.sourceDeviceId,
                hops = hopCount,
                seq = packet.sequenceNumber
            )
        }

        MessageHistoryStore.addRecord(
            MessageRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.RECEIVED,
                language = packet.language,
                priority = packet.priority,
                text = displayText,
                peer = peerLabel,
                packetSizeBytes = effectivePayload.size + Packet.MIN_PACKET_SIZE + (if (packet.hasLocation) Packet.LOCATION_SIZE_BYTES else 0) + (if (packet.isAuthenticated) Packet.AUTH_TAG_SIZE_BYTES else 0),
                rawAudioEquivalentBytes = 0L,
                measuredLatencyMs = ttsLatencyMs,
                isRelayed = packet.isForwarded || hopCount > 0,
                hopCount = hopCount,
                location = packet.location,
                isSemantic = isSemantic,
                semanticSummary = semanticSummary,
                fragmentCount = fragmentCountForLog,
                isSecure = isVerified,
                authStatus = authStatusLabel,
                representationMode = repMode
            )
        )

        Log.i(tag, "Received '${displayText}' (${packet.language}) [Mode=$repMode] from Node #${packet.sourceDeviceId} (Auth=$authStatusLabel, Semantic=$isSemantic, Priority=${packet.priority}, Relayed=${packet.isForwarded}, Hop=$hopCount, Frags=$fragmentCountForLog)")

        if (_isContinuousMode.value) {
            stateMachine.transitionTo(PttState.RECORDING)
        } else {
            stateMachine.reset()
        }
    }

    /**
     * Sends an immediate high-priority Emergency Distress / Alert broadcast.
     */
    fun sendAlert(
        alertText: String,
        isDistress: Boolean = false,
        mode: AdaptiveRepresentationMode? = null
    ) {
        scope.launch {
            val priority = if (isDistress) MessagePriority.DISTRESS else MessagePriority.ALERT
            val lang = _activeLanguage.value
            val cleanText = SentenceFinalizer.finalizeSentence(alertText, lang)
            val currentNet = resolveCurrentNetworkMode()
            val rep = AdaptiveRepresentationPolicy.select(
                text = cleanText,
                networkMode = currentNet,
                language = lang,
                semanticConfidence = 0.90f,
                forceMode = mode
            )

            val flagVal: Byte = when (rep.mode) {
                AdaptiveRepresentationMode.SEMANTIC -> Packet.FLAG_SEMANTIC.toByte()
                AdaptiveRepresentationMode.COMPACT -> {
                    val base = Packet.FLAG_COMPACT
                    val comp = if (rep.isCompressed) Packet.FLAG_COMPRESSED else 0
                    (base or comp).toByte()
                }
                else -> if (rep.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte()
            }

            val rawPacket = Packet(
                version = Packet.PROTOCOL_VERSION,
                msgType = Packet.TYPE_ALERT,
                priority = priority,
                flags = flagVal,
                sequenceNumber = sequenceCounter.getAndIncrement().toShort(),
                timestamp = System.currentTimeMillis(),
                sourceDeviceId = localDeviceId,
                destinationDeviceId = Packet.BROADCAST_ID,
                language = lang,
                payload = rep.payloadBytes,
                semanticCommand = rep.semanticCommand
            )

            val key = NetworkKeyManager.getKey()
            val signedPacket = if (key != null) {
                val (signed, genNanos) = PacketAuthenticator.signWithLatency(rawPacket, key)
                DiagnosticsRepository.recordAuthPacketSent(genNanos / 1000.0)
                signed
            } else rawPacket

            onPacketActivity?.invoke("DISTRESS", "EMERGENCY ALERT [${rep.mode}]: ${rep.text.take(20)}", localDeviceId, Packet.BROADCAST_ID, priority.name, signedPacket)
            transportManager.send(signedPacket)
            MessageHistoryStore.addRecord(
                MessageRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    direction = MessageDirection.SENT,
                    language = lang,
                    priority = priority,
                    text = rep.text,
                    peer = "Emergency Broadcast",
                    packetSizeBytes = signedPacket.payload.size + Packet.MIN_PACKET_SIZE + (if (signedPacket.isAuthenticated) Packet.AUTH_TAG_SIZE_BYTES else 0),
                    rawAudioEquivalentBytes = 0L,
                    measuredLatencyMs = 12.0,
                    isSemantic = rep.mode == AdaptiveRepresentationMode.SEMANTIC,
                    semanticSummary = rep.semanticCommand?.toBadgeString(),
                    semanticSavingsBytes = if (rep.mode == AdaptiveRepresentationMode.SEMANTIC) (cleanText.toByteArray(Charsets.UTF_8).size - SemanticCommand.SIZE_BYTES).coerceAtLeast(0) else null,
                    isSecure = key != null,
                    authStatus = if (key != null) "AUTH ✓" else "UNVERIFIED",
                    representationMode = rep.mode.name
                )
            )
        }
    }
}
