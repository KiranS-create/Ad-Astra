package org.sih.itantra.core.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUiStateTest {

    @Test
    fun testInitialStateIsIdle() {
        val state = EmergencyUiState.IDLE

        assertFalse(state.isEmergencyComposerOpen)
        assertEquals(EmergencyAction.MEDICAL, state.selectedAction)
        assertEquals("", state.customNote)
        assertFalse(state.isConfirmationOpen)
        assertFalse(state.gpsFixAvailable)
        assertNull(state.locationCoordinates)
        assertFalse(state.isTransmitting)
        assertTrue(state.canSend)
    }

    @Test
    fun testEffectiveMessageTextUsesActionDefaultWhenNoteBlank() {
        val state = EmergencyUiState(
            selectedAction = EmergencyAction.FIRE,
            customNote = ""
        )

        assertEquals(EmergencyAction.FIRE.defaultText, state.effectiveMessageText)
    }

    @Test
    fun testEffectiveMessageTextUsesActionDefaultWhenNoteIsWhitespace() {
        val state = EmergencyUiState(
            selectedAction = EmergencyAction.INJURED,
            customNote = "   "
        )

        assertEquals(EmergencyAction.INJURED.defaultText, state.effectiveMessageText)
    }

    @Test
    fun testEffectiveMessageTextUsesCustomNoteWhenPresent() {
        val state = EmergencyUiState(
            selectedAction = EmergencyAction.MEDICAL,
            customNote = "Need 2 ambulances at sector 4 checkpoint."
        )

        assertEquals("Need 2 ambulances at sector 4 checkpoint.", state.effectiveMessageText)
    }

    @Test
    fun testCanSendDisabledWhileTransmitting() {
        val state = EmergencyUiState(
            isTransmitting = true
        )

        assertFalse(state.canSend)
    }

    @Test
    fun testLocationAttachedStatusTracking() {
        val withLocation = EmergencyUiState(
            gpsFixAvailable = true,
            locationCoordinates = "12.9716° N, 77.5946° E"
        )

        assertTrue(withLocation.gpsFixAvailable)
        assertEquals("12.9716° N, 77.5946° E", withLocation.locationCoordinates)

        val withoutLocation = EmergencyUiState(
            gpsFixAvailable = false,
            locationCoordinates = null
        )

        assertFalse(withoutLocation.gpsFixAvailable)
        assertNull(withoutLocation.locationCoordinates)
    }

    @Test
    fun testConfirmationDialogStateTracking() {
        val pendingConfirm = EmergencyUiState(
            isConfirmationOpen = true
        )

        assertTrue(pendingConfirm.isConfirmationOpen)
    }
}
