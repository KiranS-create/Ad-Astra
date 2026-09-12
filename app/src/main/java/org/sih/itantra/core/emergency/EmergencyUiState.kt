package org.sih.itantra.core.emergency

/**
 * UI and interaction state for the emergency distress experience.
 */
data class EmergencyUiState(
    val isEmergencyComposerOpen: Boolean = false,
    val selectedAction: EmergencyAction = EmergencyAction.MEDICAL,
    val customNote: String = "",
    val isConfirmationOpen: Boolean = false,
    val gpsFixAvailable: Boolean = false,
    val locationCoordinates: String? = null,
    val isTransmitting: Boolean = false,
    val transmissionStatus: String? = null
) {
    val effectiveMessageText: String
        get() = if (customNote.isNotBlank()) customNote.trim() else selectedAction.defaultText

    val canSend: Boolean
        get() = !isTransmitting

    companion object {
        val IDLE = EmergencyUiState()
    }
}
