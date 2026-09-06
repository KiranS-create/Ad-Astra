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
    // MANET Simulation / Demo Engine
    // -------------------------------------------------------------------------

    val manetSimulator = org.sih.itantra.core.mesh.ManetSimulator()
    val topologyState = manetSimulator.topologyState

    fun simStartDiscovery() = manetSimulator.startDiscovery()
    fun simSendPacketAtoC(msg: String = "iTantra Voice Packet (Compressed INT8)") = manetSimulator.sendPacketAtoC(msg)
    fun simFailNodeB() = manetSimulator.failNodeB()
    fun simTriggerFailureAndRediscovery() = manetSimulator.triggerFailureAndRediscovery()
    fun simRepairNodeB() = manetSimulator.repairNodeB()
    fun simResetTopology() = manetSimulator.resetTopology()
    fun simSelectPacket(packet: org.sih.itantra.core.protocol.Packet?) = manetSimulator.selectPacket(packet)

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
