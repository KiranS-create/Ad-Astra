package org.sih.itantra.core.protocol

import android.util.Log
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.diagnostics.DiagnosticsRepository
import java.util.Locale

/**
 * Result of a voice command analysis.
 */
data class VoiceCommandResult(
    val command: VoiceCommand? = null,
    val transcript: String = "",
    val language: IndicLanguage = IndicLanguage.ENGLISH,
    val matchedPhrase: String? = null,
    val requiresConfirmation: Boolean = false,
    val decisionLatencyMs: Long = 0L
)

/**
 * Core engine for offline tactical voice control.
 *
 * Accepts finalized STT transcripts (via [processTranscript]) and matches them against
 * deterministic command phrase dictionaries. Conservative safety rules apply:
 *
 *  - Exact/high-confidence phrase  →  return command for execution
 *  - Conversational text           →  reject (return null)
 *  - Ambiguous transcript          →  reject (return null)
 *  - No phrase match               →  return null (normal message handled by caller)
 *
 * This engine is purely functional/deterministic — no ML, no fuzzy matching.
 * It never starts or stops audio recording, and never calls any transport layer.
 * Execution of the returned command is the caller's responsibility.
 *
 * Thread-safety: all methods are pure/functional and safe to call from any thread.
 */
object VoiceCommandEngine {

    private const val TAG = "VoiceCommandEngine"

    /** Max word count for a valid command phrase. Longer = conversational, reject. */
    private const val MAX_COMMAND_WORDS = 4

    /** Min character length. Shorter = ambiguous, reject. */
    private const val MIN_COMMAND_CHARS = 2

    /**
     * Commands that require explicit confirmation before execution.
     * Safety-critical: touching emergency/stop actions.
     */
    val CONFIRMATION_REQUIRED: Set<VoiceCommand> = setOf(
        VoiceCommand.DISTRESS,
        VoiceCommand.ALERT,
        VoiceCommand.STOP,
        VoiceCommand.CANCEL
    )

    /**
     * Process a finalized STT transcript.
     *
     * Returns a [VoiceCommandResult] with a non-null [VoiceCommandResult.command] if
     * a deterministic command was recognized with high confidence. Returns null if the
     * transcript is normal conversational text that should be transmitted as a message.
     *
     * @param transcript  The finalized, trimmed STT text.
     * @param language    The active STT language.
     * @return Matched [VoiceCommandResult] or null (treat as normal message).
     */
    fun processTranscript(transcript: String, language: IndicLanguage): VoiceCommandResult? {
        val decisionStart = System.currentTimeMillis()
        val text = transcript.trim()
        if (text.isEmpty()) return null

        val lowerText = text.lowercase(Locale.ROOT)

        // Safety rule 1: semantic emergency natural language → preserve existing
        // SemanticEmergencyClassifier path (caller handles, not us)
        val semanticCmd = SemanticEmergencyClassifier.classify(lowerText)
        if (semanticCmd != null) {
            Log.i(TAG, "Semantic emergency detected → preserve normal TX path: $text")
            DiagnosticsRepository.recordVoiceCommandRejected()
            return null
        }

        // Safety rule 2: conversational text → DO NOTHING
        if (isConversationalText(lowerText)) {
            Log.d(TAG, "Conversational text, skip: '$text'")
            return null
        }

        // Safety rule 3: ambiguous (too long or too short) → DO NOTHING
        if (isAmbiguous(text, lowerText)) {
            Log.d(TAG, "Ambiguous transcript, skip: '$text'")
            return null
        }

        // Rule 4: exact/high-confidence phrase match
        val (command, matchedPhrase) = matchCommandPhrase(lowerText, language)
        if (command == null) {
            // No command match — normal text for transmission
            Log.d(TAG, "No command match: '$text'")
            return null
        }

        val latency = System.currentTimeMillis() - decisionStart
        val requiresConfirmation = command in CONFIRMATION_REQUIRED

        Log.i(TAG, "Voice command detected: $command (phrase='$matchedPhrase', lang=${language.displayName}, latency=${latency}ms)")
        DiagnosticsRepository.recordVoiceCommandDetected(command, language)

        return VoiceCommandResult(
            command = command,
            transcript = text,
            language = language,
            matchedPhrase = matchedPhrase,
            requiresConfirmation = requiresConfirmation,
            decisionLatencyMs = latency
        )
    }

    /**
     * Matches lowercase transcript against all command phrase dictionaries for the given language.
     * Returns (command, matchedPhrase) or (null, null).
     */
    private fun matchCommandPhrase(lowerText: String, language: IndicLanguage): Pair<VoiceCommand?, String?> {
        for (command in VoiceCommand.all) {
            val phrases = CommandPhraseDictionary.getPhrases(command, language) ?: continue
            for (phrase in phrases) {
                val lowerPhrase = phrase.lowercase(Locale.ROOT)
                // Exact full-string match
                if (lowerText == lowerPhrase) return Pair(command, phrase)
                // Phrase is a complete substring (e.g., "switch language" inside "please switch language")
                if (lowerPhrase.length >= 3 && lowerText.contains(lowerPhrase)) return Pair(command, phrase)
            }
        }
        return Pair(null, null)
    }

    /**
     * Returns true if the text looks like conversational speech rather than a radio command.
     * Conservative: only rejects clear conversational patterns.
     */
    private fun isConversationalText(lowerText: String): Boolean {
        val markers = listOf(
            "what ", "how ", "when ", "where ", "who ", "why ",
            "hello ", "hi ", "hey ", "please ", "thank", "thanks",
            " and ", " or ", " but ", " because ", " since ",
            "can you", "could you", "would you",
            "tell me", "explain", "show me",
            "i think", "i need", "i want", "i have", "i am",
            "we are", "we need", "we want", "you are", "you need"
        )
        return markers.any { lowerText.contains(it) }
    }

    /**
     * Returns true if the transcript is too ambiguous to be a deterministic command.
     */
    private fun isAmbiguous(text: String, lowerText: String): Boolean {
        if (text.length < MIN_COMMAND_CHARS) return true
        if (text.length > 80) return true  // too long = narrative
        val words = lowerText.trim().split(Regex("\\s+"))
        if (words.size > MAX_COMMAND_WORDS) return true
        return false
    }
}