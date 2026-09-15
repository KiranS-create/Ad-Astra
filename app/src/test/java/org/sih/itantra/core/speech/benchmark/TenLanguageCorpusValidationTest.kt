package org.sih.itantra.core.speech.benchmark

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class TenLanguageCorpusValidationTest {

    private lateinit var corpusJson: JsonObject
    private lateinit var utterances: JsonArray

    @Before
    fun setUp() {
        val jsonText = when {
            File("docs/benchmark/corpus/tactical_speech_corpus_10lang.json").exists() ->
                File("docs/benchmark/corpus/tactical_speech_corpus_10lang.json").readText()
            File("../docs/benchmark/corpus/tactical_speech_corpus_10lang.json").exists() ->
                File("../docs/benchmark/corpus/tactical_speech_corpus_10lang.json").readText()
            else ->
                javaClass.classLoader?.getResourceAsStream("tactical_speech_corpus_10lang.json")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not locate tactical_speech_corpus_10lang.json in filesystem or resources")
        }

        corpusJson = Json.parseToJsonElement(jsonText).jsonObject
        utterances = corpusJson["utterances"]?.jsonArray ?: error("Missing 'utterances' array in corpus")
    }

    @Test
    fun testCorpusUtteranceCountExactly250() {
        assertEquals("Benchmark corpus must contain exactly 250 utterances", 250, utterances.size)
    }

    @Test
    fun testTenLanguagesRepresentedEqually() {
        val expectedLanguages = setOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
        val langCounts = mutableMapOf<String, Int>()

        for (u in utterances) {
            val lang = u.jsonObject["language"]?.jsonPrimitive?.content ?: ""
            langCounts[lang] = (langCounts[lang] ?: 0) + 1
        }

        assertEquals("All 10 target languages must be present", expectedLanguages, langCounts.keys)
        for (lang in expectedLanguages) {
            assertEquals("Language $lang must have exactly 25 utterances", 25, langCounts[lang])
        }
    }

    @Test
    fun testFiveCategoriesRepresentedEqually() {
        val expectedCategories = setOf("NORMAL", "NUMBERS", "COORDINATES", "CALLSIGN", "EMERGENCY")
        val catCounts = mutableMapOf<String, Int>()

        for (u in utterances) {
            val cat = u.jsonObject["category"]?.jsonPrimitive?.content ?: ""
            catCounts[cat] = (catCounts[cat] ?: 0) + 1
        }

        assertEquals("All 5 tactical categories must be present", expectedCategories, catCounts.keys)
        for (cat in expectedCategories) {
            assertEquals("Category $cat must have exactly 50 utterances (5 per lang * 10 langs)", 50, catCounts[cat])
        }
    }

    @Test
    fun testUtteranceFieldsIntegrity() {
        for (u in utterances) {
            val obj = u.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: ""
            val lang = obj["language"]?.jsonPrimitive?.content ?: ""
            val cat = obj["category"]?.jsonPrimitive?.content ?: ""
            val transcript = obj["transcript"]?.jsonPrimitive?.content ?: ""
            val transliteration = obj["transliteration"]?.jsonPrimitive?.content ?: ""
            val durationMs = obj["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L
            val criticalTokens = obj["criticalTokens"]?.jsonArray
            val expectedFacts = obj["expectedFacts"]?.jsonObject

            assertTrue("Utterance ID must not be blank ($id)", id.isNotBlank())
            assertTrue("Language must not be blank for $id", lang.isNotBlank())
            assertTrue("Category must not be blank for $id", cat.isNotBlank())
            assertTrue("Transcript must not be blank for $id", transcript.isNotBlank())
            assertTrue("Transliteration must not be blank for $id", transliteration.isNotBlank())
            assertTrue("DurationMs must be positive for $id ($durationMs)", durationMs > 0L)
            assertNotNull("Critical tokens must be present for $id", criticalTokens)
            assertNotNull("Expected facts must be present for $id", expectedFacts)
            assertTrue("Tactical utterance must have at least 1 critical token ($id)", criticalTokens!!.isNotEmpty())
        }
    }
}
