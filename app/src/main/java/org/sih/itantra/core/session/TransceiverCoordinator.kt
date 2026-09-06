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
    private val transferSequence = AtomicInteger(1)
    private val localDeviceId: Int = (Math.random() * 900000 + 100000).toInt()
    val relayRouter = PacketRelayRouter(localDeviceId)
    val manetRouter = ManetRouter(
        context          = context,
        localNodeId      = localDeviceId,
        transportManager = transportManager,
        relayRouter      = relayRouter,
        scope            = scope
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

        stateMachine.transitionTo(PttState.MESSAGE_ENCODED)
        val tEncodeStart = BenchmarkClock.nowNanos()

        val rawBytes = finalText.toByteArray(Charsets.UTF_8)
        val semanticCmd = SemanticEmergencyClassifier.classify(finalText)

        val isSemantic = semanticCmd != null
        val semanticSummary = semanticCmd?.toBadgeString()
        val semanticSavings = if (semanticCmd != null) (rawBytes.size - SemanticCommand.SIZE_BYTES).coerceAtLeast(0) else null

        val (packetPayload, flags, effectivePriority) = if (semanticCmd != null) {
            val cmdBytes = semanticCmd.serialize()
            val flagVal = Packet.FLAG_SEMANTIC.toByte()
            val prio = when (semanticCmd.severity) {
                EmergencySeverity.CRITICAL -> MessagePriority.DISTRESS
                EmergencySeverity.ALERT -> MessagePriority.ALERT
                EmergencySeverity.IMPORTANT -> MessagePriority.IMPORTANT
                EmergencySeverity.NORMAL -> priority
            }
            Log.i(tag, "SEMANTIC COMPRESSION: '$finalText' -> ${semanticCmd.toBadgeString()} (${rawBytes.size}B -> ${cmdBytes.size}B, -$semanticSavings B)")
            Triple(cmdBytes, flagVal, prio)
        } else {
            val compressionResult = AdaptiveCompressor.compress(rawBytes)
            val flagVal = if (compressionResult.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte()
            Triple(compressionResult.bytes, flagVal, priority)
        }

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
            var allSent = true
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
                val ok = transportManager.send(fragPacket)
                allSent = allSent && ok
                totalWireBytes += PacketSerializer.serialize(fragPacket).size
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
            allSent
        } else {
            val packet = Packet(
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
            val ok = transportManager.send(packet)
            totalWireBytes = PacketSerializer.serialize(packet).size
            ok
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

        MessageHistoryStore.addRecord(
            MessageRecord(
                id = messageId,
                timestamp = System.currentTimeMillis(),
                direction = MessageDirection.SENT,
                language = lang,
                priority = effectivePriority,
                text = if (isSemantic) semanticCmd!!.toDisplayString() else finalText,
                peer = "Broadcast",
                packetSizeBytes = totalWireBytes,
                rawAudioEquivalentBytes = rawAudioBytes,
                measuredLatencyMs = sttLatencyMs + encodingLatencyMs + transportLatencyMs,
                isSemantic = isSemantic,
                semanticSummary = semanticSummary,
                semanticSavingsBytes = semanticSavings,
                deliveryStatus = if (isFragmented) DeliveryStatus.PENDING else DeliveryStatus.NONE,
                transferId = if (isFragmented) transferId else null,
                fragmentCount = if (isFragmented) fragments.size else null
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
            var allSent = true
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
                val ok = if (manetRouter.isEnabled.value) {
                    manetRouter.routeAndSend(fragPacket)
                } else {
                    transportManager.send(fragPacket)
                }
                allSent = allSent && ok
                totalWireBytes += PacketSerializer.serialize(fragPacket).size
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
            allSent
        } else {
            val packet = Packet(
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
            val ok = if (manetRouter.isEnabled.value) {
                manetRouter.routeAndSend(packet)
            } else {
                transportManager.send(packet)
            }
            val serializedBytes = PacketSerializer.serialize(packet)
            totalWireBytes = serializedBytes.size
            ok
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
                fragmentCount = if (isFragmented) fragments.size else null
            )
        )

        Log.i(tag, "Transmitted DISTRESS: '$messageText' (semantic=$isSemantic, locAttached=$hasLoc, frags=${if (isFragmented) fragments.size else 1}) | Wire: ${totalWireBytes}B")
        return sentSuccess
    }

    /**
     * Incoming Pipeline: Transport -> Mesh Relay / Protocol Decode -> TTS Synthesize -> Playback
     */
    private suspend fun handleIncomingPacket(packet: Packet) {
        // 0. Periodically prune expired outgoing and reassembly transfers
        val timedOutTransfers = pendingTransferTracker.pruneExpired()
        for (t in timedOutTransfers) {
            MessageHistoryStore.updateRecordDelivery(t.transferId, DeliveryStatus.TIMEOUT)
        }
        val timedOutReassemblies = reassemblyBuffer.pruneExpired()
        if (timedOutReassemblies > 0) {
            DiagnosticsRepository.recordReassemblyTimeout()
        }

        // 1. DELIVERY RECEIPT ACK handling — if packet is TYPE_ACK, correlate and consume
        if (packet.msgType == Packet.TYPE_ACK) {
            val receipt = DeliveryReceipt.deserialize(packet.payload)
            if (receipt != null) {
                val tracked = pendingTransferTracker.onReceiptReceived(receipt)
                if (tracked != null) {
                    MessageHistoryStore.updateRecordDelivery(receipt.transferId, DeliveryStatus.DELIVERED, tracked.deliveryRttMs)
                    DiagnosticsRepository.recordDeliveryAckReceived(receipt.transferId, tracked.deliveryRttMs ?: 0L)
                    Log.i(tag, "Delivery ACK confirmed for transfer 0x${Integer.toHexString(receipt.transferId.toInt() and 0xFFFF)} in ${tracked.deliveryRttMs}ms")
                }
            }
            return
        }

        // 2. MANET control packets — dispatch and return; do NOT deliver as voice/TTS
        val isControl = manetRouter.handleControlPacket(packet)
        if (isControl) return

        // 3. DATA / ALERT / DISTRESS — relay evaluation
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

        // 4. Fragmentation Reassembly or Single-Packet Extraction
        val effectivePayload: ByteArray
        val effectiveFlags: Byte
        val effectiveMsgType: Byte
        val fragmentCountForLog: Int?

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
            scope.launch {
                try {
                    if (manetRouter.isEnabled.value) {
                        manetRouter.routeAndSend(ackPacket)
                    } else {
                        transportManager.send(ackPacket)
                    }
                    DiagnosticsRepository.recordDeliveryAckSent()
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
                scope.launch {
                    try {
                        if (manetRouter.isEnabled.value) {
                            manetRouter.routeAndSend(ackPacket)
                        } else {
                            transportManager.send(ackPacket)
                        }
                        DiagnosticsRepository.recordDeliveryAckSent()
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to send unfragmented delivery receipt: ${e.message}", e)
                    }
                }
            }
        }

        val tRx = BenchmarkClock.nowNanos()
        stateMachine.transitionTo(PttState.RECEIVED)

        val isSemantic: Boolean
        val displayText: String
        val ttsSpeechText: String
        val semanticSummary: String?

        val hasSemantic = (effectiveFlags.toInt() and Packet.FLAG_SEMANTIC) != 0 || packet.semanticCommand != null
        val isCompressed = (effectiveFlags.toInt() and Packet.FLAG_COMPRESSED) != 0

        if (hasSemantic) {
            val cmd = packet.semanticCommand ?: SemanticCommand.deserialize(effectivePayload)
            if (cmd != null) {
                isSemantic = true
                displayText = cmd.toDisplayString()
                ttsSpeechText = cmd.toTtsText(packet.language)
                semanticSummary = cmd.toBadgeString()
            } else {
                val decompressedBytes = AdaptiveCompressor.decompress(effectivePayload, isCompressed)
                displayText = String(decompressedBytes, Charsets.UTF_8)
                ttsSpeechText = displayText
                isSemantic = false
                semanticSummary = null
            }
        } else {
            val decompressedBytes = AdaptiveCompressor.decompress(effectivePayload, isCompressed)
            displayText = String(decompressedBytes, Charsets.UTF_8)
            ttsSpeechText = displayText
            isSemantic = false
            semanticSummary = null
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
        DiagnosticsRepository.recordReception(effectivePayload.size + Packet.HEADER_SIZE_BYTES + Packet.CRC_SIZE_BYTES)

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
                packetSizeBytes = effectivePayload.size + Packet.MIN_PACKET_SIZE + (if (packet.hasLocation) Packet.LOCATION_SIZE_BYTES else 0),
                rawAudioEquivalentBytes = 0L,
                measuredLatencyMs = ttsLatencyMs,
                isRelayed = packet.isForwarded || hopCount > 0,
                hopCount = hopCount,
                location = packet.location,
                isSemantic = isSemantic,
                semanticSummary = semanticSummary,
                fragmentCount = fragmentCountForLog
            )
        )

        Log.i(tag, "Received '${displayText}' (${packet.language}) from Node #${packet.sourceDeviceId} (Semantic=$isSemantic, Priority=${packet.priority}, Relayed=${packet.isForwarded}, Hop=$hopCount, Frags=$fragmentCountForLog)")

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
                    rawAudioEquivalentBytes = 0L,
                    measuredLatencyMs = 12.0
                )
            )
        }
    }
}
