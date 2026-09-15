package org.sih.itantra.core.resourcebenchmark

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.context.SharedContextEntry
import org.sih.itantra.core.context.SharedContextStore
import org.sih.itantra.core.protocol.EmergencyCategory
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.EmergencySubtype
import org.sih.itantra.core.protocol.SemanticCommand
import org.sih.itantra.core.protocol.SemanticEmergencyClassifier
import org.sih.itantra.core.speech.benchmark.TargetedRefinementBenchmarkRunner
import org.sih.itantra.core.speech.pipeline.AudioChunk
import org.sih.itantra.core.vbr.ContextDelta
import org.sih.itantra.core.vbr.SemanticBase
import org.sih.itantra.core.vbr.SemanticEnhancement
import org.sih.itantra.ml.model.ModelAssetManager
import org.sih.itantra.ml.stt.SherpaOnnxSpeechRecognizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Master orchestrator for Feature 24: Resource Benchmark Suite.
 */
class ResourceBenchmarkRunner(
    private val context: Context,
    val deviceName: String,
    val androidVersion: String
) {
    private val tag = "ResourceBenchmarkRunner"
    private val isRunning = AtomicBoolean(false)

    val cpuBenchmark = CpuBenchmark()
    val memoryBenchmark = MemoryBenchmark(context)
    val batteryBenchmark = BatteryBenchmark(context)
    val thermalBenchmark = ThermalBenchmark(context)
    val modelLoadBenchmark = ModelLoadBenchmark(context, memoryBenchmark)

    val allSnapshots = mutableListOf<ResourceSnapshot>()
    val phaseResults = mutableListOf<ResourceBenchmarkResult>()

    fun cancel() {
        isRunning.set(false)
    }

    fun takeSnapshot(
        phaseName: String,
        workload: String,
        language: String = "NONE",
        modelName: String = "NONE",
        elapsedMs: Long = 0L,
        modelLoadMs: Double = 0.0,
        inferenceMs: Double = 0.0,
        ttsMs: Double = 0.0,
        messageCount: Int = 0,
        errorCount: Int = 0,
        notes: String = ""
    ): ResourceSnapshot {
        val cpu = cpuBenchmark.sampleProcessCpuPercent()
        val mem = memoryBenchmark.sampleMemory()
        val batt = batteryBenchmark.sampleBattery()
        val therm = thermalBenchmark.sampleThermalStatus()

        val snap = ResourceSnapshot(
            timestampMs = System.currentTimeMillis(),
            elapsedMs = elapsedMs,
            phaseName = phaseName,
            workload = workload,
            language = language,
            modelName = modelName,
            processCpuPercent = cpu,
            rssKb = mem.rssKb,
            pssKb = mem.pssKb,
            javaHeapUsedKb = mem.javaHeapUsedKb,
            javaHeapTotalKb = mem.javaHeapTotalKb,
            nativeHeapAllocatedKb = mem.nativeHeapAllocatedKb,
            totalProcessMemoryKb = mem.totalProcessMemoryKb,
            batteryPct = batt.percentage,
            batteryTempCelsius = batt.temperatureCelsius,
            isCharging = batt.isCharging,
            batteryCurrentMicroAmps = batt.currentMicroAmps,
            thermalStatus = therm.thermalStatus,
            modelLoadMs = modelLoadMs,
            inferenceMs = inferenceMs,
            ttsMs = ttsMs,
            messageCount = messageCount,
            errorCount = errorCount,
            notes = notes
        )

        synchronized(allSnapshots) {
            allSnapshots.add(snap)
        }
        return snap
    }

    suspend fun runIdlePhase(
        phaseName: String,
        durationMs: Long = 5000L,
        sampleIntervalMs: Long = 1000L
    ): ResourceBenchmarkResult {
        cpuBenchmark.reset()
        val phaseSnaps = mutableListOf<ResourceSnapshot>()
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < durationMs) {
            delay(sampleIntervalMs)
            val elapsed = System.currentTimeMillis() - start
            val snap = takeSnapshot(
                phaseName = phaseName,
                workload = "IDLE_MONITORING",
                elapsedMs = elapsed,
                notes = "Idle baseline sample"
            )
            phaseSnaps.add(snap)
        }

        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = phaseName,
            workload = "IDLE",
            language = "NONE",
            modelName = "NONE",
            device = deviceName,
            androidVersion = androidVersion,
            snapshots = phaseSnaps,
            notes = "Idle baseline duration ${durationMs / 1000}s"
        )
        synchronized(phaseResults) { phaseResults.add(result) }
        return result
    }

    suspend fun runFeature17IncrementalBenchmark(
        iterations: Int = 10
    ): Pair<ResourceBenchmarkResult, ResourceBenchmarkResult> {
        val testWords = listOf("हम", "राहत", "सामग्री", "के", "साथ", "उत्तर", "दिशा", "में", "आगे", "बढ़", "रहे", "हैं")
        val testChunks = testWords.mapIndexed { idx, _ ->
            AudioChunk(
                chunkIndex = idx,
                pcmData = ByteArray(6400),
                isLast = idx == testWords.size - 1
            )
        }

        val refinerRunner = TargetedRefinementBenchmarkRunner()

        // 1. Pass 1 baseline (no refinement)
        cpuBenchmark.reset()
        val pass1Snaps = mutableListOf<ResourceSnapshot>()
        val t0 = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val (speechRes, _) = refinerRunner.runTargetedRefinement(testChunks, words = testWords, refinementDelayMs = 0L)
            val elapsed = System.currentTimeMillis() - t0
            val snap = takeSnapshot(
                phaseName = "PHASE_4A_PASS1_ONLY",
                workload = "PASS1_BATCH_BASELINE",
                language = "HINDI",
                modelName = "Pass1_Baseline",
                elapsedMs = elapsed,
                inferenceMs = speechRes.totalComputeMs,
                notes = "Pass1 only run $i, total compute ${speechRes.totalComputeMs}ms"
            )
            pass1Snaps.add(snap)
            delay(50)
        }

        val pass1Result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "PHASE_4A_PASS1_ONLY",
            workload = "PASS1_BASELINE",
            language = "HINDI",
            modelName = "Pass1_Baseline",
            device = deviceName,
            androidVersion = androidVersion,
            snapshots = pass1Snaps,
            steadyInferenceMs = pass1Snaps.map { it.inferenceMs }.average(),
            notes = "Pass 1 only baseline over $iterations iterations"
        )

        // 2. Pass 1 + Feature 17 Targeted Refinement
        cpuBenchmark.reset()
        val refSnaps = mutableListOf<ResourceSnapshot>()
        val t1 = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val (speechRes, metrics) = refinerRunner.runTargetedRefinement(testChunks, words = testWords, refinementDelayMs = 10L)
            val elapsed = System.currentTimeMillis() - t1
            val snap = takeSnapshot(
                phaseName = "PHASE_4B_TARGETED_REFINEMENT",
                workload = "PASS1_PLUS_FEATURE17_REFINEMENT",
                language = "HINDI",
                modelName = "TargetedRefinement",
                elapsedMs = elapsed,
                inferenceMs = speechRes.totalComputeMs,
                notes = "Refinement run $i: evaluated ${metrics.candidatesEvaluatedCount}, refinement compute ${metrics.targetedRefinementComputeDurationMs}ms"
            )
            refSnaps.add(snap)
            delay(50)
        }

        val refResult = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "PHASE_4B_TARGETED_REFINEMENT",
            workload = "FEATURE17_REFINEMENT",
            language = "HINDI",
            modelName = "TargetedRefinement",
            device = deviceName,
            androidVersion = androidVersion,
            snapshots = refSnaps,
            steadyInferenceMs = refSnaps.map { it.inferenceMs }.average(),
            notes = "Feature 17 bounded refinement over $iterations iterations"
        )

        synchronized(phaseResults) {
            phaseResults.add(pass1Result)
            phaseResults.add(refResult)
        }
        return Pair(pass1Result, refResult)
    }

    suspend fun runSemanticAndContextIncrementalBenchmark(
        iterations: Int = 100
    ): Triple<ResourceBenchmarkResult, ResourceBenchmarkResult, ResourceBenchmarkResult> {
        val testText = "हम राहत सामग्री के साथ उत्तर दिशा में आगे बढ़ रहे हैं। 4 लोग फंसे हैं।"

        val defaultCmd = SemanticCommand(
            category = EmergencyCategory.RESCUE,
            subtype = EmergencySubtype.COLLAPSE,
            severity = EmergencySeverity.ALERT,
            count = 4,
            parameter = 1
        )

        // Test 1: SEMANTIC_BASE
        cpuBenchmark.reset()
        val baseSnaps = mutableListOf<ResourceSnapshot>()
        val t0 = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val tStart = System.nanoTime()
            val parsed = SemanticEmergencyClassifier.classify(testText) ?: defaultCmd
            val base = SemanticBase.fromCommand(parsed)
            val bytes = base.serialize()
            val reconstructed = SemanticBase.deserialize(bytes)
            val tEnd = System.nanoTime()

            if (i % 10 == 0) {
                val snap = takeSnapshot(
                    phaseName = "PHASE_5A_SEMANTIC_BASE",
                    workload = "SEMANTIC_BASE_SERIALIZE",
                    language = "HINDI",
                    modelName = "SemanticBase_8B",
                    elapsedMs = System.currentTimeMillis() - t0,
                    inferenceMs = (tEnd - tStart) / 1_000_000.0,
                    messageCount = i + 1,
                    notes = "Payload size: ${bytes.size} bytes"
                )
                baseSnaps.add(snap)
            }
        }
        val baseResult = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "PHASE_5A_SEMANTIC_BASE",
            workload = "SEMANTIC_BASE",
            language = "HINDI",
            modelName = "SemanticBase",
            device = deviceName,
            androidVersion = androidVersion,
            snapshots = baseSnaps,
            steadyInferenceMs = baseSnaps.map { it.inferenceMs }.average(),
            notes = "SemanticBase (8 bytes) serialization and parsing"
        )

        // Test 2: SEMANTIC_ENHANCED
        cpuBenchmark.reset()
        val enhSnaps = mutableListOf<ResourceSnapshot>()
        val t1 = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val tStart = System.nanoTime()
            val enh = SemanticEnhancement(text = testText)
            val bytes = enh.serialize()
            val reconstructed = SemanticEnhancement.deserialize(bytes)
            val tEnd = System.nanoTime()

            if (i % 10 == 0) {
                val snap = takeSnapshot(
                    phaseName = "PHASE_5B_SEMANTIC_ENHANCED",
                    workload = "SEMANTIC_ENHANCED_SERIALIZE",
                    language = "HINDI",
                    modelName = "SemanticEnhanced_Var",
                    elapsedMs = System.currentTimeMillis() - t1,
                    inferenceMs = (tEnd - tStart) / 1_000_000.0,
                    messageCount = i + 1,
                    notes = "Payload size: ${bytes.size} bytes"
                )
                enhSnaps.add(snap)
            }
        }
        val enhResult = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "PHASE_5B_SEMANTIC_ENHANCED",
            workload = "SEMANTIC_ENHANCED",
            language = "HINDI",
            modelName = "SemanticEnhanced",
            device = deviceName,
            androidVersion = androidVersion,
            snapshots = enhSnaps,
            steadyInferenceMs = enhSnaps.map { it.inferenceMs }.average(),
            notes = "SemanticEnhanced serialization and parsing"
        )

        // Test 3: CONTEXT_DELTA & SharedContextStore
        cpuBenchmark.reset()
        val deltaSnaps = mutableListOf<ResourceSnapshot>()
        val t2 = System.currentTimeMillis()

        val baseEntry = SharedContextEntry.fromCommand(
            contextId = 42,
            command = defaultCmd,
            sourceDeviceId = 1,
            confidence = 90
        )
        SharedContextStore.put(baseEntry)

        for (i in 0 until iterations) {
            val tStart = System.nanoTime()
            val delta = ContextDelta(
                contextId = 42,
                version = i + 2,
                count = 5 + (i % 5)
            )
            val bytes = delta.serialize()
            val decoded = ContextDelta.deserialize(bytes)?.delta
            val resolved = decoded?.applyTo(baseEntry)
            val tEnd = System.nanoTime()

            if (i % 10 == 0) {
                val snap = takeSnapshot(
                    phaseName = "PHASE_5C_CONTEXT_DELTA",
                    workload = "CONTEXT_DELTA_RESOLVE",
                    language = "HINDI",
                    modelName = "ContextDelta_7B",
                    elapsedMs = System.currentTimeMillis() - t2,
                    inferenceMs = (tEnd - tStart) / 1_000_000.0,
                    messageCount = i + 1,
                    notes = "Delta size: ${bytes.size} bytes, resolved count=${resolved?.count}"
                )
                deltaSnaps.add(snap)
            }
        }
        val deltaResult = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "PHASE_5C_CONTEXT_DELTA",
            workload = "CONTEXT_DELTA",
            language = "HINDI",
            modelName = "ContextDelta",
            device = deviceName,
            androidVersion = androidVersion,
            snapshots = deltaSnaps,
            steadyInferenceMs = deltaSnaps.map { it.inferenceMs }.average(),
            notes = "ContextDelta (6-8 bytes) store resolution"
        )

        synchronized(phaseResults) {
            phaseResults.add(baseResult)
            phaseResults.add(enhResult)
            phaseResults.add(deltaResult)
        }
        return Triple(baseResult, enhResult, deltaResult)
    }

    suspend fun runMemoryStabilityCycleBenchmark(
        language: IndicLanguage = IndicLanguage.HINDI,
        totalCycles: Int = 10
    ): List<ResourceSnapshot> {
        val cycleSnaps = mutableListOf<ResourceSnapshot>()
        System.gc()
        Thread.sleep(200)

        val baseMem = memoryBenchmark.sampleMemory()
        Log.i(tag, "Starting $totalCycles-cycle memory stability test. Baseline: ${baseMem.totalProcessMemoryKb} KB")

        val modelAssetMgr = ModelAssetManager(context)
        val dummyPcm = ByteArray(32000)

        for (cycle in 1..totalCycles) {
            val tStart = System.currentTimeMillis()
            val snapBefore = takeSnapshot(
                phaseName = "PHASE_10_CYCLE_$cycle",
                workload = "PRE_LOAD_BASELINE",
                language = language.displayName,
                elapsedMs = 0L,
                notes = "Cycle $cycle pre-load"
            )

            // 1. Load model
            val rec = SherpaOnnxSpeechRecognizer(context, modelAssetMgr)
            val tLoad0 = System.nanoTime()
            rec.initEngine(language)
            val loadMs = (System.nanoTime() - tLoad0) / 1_000_000.0

            val snapLoaded = takeSnapshot(
                phaseName = "PHASE_10_CYCLE_$cycle",
                workload = "MODEL_LOADED",
                language = language.displayName,
                elapsedMs = (System.currentTimeMillis() - tStart),
                modelLoadMs = loadMs,
                notes = "Cycle $cycle loaded"
            )

            // 2. Infer
            val tInf0 = System.nanoTime()
            rec.processAudioSegment(dummyPcm, language)
            val infMs = (System.nanoTime() - tInf0) / 1_000_000.0

            val snapInfer = takeSnapshot(
                phaseName = "PHASE_10_CYCLE_$cycle",
                workload = "POST_INFERENCE",
                language = language.displayName,
                elapsedMs = (System.currentTimeMillis() - tStart),
                inferenceMs = infMs,
                notes = "Cycle $cycle inferred"
            )

            // 3. Release
            rec.release()
            System.gc()
            delay(150)

            val snapReleased = takeSnapshot(
                phaseName = "PHASE_10_CYCLE_$cycle",
                workload = "POST_RELEASE",
                language = language.displayName,
                elapsedMs = (System.currentTimeMillis() - tStart),
                notes = "Cycle $cycle released"
            )

            cycleSnaps.add(snapBefore)
            cycleSnaps.add(snapLoaded)
            cycleSnaps.add(snapInfer)
            cycleSnaps.add(snapReleased)

            Log.i(tag, "Cycle $cycle complete: Peak RAM=${snapInfer.totalProcessMemoryKb} KB, Released RAM=${snapReleased.totalProcessMemoryKb} KB")
            delay(100)
        }

        return cycleSnaps
    }
}
