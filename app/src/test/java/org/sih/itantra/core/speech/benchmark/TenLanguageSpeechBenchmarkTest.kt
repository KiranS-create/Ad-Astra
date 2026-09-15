package org.sih.itantra.core.speech.benchmark

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.speech.benchmark.multilang.*
import java.io.File

class TenLanguageSpeechBenchmarkTest {

    private lateinit var corpusUtterances: List<CorpusUtterance>
    private lateinit var benchmarkEngine: TenLanguageSpeechBenchmarkEngine

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
                    ?: error("Could not locate tactical_speech_corpus_10lang.json")
        }

        val root = Json.parseToJsonElement(jsonText).jsonObject
        val array = root["utterances"]!!.jsonArray

        corpusUtterances = array.map { elem ->
            val obj = elem.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val lang = obj["language"]!!.jsonPrimitive.content
            val cat = obj["category"]!!.jsonPrimitive.content
            val audio = obj["audioFilename"]?.jsonPrimitive?.content ?: "$id.wav"
            val transcript = obj["transcript"]!!.jsonPrimitive.content
            val translit = obj["transliteration"]?.jsonPrimitive?.content ?: ""
            val duration = obj["durationMs"]?.jsonPrimitive?.longOrNull ?: 3000L
            val critTokens = obj["criticalTokens"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val facts = obj["expectedFacts"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()

            CorpusUtterance(
                id = id,
                language = lang,
                category = cat,
                audioFilename = audio,
                transcript = transcript,
                transliteration = translit,
                durationMs = duration,
                criticalTokens = critTokens,
                expectedFacts = facts
            )
        }

        benchmarkEngine = TenLanguageSpeechBenchmarkEngine("UNIT_TEST_ENV")
    }

    @Test
    fun testCorpusParsedCorrectly() {
        assertEquals(250, corpusUtterances.size)
        val hiCount = corpusUtterances.count { it.language == "hi" }
        assertEquals(25, hiCount)
    }

    @Test
    fun testComparativeFourPipelineExecution() = runBlocking {
        val report = benchmarkEngine.runComparativeCorpusBenchmark(corpusUtterances)

        assertNotNull(report)
        assertEquals(250, report.totalUtterances)
        assertEquals(1000, report.allResults.size) // 250 * 4 pipelines

        val pipeA = report.pipelineSummaries[PipelineType.PIPELINE_A_SERIAL_BATCH]
        val pipeB = report.pipelineSummaries[PipelineType.PIPELINE_B_SERIAL_TWO_PASS]
        val pipeC = report.pipelineSummaries[PipelineType.PIPELINE_C_OVERLAPPED_TWO_PASS]
        val pipeD = report.pipelineSummaries[PipelineType.PIPELINE_D_TARGETED_REFINEMENT]

        assertNotNull(pipeA)
        assertNotNull(pipeB)
        assertNotNull(pipeC)
        assertNotNull(pipeD)

        // Verify that Pipelined Overlapped (C) and Targeted Refinement (D) have significantly lower
        // endpoint-to-transcript latency than Baseline Serial Batch (A) and Serial Two-Pass (B)
        assertTrue(
            "Pipeline C endpoint-to-transcript (${pipeC!!.endpointToTranscriptLatency.mean} ms) must be faster than Pipeline A (${pipeA!!.endpointToTranscriptLatency.mean} ms)",
            pipeC.endpointToTranscriptLatency.mean < pipeA.endpointToTranscriptLatency.mean
        )
        assertTrue(
            "Pipeline C endpoint-to-transcript (${pipeC.endpointToTranscriptLatency.mean} ms) must be faster than Pipeline B (${pipeB!!.endpointToTranscriptLatency.mean} ms)",
            pipeC.endpointToTranscriptLatency.mean < pipeB.endpointToTranscriptLatency.mean
        )

        // Verify that Pipeline C emits first partial during speech (<400ms), whereas Pipeline A emits only after speech ends
        assertTrue(
            "Pipeline C first partial (${pipeC.firstPartialLatency.mean} ms) must be < 450 ms",
            pipeC.firstPartialLatency.mean < 450.0
        )
        assertTrue(
            "Pipeline A first partial (${pipeA.firstPartialLatency.mean} ms) must be greater than audio duration",
            pipeA.firstPartialLatency.mean > 1500.0
        )

        // Verify percentiles monotonic ordering
        val epC = pipeC.endpointToTranscriptLatency
        assertTrue(epC.min <= epC.p50Median)
        assertTrue(epC.p50Median <= epC.p90)
        assertTrue(epC.p90 <= epC.p95)
        assertTrue(epC.p95 <= epC.max)

        // Verify all 10 languages present in summary
        assertEquals(10, report.languageSummaries.size)
        assertTrue(report.languageSummaries.containsKey("hi"))
        assertTrue(report.languageSummaries.containsKey("ta"))
        assertTrue(report.languageSummaries.containsKey("en"))

        // Verify CSV export
        val csv = benchmarkEngine.exportToCsv(report)
        val lines = csv.trim().lines()
        assertEquals(1001, lines.size) // header + 1000 data rows
    }
}
