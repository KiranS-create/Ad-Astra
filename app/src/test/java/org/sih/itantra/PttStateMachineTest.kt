package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.session.PttState
import org.sih.itantra.core.session.PttStateMachine

class PttStateMachineTest {

    @Test
    fun testNormalPttCycle() {
        val machine = PttStateMachine()
        assertEquals(PttState.IDLE, machine.state.value)

        assertTrue(machine.transitionTo(PttState.PTT_PRESSED))
        assertTrue(machine.transitionTo(PttState.RECORDING))
        assertTrue(machine.transitionTo(PttState.SPEECH_DETECTED))
        assertTrue(machine.transitionTo(PttState.STT_PROCESSING))
        assertTrue(machine.transitionTo(PttState.MESSAGE_ENCODED))
        assertTrue(machine.transitionTo(PttState.TRANSMITTING))
        assertTrue(machine.transitionTo(PttState.IDLE))
    }

    @Test
    fun testInvalidTransitionRejected() {
        val machine = PttStateMachine()
        // Cannot jump directly from IDLE to TRANSMITTING
        assertFalse(machine.transitionTo(PttState.TRANSMITTING))
        assertEquals(PttState.IDLE, machine.state.value)
    }

    @Test
    fun testReset() {
        val machine = PttStateMachine()
        machine.transitionTo(PttState.PTT_PRESSED)
        machine.reset()
        assertEquals(PttState.IDLE, machine.state.value)
    }
}
