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
import org.sih.itantra.core.transport.TransportType

class TransceiverViewModel(application: Application) : AndroidViewModel(application) {

    val coordinator = TransceiverCoordinator(application.applicationContext)

    val pttState: StateFlow<PttState> = coordinator.stateMachine.state
    val activeLanguage: StateFlow<IndicLanguage> = coordinator.activeLanguage
    val isContinuousMode: StateFlow<Boolean> = coordinator.isContinuousMode
    val lastTranscribedText: StateFlow<String> = coordinator.lastTranscribedText
    val lastReceivedText: StateFlow<String> = coordinator.lastReceivedText

    private val _activeTransportType = MutableStateFlow(TransportType.WIFI)
    val activeTransportType: StateFlow<TransportType> = _activeTransportType.asStateFlow()

    val messageHistory: StateFlow<List<MessageRecord>> = MessageHistoryStore.historyFlow
    val diagnosticsState: StateFlow<DiagnosticsState> = DiagnosticsRepository.state

    init {
        viewModelScope.launch {
            coordinator.start()
        }
    }

    fun startPtt() {
        coordinator.startPtt()
    }

    fun stopPtt() {
        coordinator.stopPtt()
    }

    fun toggleContinuousMode() {
        val next = !coordinator.isContinuousMode.value
        coordinator.setContinuousMode(next)
    }

    fun setLanguage(language: IndicLanguage) {
        coordinator.setLanguage(language)
    }

    fun setTransport(type: TransportType) {
        _activeTransportType.value = type
        viewModelScope.launch {
            coordinator.transportManager.switchTransport(type)
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

    fun testHindiNeuralLoopback() {
        viewModelScope.launch {
            coordinator.sendAlert("नमस्ते, यह आई-तंत्रा का न्यूरल वॉइस परीक्षण है।", isDistress = false)
        }
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
