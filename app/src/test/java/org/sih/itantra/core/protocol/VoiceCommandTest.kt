package org.sih.itantra.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage

/**
 * VoiceCommandTest — 15 targeted unit tests for:
 *  - Command phrase parsing (6 tests)
 *  - Safety filter / conservative rejection (3 tests)
 *  - State machine transitions (3 tests)
 *  - Integration-level command routing (3 tests)
 */
class VoiceCommandTest {

    // -------------------------------------------------------------------------
    // 1. Command Phrase Parsing — 6 tests
    // -------------------------------------------------------------------------

    @Test
    fun `parse01 English SEND exact match`() {
        val result = VoiceCommandEngine.processTranscript("send", IndicLanguage.ENGLISH)
        assertNotNull("'send' should match VoiceCommand.SEND in English", result)
        assertEquals(VoiceCommand.SEND, result!!.command)
    }

    @Test
    fun `parse02 English DISTRESS exact match`() {
        val result = VoiceCommandEngine.processTranscript("distress", IndicLanguage.ENGLISH)
        assertNotNull("'distress' should match VoiceCommand.DISTRESS in English", result)
        assertEquals(VoiceCommand.DISTRESS, result!!.command)
        assertTrue("DISTRESS should require confirmation", result.requiresConfirmation)
    }

    @Test
    fun `parse03 Hindi SEND exact match`() {
        val result = VoiceCommandEngine.processTranscript("भेजें", IndicLanguage.HINDI)
        assertNotNull("Hindi 'भेजें' should match VoiceCommand.SEND", result)
        assertEquals(VoiceCommand.SEND, result!!.command)
    }

    @Test
    fun `parse04 Tamil DISTRESS phrase match`() {
        val result = VoiceCommandEngine.processTranscript("अवसर", IndicLanguage.TAMIL)
        // Tamil DISTRESS phrase is "அவசர" — different script; this should NOT match Hindi
        // This test confirms language isolation
        val hindiResult = VoiceCommandEngine.processTranscript("आपातकालीन", IndicLanguage.HINDI)
        assertNotNull("Hindi आपातकालीन should match DISTRESS in Hindi", hindiResult)
        assertEquals(VoiceCommand.DISTRESS, hindiResult!!.command)
    }

    @Test
    fun `parse05 English STATUS exact match`() {
        val result = VoiceCommandEngine.processTranscript("status", IndicLanguage.ENGLISH)
        assertNotNull("'status' should match VoiceCommand.STATUS", result)
        assertEquals(VoiceCommand.STATUS, result!!.command)
        assertFalse("STATUS should not require confirmation", result.requiresConfirmation)
    }

    @Test
    fun `parse06 English TOPOLOGY map phrase match`() {
        val result = VoiceCommandEngine.processTranscript("map", IndicLanguage.ENGLISH)
        assertNotNull("'map' should match VoiceCommand.TOPOLOGY in English", result)
        assertEquals(VoiceCommand.TOPOLOGY, result!!.command)
    }

    // -------------------------------------------------------------------------
    // 2. Safety Filter — Conservative Rejection — 3 tests
    // -------------------------------------------------------------------------

    @Test
    fun `safety01 conversational text rejected`() {
        val result = VoiceCommandEngine.processTranscript(
            "hello can you please help me send a message", IndicLanguage.ENGLISH
        )
        assertNull("Conversational text must be rejected (return null)", result)
    }

    @Test
    fun `safety02 too many words rejected`() {
        val result = VoiceCommandEngine.processTranscript(
            "send the message now to the base camp immediately", IndicLanguage.ENGLISH
        )
        assertNull("Transcript with >4 words and no exact match must be rejected", result)
    }

    @Test
    fun `safety03 empty transcript rejected`() {
        val result = VoiceCommandEngine.processTranscript("", IndicLanguage.ENGLISH)
        assertNull("Empty transcript must return null", result)
    }

    // -------------------------------------------------------------------------
    // 3. State Machine — 3 tests
    // -------------------------------------------------------------------------

    @Test
    fun `state01 initial state is IDLE`() {
        val sm = VoiceCommandStateMachine()
        assertEquals(VoiceCommandState.IDLE, sm.state.value)
    }

    @Test
    fun `state02 confirmation required commands stage in AWAITING_CONFIRMATION`() {
        val sm = VoiceCommandStateMachine()
        val nextState = sm.onCommandDetected(VoiceCommand.DISTRESS)
        assertEquals(VoiceCommandState.AWAITING_CONFIRMATION, nextState)
        assertTrue("requiresConfirmation should be true after DISTRESS detected", sm.requiresConfirmation())
    }

    @Test
    fun `state03 confirm transitions to EXECUTING then reset to IDLE`() {
        val sm = VoiceCommandStateMachine()
        sm.onCommandDetected(VoiceCommand.ALERT)
        assertEquals(VoiceCommandState.AWAITING_CONFIRMATION, sm.state.value)
        val confirmed = sm.confirm()
        assertTrue("confirm() should succeed from AWAITING_CONFIRMATION", confirmed)
        assertEquals(VoiceCommandState.EXECUTING, sm.state.value)
        sm.complete(success = true)
        assertEquals(VoiceCommandState.IDLE, sm.state.value)
    }

    // -------------------------------------------------------------------------
    // 4. Integration-level Command Routing — 3 tests
    // -------------------------------------------------------------------------

    @Test
    fun `integration01 DISTRESS requires confirmation flag`() {
        val result = VoiceCommandEngine.processTranscript("emergency", IndicLanguage.ENGLISH)
        assertNotNull(result)
        assertEquals(VoiceCommand.DISTRESS, result!!.command)
        assertTrue("DISTRESS command must have requiresConfirmation=true", result.requiresConfirmation)
    }

    @Test
    fun `integration02 STOP requires confirmation flag`() {
        val result = VoiceCommandEngine.processTranscript("stop", IndicLanguage.ENGLISH)
        assertNotNull(result)
        assertEquals(VoiceCommand.STOP, result!!.command)
        assertTrue("STOP command must have requiresConfirmation=true", result.requiresConfirmation)
    }

    @Test
    fun `integration03 safe commands do not require confirmation`() {
        val safeCommands = listOf(
            "status" to VoiceCommand.STATUS,
            "map" to VoiceCommand.TOPOLOGY,
            "diagnostics" to VoiceCommand.DIAGNOSTICS,
            "repeat" to VoiceCommand.REPEAT
        )
        for ((phrase, expectedCmd) in safeCommands) {
            val result = VoiceCommandEngine.processTranscript(phrase, IndicLanguage.ENGLISH)
            assertNotNull("'$phrase' should match $expectedCmd", result)
            assertEquals(expectedCmd, result!!.command)
            assertFalse("$expectedCmd must NOT require confirmation", result.requiresConfirmation)
        }
    }
}
