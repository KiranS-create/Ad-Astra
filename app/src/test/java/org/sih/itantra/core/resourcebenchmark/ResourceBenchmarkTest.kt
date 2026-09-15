package org.sih.itantra.core.resourcebenchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceBenchmarkTest {

    // 1. CPU sample aggregation
    @Test
    fun testCpuSampleAggregation() {
        val snapshots = listOf(
            ResourceSnapshot(processCpuPercent = 10.0, totalProcessMemoryKb = 1000),
            ResourceSnapshot(processCpuPercent = 20.0, totalProcessMemoryKb = 1000),
            ResourceSnapshot(processCpuPercent = 30.0, totalProcessMemoryKb = 1000)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "CPU_AGG",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(20.0, result.cpuMean, 0.001)
        assertEquals(30.0, result.cpuPeak, 0.001)
        assertEquals(20.0, result.cpuMedian, 0.001)
    }

    // 2. peak memory calculation
    @Test
    fun testPeakMemoryCalculation() {
        val snapshots = listOf(
            ResourceSnapshot(totalProcessMemoryKb = 120_000L),
            ResourceSnapshot(totalProcessMemoryKb = 250_000L),
            ResourceSnapshot(totalProcessMemoryKb = 180_000L)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "MEM_PEAK",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(250_000L, result.memoryPeakKb)
    }

    // 3. average memory calculation
    @Test
    fun testAverageMemoryCalculation() {
        val snapshots = listOf(
            ResourceSnapshot(totalProcessMemoryKb = 100_000L),
            ResourceSnapshot(totalProcessMemoryKb = 200_000L),
            ResourceSnapshot(totalProcessMemoryKb = 300_000L)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "MEM_AVG",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(200_000L, result.memoryMeanKb)
    }

    // 4. percentile calculation
    @Test
    fun testPercentileCalculation() {
        val values = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
        val median = ResourceBenchmarkResult.computePercentile(values, 50.0)
        val p95 = ResourceBenchmarkResult.computePercentile(values, 95.0)
        assertEquals(55.0, median, 0.001)
        assertTrue(p95 > 90.0)
    }

    // 5. battery percentage delta calculation
    @Test
    fun testBatteryDeltaAndDrainRate() {
        val snapshots = listOf(
            ResourceSnapshot(timestampMs = 1000L, batteryPct = 85),
            ResourceSnapshot(timestampMs = 61000L, batteryPct = 84)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "BATT_DRAIN",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(85, result.batteryStartPct)
        assertEquals(84, result.batteryEndPct)
        assertEquals(1, result.batteryDeltaPct)
        assertEquals(60.0, result.batteryPctPerHour, 0.5)
    }

    // 6. elapsed-time calculation
    @Test
    fun testElapsedTimeCalculation() {
        val snapshots = listOf(
            ResourceSnapshot(timestampMs = 1000L, elapsedMs = 0L),
            ResourceSnapshot(timestampMs = 15000L, elapsedMs = 14000L)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "ELAPSED",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(14000L, result.durationMs)
    }

    // 7. thermal sample aggregation
    @Test
    fun testThermalSampleAggregation() {
        val snapshots = listOf(
            ResourceSnapshot(batteryTempCelsius = 31.0, thermalStatus = "NONE"),
            ResourceSnapshot(batteryTempCelsius = 35.5, thermalStatus = "LIGHT"),
            ResourceSnapshot(batteryTempCelsius = 33.2, thermalStatus = "NONE")
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "THERMAL",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(31.0, result.tempStartC, 0.01)
        assertEquals(35.5, result.tempPeakC, 0.01)
        assertEquals(33.2, result.tempEndC, 0.01)
        assertEquals("LIGHT", result.peakThermalStatus)
    }

    // 8. missing sensor handling
    @Test
    fun testMissingSensorHandling() {
        val snapshot = ResourceSnapshot(
            batteryPct = -1,
            batteryTempCelsius = Double.NaN,
            thermalStatus = "UNAVAILABLE"
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "MISSING_SENSORS",
            language = "HINDI",
            modelName = "M1",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = listOf(snapshot)
        )
        assertEquals(-1, result.batteryStartPct)
        assertTrue(result.tempStartC.isNaN())
        assertEquals("UNAVAILABLE", result.peakThermalStatus)
    }

    // 9. unavailable metric handling
    @Test
    fun testUnavailableMetricHandling() {
        val emptyResult = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "EMPTY",
            workload = "NO_DATA",
            language = "NONE",
            modelName = "NONE",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = emptyList()
        )
        assertEquals(0L, emptyResult.durationMs)
        assertEquals(0.0, emptyResult.cpuMean, 0.001)
        assertEquals(0L, emptyResult.memoryPeakKb)
        assertEquals(-1, emptyResult.batteryStartPct)
        assertEquals(0, emptyResult.sampleCount)
    }

    // 10. deterministic result serialization
    @Test
    fun testDeterministicResultSerialization() {
        val snap = ResourceSnapshot(
            timestampMs = 1700000000000L,
            elapsedMs = 5000L,
            phaseName = "PHASE_1",
            workload = "APP_IDLE",
            language = "HINDI",
            modelName = "NONE",
            processCpuPercent = 1.25,
            rssKb = 45000L,
            totalProcessMemoryKb = 55000L,
            batteryPct = 80,
            batteryTempCelsius = 32.5,
            thermalStatus = "NONE",
            notes = "Clean run"
        )
        val line1 = snap.toCsvLine("DeviceA", "16")
        val line2 = snap.toCsvLine("DeviceA", "16")
        assertEquals("CSV serialization must be strictly deterministic", line1, line2)
        assertTrue(line1.contains("APP_IDLE"))
        assertTrue(line1.contains("55000"))
    }

    // 11. CSV serialization
    @Test
    fun testCsvSerialization() {
        val result = ResourceBenchmarkResult(
            phaseName = "PHASE_2",
            workload = "STT_LOAD",
            language = "HINDI",
            modelName = "IndicConformer",
            device = "GalaxyA55",
            androidVersion = "16",
            durationMs = 5000L,
            sampleCount = 5,
            cpuMean = 12.5,
            cpuMedian = 12.0,
            cpuP95 = 14.0,
            cpuPeak = 15.0,
            memoryBaselineKb = 60000L,
            memoryMeanKb = 80000L,
            memoryP95Kb = 95000L,
            memoryPeakKb = 100000L,
            memoryEndKb = 90000L,
            memoryDeltaKb = 30000L,
            batteryStartPct = 90,
            batteryEndPct = 90,
            batteryDeltaPct = 0,
            batteryPctPerHour = 0.0,
            tempStartC = 30.0,
            tempMeanC = 30.2,
            tempPeakC = 30.5,
            tempEndC = 30.5,
            peakThermalStatus = "NONE",
            modelLoadMs = 450.0,
            firstInferenceMs = 210.0,
            steadyInferenceMs = 180.0,
            ttsMs = 0.0,
            messageCount = 1,
            errorCount = 0,
            notes = "Test CSV"
        )
        val csv = result.toCsvLine()
        assertTrue(csv.startsWith("GalaxyA55,16,PHASE_2,STT_LOAD,HINDI,IndicConformer,"))
        assertTrue(csv.contains("450.00"))
    }

    // 12. JSON serialization
    @Test
    fun testJsonSerialization() {
        val result = ResourceBenchmarkResult(
            phaseName = "PHASE_3",
            workload = "STT_INFERENCE",
            language = "ENGLISH",
            modelName = "WhisperTiny",
            device = "Note10Lite",
            androidVersion = "12",
            durationMs = 3000L,
            sampleCount = 3,
            cpuMean = 25.0,
            cpuMedian = 25.0,
            cpuP95 = 28.0,
            cpuPeak = 30.0,
            memoryBaselineKb = 70000L,
            memoryMeanKb = 85000L,
            memoryP95Kb = 90000L,
            memoryPeakKb = 92000L,
            memoryEndKb = 88000L,
            memoryDeltaKb = 18000L,
            batteryStartPct = 80,
            batteryEndPct = 80,
            batteryDeltaPct = 0,
            batteryPctPerHour = 0.0,
            tempStartC = 32.0,
            tempMeanC = 32.5,
            tempPeakC = 33.0,
            tempEndC = 33.0,
            peakThermalStatus = "NONE",
            modelLoadMs = 320.0,
            firstInferenceMs = 190.0,
            steadyInferenceMs = 160.0,
            ttsMs = 0.0,
            messageCount = 1,
            errorCount = 0,
            notes = "Test JSON"
        )
        val json = result.toJsonString()
        assertTrue(json.contains("\"phaseName\": \"PHASE_3\""))
        assertTrue(json.contains("\"mean\": 25.00"))
        assertTrue(json.contains("\"steadyInference\": 160.00"))
    }

    // 13. repeated-cycle aggregation
    @Test
    fun testRepeatedCycleAggregation() {
        val cycleSnaps = mutableListOf<ResourceSnapshot>()
        for (i in 1..10) {
            cycleSnaps.add(ResourceSnapshot(phaseName = "CYCLE_${i}_LOAD", totalProcessMemoryKb = 100_000L + (i * 500L), workload = "PRE_LOAD_BASELINE"))
            cycleSnaps.add(ResourceSnapshot(phaseName = "CYCLE_${i}_POST", totalProcessMemoryKb = 102_000L + (i * 200L), workload = "POST_RELEASE"))
        }
        val table = ResourceReportGenerator.generateMemoryStabilityTable(cycleSnaps)
        assertTrue("Table should have rows for cycles", table.contains("| 1 |"))
        assertTrue("Table should have rows for cycles", table.contains("| 10 |"))
    }

    // 14. model-load timing aggregation
    @Test
    fun testModelLoadTimingAggregation() {
        val snaps = listOf(
            ResourceSnapshot(modelLoadMs = 400.0),
            ResourceSnapshot(modelLoadMs = 420.0)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "PHASE_2",
            workload = "MODEL_LOAD",
            language = "HINDI",
            modelName = "Conformer",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snaps,
            modelLoadMs = 410.0,
            firstInferenceMs = 200.0,
            steadyInferenceMs = 150.0
        )
        assertEquals(410.0, result.modelLoadMs, 0.001)
        assertEquals(200.0, result.firstInferenceMs, 0.001)
        assertEquals(150.0, result.steadyInferenceMs, 0.001)
    }

    // 15. invalid sample handling
    @Test
    fun testInvalidSampleHandling() {
        val snapshots = listOf(
            ResourceSnapshot(processCpuPercent = Double.NaN, totalProcessMemoryKb = -100L),
            ResourceSnapshot(processCpuPercent = -5.0, totalProcessMemoryKb = 50_000L),
            ResourceSnapshot(processCpuPercent = 20.0, totalProcessMemoryKb = 60_000L)
        )
        val result = ResourceBenchmarkResult.fromSnapshots(
            phaseName = "TEST",
            workload = "INVALID_SAMPLES",
            language = "NONE",
            modelName = "NONE",
            device = "PhoneA",
            androidVersion = "16",
            snapshots = snapshots
        )
        assertEquals(20.0, result.cpuMean, 0.001)
        assertEquals(60_000L, result.memoryPeakKb)
    }

    // 16. resource benchmark lifecycle cleanup
    @Test
    fun testResourceBenchmarkLifecycleCleanup() {
        val batteryRate = BatteryBenchmark.calculateDrainRatePerHour(deltaPct = 2, durationMs = 120_000L)
        assertEquals(60.0, batteryRate, 0.001)

        val chart = ResourceReportGenerator.generateAsciiBarChart(
            title = "Test CPU",
            labels = listOf("Idle", "STT"),
            values = listOf(2.5, 45.0)
        )
        assertTrue(chart.contains("Test CPU:"))
        assertTrue(chart.contains("Idle"))
        assertTrue(chart.contains("STT"))
    }
}
