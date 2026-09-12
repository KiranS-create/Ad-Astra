package org.sih.itantra.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPlaybackStateTest {

    @Test
    fun testInitialIdleState() {
        val state = MessagePlaybackState.IDLE
        assertNull(state.messageId)
        assertEquals(TtsPlaybackStatus.IDLE, state.status)
        assertEquals(TtsLanguage.UNKNOWN, state.language)
        assertFalse(state.isActive)
        assertFalse(state.isPlaying)
        assertFalse(state.isFailed)
    }

    @Test
    fun testLoadingVoiceState() {
        val state = MessagePlaybackState(
            messageId = "msg_001",
            status = TtsPlaybackStatus.LOADING_VOICE,
            language = TtsLanguage.TAMIL
        )
        assertTrue("Loading voice should be active", state.isActive)
        assertFalse("Loading voice is not yet playing", state.isPlaying)
        assertFalse(state.isFailed)
    }

    @Test
    fun testSynthesizingState() {
        val state = MessagePlaybackState(
            messageId = "msg_002",
            status = TtsPlaybackStatus.SYNTHESIZING,
            language = TtsLanguage.HINDI
        )
        assertTrue("Synthesizing should be active", state.isActive)
        assertFalse("Synthesizing is not yet playing", state.isPlaying)
        assertFalse(state.isFailed)
    }

    @Test
    fun testPlayingState() {
        val state = MessagePlaybackState(
            messageId = "msg_003",
            status = TtsPlaybackStatus.PLAYING,
            language = TtsLanguage.GUJARATI
        )
        assertTrue("Playing must be active", state.isActive)
        assertTrue("Playing must report isPlaying true", state.isPlaying)
        assertFalse(state.isFailed)
    }

    @Test
    fun testCompletedState() {
        val state = MessagePlaybackState(
            messageId = "msg_004",
            status = TtsPlaybackStatus.COMPLETED,
            language = TtsLanguage.ENGLISH
        )
        assertFalse("Completed is not active", state.isActive)
        assertFalse(state.isPlaying)
        assertFalse(state.isFailed)
    }

    @Test
    fun testUnavailableAndFailedStates() {
        val unavail = MessagePlaybackState(
            messageId = "msg_005",
            status = TtsPlaybackStatus.UNAVAILABLE,
            language = TtsLanguage.KANNADA,
            error = "TTS UNAVAILABLE"
        )
        assertFalse(unavail.isActive)
        assertFalse(unavail.isPlaying)
        assertTrue("Unavailable should report isFailed", unavail.isFailed)
        assertEquals("TTS UNAVAILABLE", unavail.error)

        val failed = MessagePlaybackState(
            messageId = "msg_006",
            status = TtsPlaybackStatus.FAILED,
            language = TtsLanguage.MARATHI,
            error = "SYNTHESIS FAILED"
        )
        assertFalse(failed.isActive)
        assertFalse(failed.isPlaying)
        assertTrue("Failed should report isFailed", failed.isFailed)
        assertEquals("SYNTHESIS FAILED", failed.error)
    }

    @Test
    fun testFullLifecycleProgression() {
        val id = "test_lifecycle_id"
        val lang = TtsLanguage.TELUGU

        val steps = listOf(
            MessagePlaybackState(id, TtsPlaybackStatus.LOADING_VOICE, lang),
            MessagePlaybackState(id, TtsPlaybackStatus.SYNTHESIZING, lang),
            MessagePlaybackState(id, TtsPlaybackStatus.PLAYING, lang),
            MessagePlaybackState(id, TtsPlaybackStatus.COMPLETED, lang),
            MessagePlaybackState.IDLE
        )

        assertTrue(steps[0].isActive)
        assertTrue(steps[1].isActive)
        assertTrue(steps[2].isActive && steps[2].isPlaying)
        assertFalse(steps[3].isActive)
        assertEquals(TtsPlaybackStatus.IDLE, steps[4].status)
    }
}
