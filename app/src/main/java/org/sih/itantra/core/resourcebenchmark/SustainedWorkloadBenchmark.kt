package org.sih.itantra.core.resourcebenchmark

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.EmergencySeverity
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.vbr.ContextDelta
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executes a controlled, sustained tactical communication workload over a specified duration.
 */
class SustainedWorkloadBenchmark(
    private val context: Context,
    private val deviceName: String,
    private val androidVersion: String,
    private val sampleIntervalMs: Long = 1000L
) {
    private val tag = "SustainedBenchmark"
    private val isRunning = AtomicBoolean(false)
    private var benchmarkJob: Job? = null

    private val cpuBenchmark = CpuBenchmark()
    private val memoryBenchmark = MemoryBenchmark(context)
    private val batteryBenchmark = BatteryBenchmark(context)
    private val thermalBenchmark = ThermalBenchmark(context)

    val snapshots = mutableListOf<ResourceSnapshot>()
    val messageCounter = AtomicInteger(0)
    val errorCounter = AtomicInteger(0)

    suspend fun runSustainedTest(
        targetDurationMs: Long,
        workloadName: String = "SUSTAINED_TACTICAL_COMMUNICATION",
        language: String = "HINDI",
        actionEveryMs: Long = 2500L,
        onSnapshotTaken: ((ResourceSnapshot) -> Unit)? = null
    ): List<ResourceSnapshot> {
        isRunning.set(true)
        snapshots.clear()
        messageCounter.set(0)
        errorCounter.set(0)

        cpuBenchmark.reset()
        val startTime = System.currentTimeMillis()
        var lastActionTime = 0L

        Log.i(tag, "Starting sustained workload: $workloadName for ${targetDurationMs / 1000}s on $deviceName")

        try {
            while (isRunning.get()) {
                val now = System.currentTimeMillis()
                val elapsed = now - startTime
                if (elapsed >= targetDurationMs) {
                    break
                }

                if (now - lastActionTime >= actionEveryMs) {
                    lastActionTime = now
                    try {
                        simulateTacticalTransaction(messageCounter.incrementAndGet())
                    } catch (e: Throwable) {
                        errorCounter.incrementAndGet()
                        Log.e(tag, "Error during simulated tactical action", e)
                    }
                }

                val cpuPercent = cpuBenchmark.sampleProcessCpuPercent()
                val memStats = memoryBenchmark.sampleMemory()
                val battStats = batteryBenchmark.sampleBattery()
                val thermStats = thermalBenchmark.sampleThermalStatus()

                val snapshot = ResourceSnapshot(
                    timestampMs = now,
                    elapsedMs = elapsed,
                    phaseName = "PHASE_9_SUSTAINED",
                    workload = workloadName,
                    language = language,
                    modelName = "MULTI_COMPONENT",
                    processCpuPercent = cpuPercent,
                    rssKb = memStats.rssKb,
                    pssKb = memStats.pssKb,
                    javaHeapUsedKb = memStats.javaHeapUsedKb,
                    javaHeapTotalKb = memStats.javaHeapTotalKb,
                    nativeHeapAllocatedKb = memStats.nativeHeapAllocatedKb,
                    totalProcessMemoryKb = memStats.totalProcessMemoryKb,
                    batteryPct = battStats.percentage,
                    batteryTempCelsius = battStats.temperatureCelsius,
                    isCharging = battStats.isCharging,
                    batteryCurrentMicroAmps = battStats.currentMicroAmps,
                    thermalStatus = thermStats.thermalStatus,
                    messageCount = messageCounter.get(),
                    errorCount = errorCounter.get(),
                    notes = "Sustained test step ${elapsed / 1000}s"
                )

                synchronized(snapshots) {
                    snapshots.add(snapshot)
                }
                onSnapshotTaken?.invoke(snapshot)

                delay(sampleIntervalMs)
            }
        } catch (e: CancellationException) {
            Log.i(tag, "Sustained workload cancelled cleanly")
        } finally {
            isRunning.set(false)
            Log.i(tag, "Sustained workload finished. Collected ${snapshots.size} samples.")
        }

        return synchronized(snapshots) { snapshots.toList() }
    }

    fun stop() {
        isRunning.set(false)
        benchmarkJob?.cancel()
    }

    private fun simulateTacticalTransaction(seq: Int) {
        val delta = ContextDelta(
            contextId = 1001,
            version = (seq % 250) + 1,
            severity = EmergencySeverity.ALERT,
            count = seq % 10
        )
        val payload = delta.serialize()
        val packet = Packet(
            sequenceNumber = (seq and 0x7FFF).toShort(),
            timestamp = System.currentTimeMillis(),
            sourceDeviceId = 1,
            destinationDeviceId = 2,
            priority = MessagePriority.NORMAL,
            payload = payload,
            language = IndicLanguage.HINDI
        )
        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)
        check(deserialized.payload.isNotEmpty()) { "Deserialized packet payload must not be empty" }
    }
}
