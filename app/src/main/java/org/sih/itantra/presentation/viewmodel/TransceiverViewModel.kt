package org.sih.itantra.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import org.sih.itantra.core.diagnostics.DiagnosticsState
import org.sih.itantra.core.location.LocationProviderHelper
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.session.PttState
import org.sih.itantra.core.session.TransceiverCoordinator
import org.sih.itantra.core.transport.PeerDevice
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.service.ManetNodePreference
import org.sih.itantra.service.ManetNodeService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.sih.itantra.core.mesh.MeshTopologyProvider
import org.sih.itantra.core.mesh.MeshTopologySnapshot
import org.sih.itantra.core.mesh.TopologyPacketActivity
import org.sih.itantra.core.mesh.TopologyNodeRole
import org.sih.itantra.core.protocol.VoiceCommand
import org.sih.itantra.core.protocol.VoiceCommandEngine
import org.sih.itantra.core.protocol.VoiceCommandResult
import org.sih.itantra.core.protocol.VoiceCommandState
import org.sih.itantra.core.protocol.VoiceCommandStateMachine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.sih.itantra.core.demo.DemoScenario
import org.sih.itantra.core.demo.SihDemoCoordinator
import org.sih.itantra.core.demo.SihDemoState
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactIdentity
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.contact.TacticalContact
import org.sih.itantra.core.discovery.BleDiscoverySource
import org.sih.itantra.core.discovery.DeviceTrustState
import org.sih.itantra.core.discovery.MeshTopologyDiscoverySource
import org.sih.itantra.core.discovery.NearbyDevice
import org.sih.itantra.core.discovery.NearbyDeviceRepository
import org.sih.itantra.core.discovery.UwbDiscoverySource
import org.sih.itantra.core.search.LocalSearchRepository
import org.sih.itantra.core.search.SearchContactItem
import org.sih.itantra.core.search.SearchIndex

enum class VoiceEngineStatus(val label: String) {
    READY("READY"),
    LOADING("LOADING"),
    VOICE("VOICE"),
    OFFLINE("OFFLINE")
}

class TransceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "TransceiverViewModel"

    // -------------------------------------------------------------------------
    // Transceiver coordinator (UI voice pipeline — PTT/STT/TTS)
    // -------------------------------------------------------------------------

    val coordinator = TransceiverCoordinator(application.applicationContext)

    val pttState: StateFlow<PttState> = coordinator.stateMachine.state
    val activeLanguage: StateFlow<IndicLanguage> = coordinator.activeLanguage
    val isContinuousMode: StateFlow<Boolean> = coordinator.isContinuousMode
    val lastTranscribedText: StateFlow<String> = coordinator.lastTranscribedText
    val lastReceivedText: StateFlow<String> = coordinator.lastReceivedText
    val isModelReady: StateFlow<Boolean> = coordinator.isModelReady

    private val _activeTransportType = MutableStateFlow(TransportType.WIFI)
    val activeTransportType: StateFlow<TransportType> = _activeTransportType.asStateFlow()

    private val _bondedBluetoothDevices = MutableStateFlow<List<PeerDevice>>(emptyList())
    val bondedBluetoothDevices: StateFlow<List<PeerDevice>> = _bondedBluetoothDevices.asStateFlow()

    val bluetoothTransportState: StateFlow<TransportState> = coordinator.transportManager.bluetoothTransport.state
    val isAutoFailoverEnabled: StateFlow<Boolean> = coordinator.transportManager.isAutoFailoverEnabled

    fun setAutoFailoverEnabled(enabled: Boolean) {
        coordinator.transportManager.setAutoFailoverEnabled(enabled)
    }

    val messageHistory: StateFlow<List<MessageRecord>> = MessageHistoryStore.historyFlow
    val diagnosticsState: StateFlow<DiagnosticsState> = DiagnosticsRepository.state
    val isRelayEnabled: StateFlow<Boolean> = coordinator.relayRouter.isRelayEnabled

    fun setRelayEnabled(enabled: Boolean) {
        coordinator.relayRouter.setRelayEnabled(enabled)
    }

    private val _themeMode = MutableStateFlow(org.sih.itantra.presentation.theme.AppThemeMode.SYSTEM)
    val themeMode: StateFlow<org.sih.itantra.presentation.theme.AppThemeMode> = _themeMode.asStateFlow()

    private val languageIdentifier: org.sih.itantra.core.language.LanguageIdentifier =
        org.sih.itantra.core.language.OfflineLanguageIdentifier()

    private val _languageState = MutableStateFlow<org.sih.itantra.core.language.LanguageSelectionState>(
        org.sih.itantra.core.language.LanguageSelectionState.Manual(coordinator.activeLanguage.value)
    )
    val languageState: StateFlow<org.sih.itantra.core.language.LanguageSelectionState> = _languageState.asStateFlow()

    val voiceEngineStatus: StateFlow<VoiceEngineStatus> = kotlinx.coroutines.flow.combine(
        pttState,
        isModelReady
    ) { state, ready ->
        when {
            state == PttState.RECORDING || state == PttState.SPEECH_DETECTED ||
            state == PttState.STT_PROCESSING || state == PttState.TRANSMITTING ||
            state == PttState.RECEIVED || state == PttState.TTS_PROCESSING ||
            state == PttState.PLAYING -> VoiceEngineStatus.VOICE
            !ready -> VoiceEngineStatus.LOADING
            ready -> VoiceEngineStatus.READY
            else -> VoiceEngineStatus.OFFLINE
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceEngineStatus.LOADING)

    // -------------------------------------------------------------------------
    // Tactical Chats & Conversation Layer
    // -------------------------------------------------------------------------

    val chatRepository = org.sih.itantra.core.chat.ChatRepository(
        context = application.applicationContext,
        scope = viewModelScope
    )

    // -------------------------------------------------------------------------
    // Tactical Contacts Layer (Feature 3)
    // -------------------------------------------------------------------------

    val contactRepository = ContactRepository(
        context = application.applicationContext,
        scope = viewModelScope
    )

    val conversationSummaries: StateFlow<List<org.sih.itantra.core.chat.ConversationSummary>> =
        chatRepository.filteredConversations

    val chatSearchQuery: StateFlow<String> = chatRepository.searchQuery

    fun setChatSearchQuery(query: String) {
        chatRepository.setSearchQuery(query)
    }

    fun markConversationAsRead(peerId: String) {
        chatRepository.markConversationAsRead(peerId)
    }

    fun getThreadMessages(peerId: String): List<MessageRecord> {
        return chatRepository.getThreadMessages(peerId)
    }

    fun getChatHeaderState(peerId: String): org.sih.itantra.core.chat.IndividualChatHeaderState {
        return chatRepository.getHeaderState(peerId)
    }

    fun playVoiceMessage(text: String, language: IndicLanguage = activeLanguage.value) {
        viewModelScope.launch {
            coordinator.testSynthesizeAndPlay(text, language)
        }
    }

    fun sendChatMessage(peerId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            coordinator.sendAlert(text, isDistress = false)
        }
    }

    // -------------------------------------------------------------------------
    // MANET Node Mode — ServiceConnection to ManetNodeService
    // -------------------------------------------------------------------------

    private val _isNodeModeEnabled = MutableStateFlow(false)
    val isNodeModeEnabled: StateFlow<Boolean> = _isNodeModeEnabled.asStateFlow()

    private val _nodeNeighborCount = MutableStateFlow(0)
    val nodeNeighborCount: StateFlow<Int> = _nodeNeighborCount.asStateFlow()

    private val _nodeRouteCount = MutableStateFlow(0)
    val nodeRouteCount: StateFlow<Int> = _nodeRouteCount.asStateFlow()

    private val _serviceState = MutableStateFlow<ManetNodeService.ManetServiceState?>(null)
    val serviceState: StateFlow<ManetNodeService.ManetServiceState?> = _serviceState.asStateFlow()

    private var boundService: ManetNodeService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as ManetNodeService.LocalBinder).getService()
            boundService = service
            Log.i(tag, "ManetNodeService connected")

            // Forward service state into ViewModel StateFlows
            viewModelScope.launch {
                service.serviceState.collect { state ->
                    _serviceState.value = state
                    _isNodeModeEnabled.value = state.isRunning
                    _nodeNeighborCount.value = state.neighborCount
                    _nodeRouteCount.value    = state.routeCount
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
            Log.w(tag, "ManetNodeService disconnected unexpectedly")
        }
    }

    private var isBound = false

    /**
     * Bind to ManetNodeService if it is already running.
     * Call from Activity.onStart().
     */
    fun bindToServiceIfRunning() {
        val intent = Intent(getApplication(), ManetNodeService::class.java)
        val bound = getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        isBound = bound
        if (!bound) {
            // Service not running — read persisted preference for UI toggle state
            _isNodeModeEnabled.value = ManetNodePreference.isNodeModeEnabled(getApplication())
        }
    }

    /**
     * Unbind from ManetNodeService (does NOT stop the service).
     * Call from Activity.onStop().
     */
    fun unbindFromService() {
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) { }
            isBound = false
        }
    }

    /**
     * Start MANET Node Mode.
     *
     * Uses startForegroundService() — only valid when called from a foreground
     * Activity (user just tapped the toggle). This satisfies Android 12+
     * foreground-service start restrictions.
     */
    fun startNodeMode() {
        ManetNodePreference.setNodeModeEnabled(getApplication(), true)
        _isNodeModeEnabled.value = true
        val intent = Intent(getApplication(), ManetNodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        // Bind immediately so ViewModel can observe service state
        if (!isBound) bindToServiceIfRunning()
        Log.i(tag, "startNodeMode: ManetNodeService started")
    }

    /**
     * Stop MANET Node Mode cleanly.
     */
    fun stopNodeMode() {
        ManetNodePreference.setNodeModeEnabled(getApplication(), false)
        _isNodeModeEnabled.value = false
        _nodeNeighborCount.value = 0
        _nodeRouteCount.value = 0
        boundService?.stopNodeMode() ?: run {
            // Service may not be bound — stop directly
            getApplication<Application>().stopService(
                Intent(getApplication(), ManetNodeService::class.java)
            )
        }
        Log.i(tag, "stopNodeMode: ManetNodeService stopped")
    }

    // -------------------------------------------------------------------------
    // Tactical Mesh Topology & Simulation Engine
    // -------------------------------------------------------------------------

    val manetSimulator = org.sih.itantra.core.mesh.ManetSimulator()
    val topologyState = manetSimulator.topologyState

    val sihDemoCoordinator = SihDemoCoordinator(manetSimulator, viewModelScope)
    val sihDemoState: StateFlow<SihDemoState> = sihDemoCoordinator.state

    fun selectDemoScenario(scenario: DemoScenario) = sihDemoCoordinator.selectScenario(scenario)
    fun startDemo() = sihDemoCoordinator.startDemo()
    fun nextDemoStep() = sihDemoCoordinator.nextStep()
    fun runFullDemo() = sihDemoCoordinator.runFullDemo()
    fun stopAutoRun() = sihDemoCoordinator.stopAutoRun()
    fun resetDemo() = sihDemoCoordinator.resetDemo()
    fun runDemoBenchmarks() = sihDemoCoordinator.runBenchmarks()
    fun processManualDemoInput(text: String) = sihDemoCoordinator.processManualTextInput(text)

    fun refreshDemoReadiness() {
        val sttReady = if (isModelReady.value) "READY" else "NOT READY"
        val ttsReady = if (coordinator.tts.ttsState.value != org.sih.itantra.core.tts.TtsState.ERROR) "READY" else "NOT READY"
        val netReady = if (bluetoothTransportState.value != TransportState.ERROR) "READY" else "NOT READY"
        val secReady = if (org.sih.itantra.core.crypto.NetworkKeyManager.hasKey()) "READY" else "NOT READY"
        val manetReady = if (_isNodeModeEnabled.value) "RUNNING" else "STOPPED"
        val topReady = if (_nodeRouteCount.value > 0 || _meshTopologySnapshot.value.nodes.isNotEmpty()) "AVAILABLE" else "NO DATA"

        sihDemoCoordinator.updateDeviceReadiness(
            org.sih.itantra.core.demo.DeviceReadinessState(
                sttStatus = sttReady,
                ttsStatus = ttsReady,
                networkStatus = netReady,
                securityStatus = secReady,
                manetServiceStatus = manetReady,
                topologyStatus = topReady,
                demoStatus = "READY"
            )
        )
    }

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<Int?>(null)
    val selectedNodeId: StateFlow<Int?> = _selectedNodeId.asStateFlow()

    private val activityIdCounter = AtomicLong(1L)
    private val liveActivityList = java.util.ArrayDeque<TopologyPacketActivity>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _meshTopologySnapshot = MutableStateFlow(
        MeshTopologyProvider.buildLiveSnapshot(
            localNodeId = coordinator.manetRouter.localNodeId,
            neighborTable = coordinator.manetRouter.neighborTable,
            routeTable = coordinator.manetRouter.routeTable,
            transportManager = coordinator.transportManager,
            qosScheduler = coordinator.qosScheduler
        )
    )
    val meshTopologySnapshot: StateFlow<MeshTopologySnapshot> = _meshTopologySnapshot.asStateFlow()

    // -------------------------------------------------------------------------
    // Nearby Device Discovery Layer (Feature 4)
    // -------------------------------------------------------------------------

    val nearbyDeviceRepository = NearbyDeviceRepository(
        localNodeId = coordinator.manetRouter.localNodeId,
        bleSource = BleDiscoverySource(application.applicationContext),
        uwbSource = UwbDiscoverySource(application.applicationContext),
        meshSource = MeshTopologyDiscoverySource(
            localNodeId = coordinator.manetRouter.localNodeId,
            neighborTableProvider = { coordinator.manetRouter.neighborTable.liveNeighbors() },
            routeTableProvider = { _meshTopologySnapshot.value.routes }
        )
    )

    fun addContactFromNearby(device: NearbyDevice): Boolean {
        val identity = ContactIdentity(
            nodeId = device.nodeId,
            callsign = device.callsign,
            displayName = if (device.callsign.startsWith("NODE #", ignoreCase = true)) null else device.callsign,
            supportedLanguages = device.supportedLanguages,
            authStatus = when (device.trustState) {
                DeviceTrustState.AUTHENTICATED_HMAC -> ContactAuthStatus.AUTHENTICATED
                DeviceTrustState.KNOWN_CONTACT -> ContactAuthStatus.TRUSTED
                else -> ContactAuthStatus.UNVERIFIED
            }
        )
        return contactRepository.addContact(identity)
    }

    fun sendTestPacketTo(nodeId: Int) {
        viewModelScope.launch {
            val text = "Radio connection ping to Node #$nodeId"
            coordinator.sendAlert(text, isDistress = false)
        }
    }

    // -------------------------------------------------------------------------
    // Offline Global Search Layer (Feature 5)
    // -------------------------------------------------------------------------

    val searchRepository = LocalSearchRepository(
        context = application.applicationContext,
        scope = viewModelScope
    ).apply {
        setContactProvider {
            contactRepository.contacts.value.map { contact ->
                SearchContactItem(
                    nodeId = contact.nodeId,
                    callsign = contact.callsign,
                    displayName = contact.displayName,
                    supportedLanguages = contact.supportedLanguages,
                    authStatus = contact.authStatus.name,
                    notes = contact.identity.notes,
                    isOffline = contact.isOffline
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            contactRepository.contacts.collect {
                searchRepository.searchIndex.refreshContacts()
            }
        }
    }

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        _selectedNodeId.value = null
        refreshTopologySnapshot()
    }

    fun selectNode(nodeId: Int?) {
        _selectedNodeId.value = nodeId
        refreshTopologySnapshot()
    }

    fun logPacketActivity(
        type: String,
        description: String,
        sourceId: Int? = null,
        destId: Int? = null,
        priority: String? = null,
        rawPacket: org.sih.itantra.core.protocol.Packet? = null
    ) {
        val now = System.currentTimeMillis()
        val activity = TopologyPacketActivity(
            id = activityIdCounter.getAndIncrement(),
            timestampMs = now,
            timeFormatted = timeFormat.format(Date(now)),
            type = type,
            description = description,
            sourceId = sourceId,
            destId = destId,
            priorityLabel = priority,
            rawPacket = rawPacket
        )
        synchronized(liveActivityList) {
            liveActivityList.addFirst(activity)
            while (liveActivityList.size > 50) {
                liveActivityList.removeLast()
            }
        }
        refreshTopologySnapshot()
    }

    fun refreshTopologySnapshot() {
        val snapshot = if (_isSimulationMode.value) {
            MeshTopologyProvider.buildSimulationSnapshot(
                simState = manetSimulator.topologyState.value,
                selectedNodeId = _selectedNodeId.value
            )
        } else {
            val events = synchronized(liveActivityList) { liveActivityList.toList() }
            val snap = MeshTopologyProvider.buildLiveSnapshot(
                localNodeId = coordinator.manetRouter.localNodeId,
                neighborTable = coordinator.manetRouter.neighborTable,
                routeTable = coordinator.manetRouter.routeTable,
                transportManager = coordinator.transportManager,
                qosScheduler = coordinator.qosScheduler,
                dtnPendingCount = coordinator.manetRouter.dtnStore.size(),
                activeDistressDestinationId = null,
                selectedNodeId = _selectedNodeId.value,
                activityEvents = events,
                batteryPct = null
            )
            DiagnosticsRepository.updateTopologyMetrics(
                knownNodes = snap.nodes.size,
                activeNeighbors = snap.nodes.count { it.role == TopologyNodeRole.NEIGHBOR },
                activeRoutes = snap.routes.size,
                reachableDestinations = snap.nodes.count { it.role == TopologyNodeRole.DESTINATION && it.isReachable },
                currentTransport = snap.activeTransport
            )
            snap
        }
        _meshTopologySnapshot.value = snapshot
        chatRepository.updateTopology(snapshot)
        contactRepository.updateTopology(snapshot)
        searchRepository.updateTopology(snapshot)
        nearbyDeviceRepository.localNodeId = coordinator.manetRouter.localNodeId
    }

    fun simStartDiscovery() {
        manetSimulator.startDiscovery()
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    fun simSendPacketAtoC(msg: String = "iTantra Voice Packet (Compressed INT8)") {
        manetSimulator.sendPacketAtoC(msg)
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    fun simFailNodeB() {
        manetSimulator.failNodeB()
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    fun simTriggerFailureAndRediscovery() {
        manetSimulator.triggerFailureAndRediscovery()
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    fun simRepairNodeB() {
        manetSimulator.repairNodeB()
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    fun simResetTopology() {
        manetSimulator.resetTopology()
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    fun simSelectPacket(packet: org.sih.itantra.core.protocol.Packet?) {
        manetSimulator.selectPacket(packet)
        if (_isSimulationMode.value) refreshTopologySnapshot()
    }

    // -------------------------------------------------------------------------
    // Voice Command Control Layer
    // -------------------------------------------------------------------------

    val voiceCommandStateMachine = VoiceCommandStateMachine()

    private val _voiceCommandState = MutableStateFlow(VoiceCommandState.IDLE)
    val voiceCommandState: StateFlow<VoiceCommandState> = _voiceCommandState.asStateFlow()

    private val _voiceCommandStatusLabel = MutableStateFlow("VOICE CONTROL READY")
    val voiceCommandStatusLabel: StateFlow<String> = _voiceCommandStatusLabel.asStateFlow()

    private val _lastVoiceCommandLabel = MutableStateFlow<String?>(null)
    val lastVoiceCommandLabel: StateFlow<String?> = _lastVoiceCommandLabel.asStateFlow()

    /** Command staged for safety confirmation (DISTRESS, ALERT, STOP, CANCEL). */
    private var _pendingVoiceCommand: VoiceCommandResult? = null

    /** Callbacks wired by the UI to navigate to other screens. */
    var onVoiceCommandNavigateTopology: (() -> Unit)? = null
    var onVoiceCommandNavigateDiagnostics: (() -> Unit)? = null

    /** Registers execution metrics timing for the last executed voice command. */
    private val _lastVoiceCommandExecLatencyMs = MutableStateFlow(0L)
    val lastVoiceCommandExecLatencyMs: StateFlow<Long> = _lastVoiceCommandExecLatencyMs.asStateFlow()

    private val _lastVoiceCommandDecisionLatencyMs = MutableStateFlow(0L)
    val lastVoiceCommandDecisionLatencyMs: StateFlow<Long> = _lastVoiceCommandDecisionLatencyMs.asStateFlow()

    /**
     * Handle a detected voice command result from VoiceCommandEngine.
     * Called on the coordinator's dispatcher — dispatches to viewModelScope for UI work.
     * Returns true = command consumed (suppress normal message TX), false = pass through.
     */
    private fun handleVoiceCommandResult(result: VoiceCommandResult): Boolean {
        _lastVoiceCommandDecisionLatencyMs.value = result.decisionLatencyMs
        _lastVoiceCommandLabel.value = result.command?.name

        if (result.requiresConfirmation) {
            _pendingVoiceCommand = result
            val cmdName = result.command?.name ?: "?"
            _voiceCommandState.value = VoiceCommandState.AWAITING_CONFIRMATION
            _voiceCommandStatusLabel.value = "CONFIRM $cmdName?"
            android.util.Log.i("TransceiverViewModel", "Voice command awaiting confirmation: $cmdName")
            return true  // consumed; don't transmit as message
        }

        // Execute immediately
        viewModelScope.launch {
            val execStart = System.currentTimeMillis()
            dispatchVoiceCommand(result.command!!, result.language)
            _lastVoiceCommandExecLatencyMs.value = System.currentTimeMillis() - execStart
            DiagnosticsRepository.recordVoiceCommandExecuted(result.command, result.language)
            _voiceCommandState.value = VoiceCommandState.IDLE
            _voiceCommandStatusLabel.value = "VOICE CONTROL READY"
        }
        return true
    }

    /**
     * Confirms a staged voice command (DISTRESS, ALERT, STOP, CANCEL).
     * Call from UI confirm button.
     */
    fun confirmVoiceCommand() {
        val pending = _pendingVoiceCommand ?: return
        _pendingVoiceCommand = null
        _voiceCommandState.value = VoiceCommandState.EXECUTING
        _voiceCommandStatusLabel.value = "EXECUTING ${pending.command?.name}"
        viewModelScope.launch {
            val execStart = System.currentTimeMillis()
            dispatchVoiceCommand(pending.command!!, pending.language)
            _lastVoiceCommandExecLatencyMs.value = System.currentTimeMillis() - execStart
            DiagnosticsRepository.recordVoiceCommandExecuted(pending.command, pending.language)
            _voiceCommandState.value = VoiceCommandState.IDLE
            _voiceCommandStatusLabel.value = "VOICE CONTROL READY"
        }
    }

    /**
     * Rejects a staged voice command.
     * Call from UI reject/dismiss button.
     */
    fun rejectVoiceCommand() {
        _pendingVoiceCommand = null
        _voiceCommandState.value = VoiceCommandState.IDLE
        _voiceCommandStatusLabel.value = "COMMAND REJECTED"
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_voiceCommandStatusLabel.value == "COMMAND REJECTED") {
                _voiceCommandStatusLabel.value = "VOICE CONTROL READY"
            }
        }
    }

    /**
     * Dispatches an already-confirmed voice command to the existing action layer.
     * Reuses all existing pipelines — no duplicate logic.
     */
    private suspend fun dispatchVoiceCommand(command: VoiceCommand, language: org.sih.itantra.core.common.IndicLanguage) {
        android.util.Log.i("TransceiverViewModel", "Executing voice command: $command (lang=${language.displayName})")
        _voiceCommandStatusLabel.value = "EXECUTING: ${command.name}"
        when (command) {
            VoiceCommand.SEND -> {
                // Normal PTT send — transcript already processed; nothing to re-send
            }
            VoiceCommand.CANCEL -> {
                coordinator.stopPtt()
            }
            VoiceCommand.DISTRESS -> {
                sendDistress()
            }
            VoiceCommand.ALERT -> {
                val alertText = when (language) {
                    org.sih.itantra.core.common.IndicLanguage.HINDI    -> "⚠️ सतर्क रहें! तत्काल सहायता की आवश्यकता है।"
                    org.sih.itantra.core.common.IndicLanguage.TAMIL     -> "⚠️ கவனி! உடனடி கவனிப்பு தேவை."
                    org.sih.itantra.core.common.IndicLanguage.TELUGU    -> "⚠️ జాగ్రత్త! తక్షణ శ్రద్ధ అవసరం."
                    org.sih.itantra.core.common.IndicLanguage.KANNADA   -> "⚠️ ಎಚ್ಚರ! ತಕ್ಷಣದ ಗಮನ ಅಗತ್ಯ."
                    org.sih.itantra.core.common.IndicLanguage.MALAYALAM -> "⚠️ ശ്രദ്ധ! ഉടൻ ശ്രദ്ധ ആവശ്യം."
                    org.sih.itantra.core.common.IndicLanguage.BENGALI   -> "⚠️ সতর্ক! তাৎক্ষণিক মনোযোগ প্রয়োজন।"
                    org.sih.itantra.core.common.IndicLanguage.MARATHI   -> "⚠️ सावधान! त्वरित लक्ष आवश्यक आहे."
                    org.sih.itantra.core.common.IndicLanguage.GUJARATI  -> "⚠️ સાવધાન! તાત્કાલિક ધ્યાન જરૂરી."
                    org.sih.itantra.core.common.IndicLanguage.ODIA      -> "⚠️ ସାବଧାନ! ତୁରନ୍ତ ଧ୍ୟାନ ଆବଶ୍ୟକ।"
                    else -> "⚠️ ALERT: Immediate attention required from Node #${coordinator.relayRouter.localDeviceId}"
                }
                coordinator.sendAlert(alertText, isDistress = false)
            }
            VoiceCommand.STATUS -> {
                val snap = coordinator.manetRouter.let { router ->
                    org.sih.itantra.core.mesh.MeshTopologyProvider.buildLiveSnapshot(
                        localNodeId = router.localNodeId,
                        neighborTable = router.neighborTable,
                        routeTable = router.routeTable,
                        transportManager = coordinator.transportManager,
                        qosScheduler = coordinator.qosScheduler,
                        dtnPendingCount = router.dtnStore.size()
                    )
                }
                val statusText = "Status: ${snap.nodes.size} nodes, ${snap.routes.size} routes, " +
                    "transport ${snap.activeTransport}, DTN ${coordinator.manetRouter.dtnStore.size()} queued"
                android.util.Log.i("TransceiverViewModel", "Voice STATUS: $statusText")
                coordinator.testSynthesizeAndPlay(statusText, language)
            }
            VoiceCommand.PTT_MODE -> {
                if (coordinator.isContinuousMode.value) coordinator.setContinuousMode(false)
            }
            VoiceCommand.CONTINUOUS_MODE -> {
                if (!coordinator.isContinuousMode.value) coordinator.setContinuousMode(true)
            }
            VoiceCommand.STOP -> {
                coordinator.setContinuousMode(false)
                coordinator.stopPtt()
            }
            VoiceCommand.SWITCH_LANGUAGE -> {
                // Cycle to next language in supported set
                val langs = org.sih.itantra.core.common.IndicLanguage.entries
                val current = coordinator.activeLanguage.value
                val next = langs[(langs.indexOf(current) + 1) % langs.size]
                setLanguage(next)
                android.util.Log.i("TransceiverViewModel", "Voice SWITCH_LANGUAGE -> ${next.displayName}")
            }
            VoiceCommand.REPEAT -> {
                val lastRx = coordinator.lastReceivedText.value
                if (lastRx.isNotBlank()) {
                    coordinator.testSynthesizeAndPlay(lastRx, language)
                }
            }
            VoiceCommand.MUTE -> {
                // No hardware mute in current stack; log only
                android.util.Log.i("TransceiverViewModel", "Voice MUTE requested (no-op in current stack)")
            }
            VoiceCommand.UNMUTE -> {
                android.util.Log.i("TransceiverViewModel", "Voice UNMUTE requested (no-op in current stack)")
            }
            VoiceCommand.TOPOLOGY -> {
                onVoiceCommandNavigateTopology?.invoke()
            }
            VoiceCommand.DIAGNOSTICS -> {
                onVoiceCommandNavigateDiagnostics?.invoke()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            coordinator.start()
        }
        viewModelScope.launch {
            coordinator.activeLanguage.collect { lang ->
                if (_languageState.value is org.sih.itantra.core.language.LanguageSelectionState.Manual) {
                    _languageState.value = org.sih.itantra.core.language.LanguageSelectionState.Manual(lang)
                }
            }
        }
        coordinator.onPacketActivity = { type, desc, src, dest, prio, raw ->
            logPacketActivity(type, desc, src, dest, prio, raw)
        }
        // Register voice command interceptor in coordinator
        coordinator.onVoiceCommandResult = { result ->
            handleVoiceCommandResult(result)
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                refreshTopologySnapshot()
            }
        }
        viewModelScope.launch {
            manetSimulator.topologyState.collect {
                if (_isSimulationMode.value) {
                    refreshTopologySnapshot()
                }
            }
        }
        // Restore node mode UI state from preference (service may already be running from before)
        _isNodeModeEnabled.value = ManetNodePreference.isNodeModeEnabled(getApplication())
    }

    // -------------------------------------------------------------------------
    // Existing functions (unchanged)
    // -------------------------------------------------------------------------

    fun setThemeMode(mode: org.sih.itantra.presentation.theme.AppThemeMode) {
        _themeMode.value = mode
    }

    fun setLanguageMode(mode: org.sih.itantra.core.language.LanguageSelectionMode) {
        when (mode) {
            is org.sih.itantra.core.language.LanguageSelectionMode.Manual -> {
                coordinator.setLanguage(mode.language)
                _languageState.value = org.sih.itantra.core.language.LanguageSelectionState.Manual(mode.language)
            }
            is org.sih.itantra.core.language.LanguageSelectionMode.Auto -> {
                if (languageIdentifier.isAvailable()) {
                    _languageState.value = org.sih.itantra.core.language.LanguageSelectionState.Auto
                } else {
                    _languageState.value = org.sih.itantra.core.language.LanguageSelectionState.AutoUnavailable(
                        previousLanguage = coordinator.activeLanguage.value
                    )
                }
            }
        }
    }

    fun setLanguage(language: IndicLanguage) {
        setLanguageMode(org.sih.itantra.core.language.LanguageSelectionMode.Manual(language))
    }

    fun clearHistory() {
        MessageHistoryStore.clear()
    }

    fun refreshBondedBluetoothDevices() {
        _bondedBluetoothDevices.value = coordinator.transportManager.getBondedBluetoothDevices()
    }

    fun connectBluetooth(targetAddress: String? = null) {
        viewModelScope.launch {
            coordinator.transportManager.connectBluetooth(targetAddress)
            refreshBondedBluetoothDevices()
        }
    }

    fun startPtt() {
        if (_languageState.value is org.sih.itantra.core.language.LanguageSelectionState.AutoUnavailable) {
            android.util.Log.w("TransceiverViewModel", "PTT blocked: AUTO unavailable. Manual language selection required.")
            return
        }
        coordinator.startPtt()
    }

    fun stopPtt() {
        coordinator.stopPtt()
    }

    fun toggleContinuousMode() {
        val next = !coordinator.isContinuousMode.value
        coordinator.setContinuousMode(next)
    }

    fun setTransport(type: TransportType) {
        _activeTransportType.value = type
        viewModelScope.launch {
            coordinator.transportManager.switchTransport(type)
            if (type == TransportType.BLUETOOTH) {
                refreshBondedBluetoothDevices()
            }
        }
    }

    private val _distressStatus = MutableStateFlow<String?>(null)
    val distressStatus: StateFlow<String?> = _distressStatus.asStateFlow()

    fun clearDistressStatus() {
        _distressStatus.value = null
    }

    fun hasLocationPermission(): Boolean =
        LocationProviderHelper.hasLocationPermission(getApplication())

    /**
     * Sends an offline emergency distress packet with highest priority over MANET.
     * Captures freshest on-device GPS/Network location if permission is granted.
     * If permission is denied or location is unavailable, transmits immediately without location.
     */
    fun sendDistress(customText: String? = null, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val defaultText = when (activeLanguage.value) {
                IndicLanguage.HINDI     -> "आपातकालीन संकट संकेत! तत्काल सहायता की आवश्यकता है।"
                IndicLanguage.TAMIL     -> "அவசர உதவி தேவை! உடனடி உதவி தேவைப்படுகிறது."
                IndicLanguage.TELUGU    -> "అత్యవసర సహాయం కావాలి! తక్షణ సహాయం అవసరం."
                IndicLanguage.KANNADA   -> "ತುರ್ತು ಸಹಾಯ ಬೇಕು! ತಕ್ಷಣದ ನೆರವು ಅಗತ್ಯವಿದೆ."
                IndicLanguage.MALAYALAM -> "അടിയന്തര സഹായം ആവശ്യമാണ്! ഉടൻ സഹായം വേണം."
                IndicLanguage.BENGALI   -> "জরুরি সাহায্য প্রয়োজন! অবিলম্বে সহায়তা দরকার।"
                IndicLanguage.MARATHI   -> "तातडीची मदत हवी आहे! त्वरित सहाय्य आवश्यक आहे."
                IndicLanguage.GUJARATI  -> "કટોકટી સહાયની જરૂર છે! તાત્કાલિક મદદની જરૂર છે."
                IndicLanguage.ODIA      -> "ଜରୁରୀ ସାହାଯ୍ୟ ଦରକାର! ତୁରନ୍ତ ସହାୟତା ଆବଶ୍ୟକ।"
                else -> "Emergency distress signal from Node #${coordinator.relayRouter.localDeviceId}! Immediate assistance required."
            }
            val textToSend = customText?.ifBlank { defaultText } ?: defaultText

            // Fetch one-shot fresh location (offline, fails fast in 3s max)
            val location = LocationProviderHelper.getFreshLocation(getApplication())

            val success = coordinator.sendEmergencyDistress(textToSend, location)
            val statusMsg = if (location != null) {
                "DISTRESS SENT · LOCATION ATTACHED"
            } else {
                "DISTRESS SENT · LOCATION NOT ATTACHED"
            }
            _distressStatus.value = statusMsg
            onComplete?.invoke(success, statusMsg)
        }
    }

    fun sendEmergencyDistress() {
        sendDistress()
    }

    fun testNeuralLoopback() {
        viewModelScope.launch {
            val alertText = when (activeLanguage.value) {
                IndicLanguage.HINDI    -> "नमस्ते, यह आई-तंत्रा का न्यूरल वॉइस परीक्षण है।"
                IndicLanguage.GUJARATI -> "નમસ્તે, આ આઈ-તંત્રા ન્યુરલ વૉઇસ ટેસ્ટ છે."
                IndicLanguage.MARATHI  -> "नमस्कार, ही आय-तंत्रा न्यूरल व्हॉइस चाचणी आहे."
                IndicLanguage.KANNADA  -> "ನಮಸ್ಕಾರ, ಇದು ಐ-ತಂತ್ರ ನ್ಯೂರಲ್ ವಾಯ್ಸ್ ಪರೀಕ್ಷೆ ಆಗಿದೆ."
                IndicLanguage.MALAYALAM -> "നമസ്കാരം, ഇത് ഐ-തന്ത്ര ന്യൂറൽ വോയ്സ് ടെസ്റ്റ് ആണ്."
                IndicLanguage.TAMIL    -> "வணக்கம், இது ஐ-தந்த்ரா நியூரல் குரல் சோதனை."
                IndicLanguage.TELUGU   -> "నమస్కారం, ఇది ఐ-తంత్ర న్యూరల్ వాయిస్ టెస్ట్."
                IndicLanguage.ODIA     -> "ନମସ୍କାର, ଏହା ଆଇ-ତନ୍ତ୍ର ନ୍ୟୁରାଲ୍ ଭଏସ୍ ପରୀକ୍ଷଣ ଅଟେ।"
                IndicLanguage.BENGALI  -> "নমস্কার, এটি আই-তন্ত্র নিউরাল ভয়েস টেস্ট।"
                IndicLanguage.ENGLISH  -> "Hello, this is iTantra neural voice test."
                else -> "Hello, this is iTantra neural voice test."
            }
            coordinator.sendAlert(alertText, isDistress = false)
        }
    }

    fun testHindiNeuralLoopback() {
        testNeuralLoopback()
    }

    fun testSynthesizeSpeech(text: String) {
        viewModelScope.launch {
            coordinator.testSynthesizeAndPlay(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
        viewModelScope.launch {
            coordinator.stop()
        }
    }
}
