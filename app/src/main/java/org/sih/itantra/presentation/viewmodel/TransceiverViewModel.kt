package org.sih.itantra.presentation.viewmodel

import android.app.Application
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
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.session.PttState
import org.sih.itantra.core.session.TransceiverCoordinator
import org.sih.itantra.core.transport.PeerDevice
import org.sih.itantra.core.transport.TransportState
import org.sih.itantra.core.transport.TransportType

enum class VoiceEngineStatus(val label: String) {
    READY("READY"),
    LOADING("LOADING"),
    VOICE("VOICE"),
    OFFLINE("OFFLINE")
}

class TransceiverViewModel(application: Application) : AndroidViewModel(application) {

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
    }

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
                    // Honestly report AUTO unavailable and require manual selection; do NOT silently substitute Hindi!
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

    fun sendEmergencyDistress() {
        val alert = when (coordinator.activeLanguage.value) {
            IndicLanguage.HINDI -> "आपातकालीन संकट! तत्काल सहायता की आवश्यकता है!"
            IndicLanguage.TAMIL -> "அவசர உதவி தேவை! உடனடியாக உதவவும்!"
            IndicLanguage.TELUGU -> "అత్యవసర పరిస్థితి! వెంటనే సహాయం కావాలి!"
            else -> "EMERGENCY DISTRESS! IMMEDIATE ASSISTANCE REQUIRED!"
        }
        coordinator.sendAlert(alert, isDistress = true)
    }

    fun testNeuralLoopback() {
        viewModelScope.launch {
            val alertText = when (activeLanguage.value) {
                IndicLanguage.HINDI -> "नमस्ते, यह आई-तंत्रा का न्यूरल वॉइस परीक्षण है।"
                IndicLanguage.GUJARATI -> "નમસ્તે, આ આઈ-તંત્રા ન્યુરલ વૉઇસ ટેસ્ટ છે."
                IndicLanguage.MARATHI -> "नमस्कार, ही आय-तंत्रा न्यूरल व्हॉइस चाचणी आहे."
                IndicLanguage.KANNADA -> "ನಮಸ್ಕಾರ, ಇದು ಐ-ತಂತ್ರ ನ್ಯೂರಲ್ ವಾಯ್ಸ್ ಪರೀಕ್ಷೆ ಆಗಿದೆ."
                IndicLanguage.MALAYALAM -> "നമസ്കാരം, ഇത് ഐ-തന്ത്ര ന്യൂറൽ വോയ്സ് ടെസ്റ്റ് ആണ്."
                IndicLanguage.TAMIL -> "வணக்கம், இது ஐ-தந்த்ரா நியூரல் குரல் சோதனை."
                IndicLanguage.TELUGU -> "నమస్కారం, ఇది ఐ-తంత్ర న్యూరల్ వాయిస్ టెస్ట్."
                IndicLanguage.ODIA -> "ନମସ୍କାର, ଏହା ଆଇ-ତନ୍ତ୍ର ନ୍ୟୁରାଲ୍ ଭଏସ୍ ପରୀକ୍ଷଣ ଅଟେ।"
                IndicLanguage.BENGALI -> "নমস্কার, এটি আই-তন্ত্র নিউরাল ভয়েস টেস্ট।"
                IndicLanguage.ENGLISH -> "Hello, this is iTantra neural voice test."
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
        viewModelScope.launch {
            coordinator.stop()
        }
    }
}
