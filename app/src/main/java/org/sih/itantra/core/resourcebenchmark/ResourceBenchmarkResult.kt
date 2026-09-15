package org.sih.itantra.core.resourcebenchmark

import java.io.Serializable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Aggregated summary results for a single benchmark phase or workload.
 */
data class ResourceBenchmarkResult(
    val phaseName: String,
    val workload: String,
    val language: String,
    val modelName: String,
    val device: String,
    val androidVersion: String,
    val durationMs: Long,
    val sampleCount: Int,

    // Aggregated CPU (%)
    val cpuMean: Double,
    val cpuMedian: Double,
    val cpuP95: Double,
    val cpuPeak: Double,

    // Aggregated Memory (KB)
    val memoryBaselineKb: Long,
    val memoryMeanKb: Long,
    val memoryP95Kb: Long,
    val memoryPeakKb: Long,
    val memoryEndKb: Long,
    val memoryDeltaKb: Long,

    // Battery Metrics
    val batteryStartPct: Int,
    val batteryEndPct: Int,
    val batteryDeltaPct: Int,
    val batteryPctPerHour: Double,

    // Thermal Metrics (°C)
    val tempStartC: Double,
    val tempMeanC: Double,
    val tempPeakC: Double,
    val tempEndC: Double,
    val peakThermalStatus: String,

    // Latency Metrics (ms)
    val modelLoadMs: Double,
    val firstInferenceMs: Double,
    val steadyInferenceMs: Double,
    val ttsMs: Double,

    val messageCount: Int,
    val errorCount: Int,
    val notes: String = ""
) : Serializable {

    fun toJsonString(): String {
        return buildString {
            append("{\n")
            append("  \"phaseName\": \"").append(escapeJson(phaseName)).append("\",\n")
            append("  \"workload\": \"").append(escapeJson(workload)).append("\",\n")
            append("  \"language\": \"").append(escapeJson(language)).append("\",\n")
            append("  \"modelName\": \"").append(escapeJson(modelName)).append("\",\n")
            append("  \"device\": \"").append(escapeJson(device)).append("\",\n")
            append("  \"androidVersion\": \"").append(escapeJson(androidVersion)).append("\",\n")
            append("  \"durationMs\": ").append(durationMs).append(",\n")
            append("  \"sampleCount\": ").append(sampleCount).append(",\n")
            append("  \"cpu\": {\n")
            append("    \"mean\": ").append(String.format(Locale.US, "%.2f", cpuMean)).append(",\n")
            append("    \"median\": ").append(String.format(Locale.US, "%.2f", cpuMedian)).append(",\n")
            append("    \"p95\": ").append(String.format(Locale.US, "%.2f", cpuP95)).append(",\n")
            append("    \"peak\": ").append(String.format(Locale.US, "%.2f", cpuPeak)).append("\n")
            append("  },\n")
            append("  \"memoryKb\": {\n")
            append("    \"baseline\": ").append(memoryBaselineKb).append(",\n")
            append("    \"mean\": ").append(memoryMeanKb).append(",\n")
            append("    \"p95\": ").append(memoryP95Kb).append(",\n")
            append("    \"peak\": ").append(memoryPeakKb).append(",\n")
            append("    \"end\": ").append(memoryEndKb).append(",\n")
            append("    \"delta\": ").append(memoryDeltaKb).append("\n")
            append("  },\n")
            append("  \"battery\": {\n")
            append("    \"startPct\": ").append(batteryStartPct).append(",\n")
            append("    \"endPct\": ").append(batteryEndPct).append(",\n")
            append("    \"deltaPct\": ").append(batteryDeltaPct).append(",\n")
            append("    \"pctPerHour\": ").append(String.format(Locale.US, "%.2f", batteryPctPerHour)).append("\n")
            append("  },\n")
            append("  \"thermal\": {\n")
            append("    \"startC\": ").append(if (tempStartC.isNaN()) "null" else String.format(Locale.US, "%.1f", tempStartC)).append(",\n")
            append("    \"meanC\": ").append(if (tempMeanC.isNaN()) "null" else String.format(Locale.US, "%.1f", tempMeanC)).append(",\n")
            append("    \"peakC\": ").append(if (tempPeakC.isNaN()) "null" else String.format(Locale.US, "%.1f", tempPeakC)).append(",\n")
            append("    \"endC\": ").append(if (tempEndC.isNaN()) "null" else String.format(Locale.US, "%.1f", tempEndC)).append(",\n")
            append("    \"peakStatus\": \"").append(escapeJson(peakThermalStatus)).append("\"\n")
            append("  },\n")
            append("  \"timingsMs\": {\n")
            append("    \"modelLoad\": ").append(String.format(Locale.US, "%.2f", modelLoadMs)).append(",\n")
            append("    \"firstInference\": ").append(String.format(Locale.US, "%.2f", firstInferenceMs)).append(",\n")
            append("    \"steadyInference\": ").append(String.format(Locale.US, "%.2f", steadyInferenceMs)).append(",\n")
            append("    \"tts\": ").append(String.format(Locale.US, "%.2f", ttsMs)).append("\n")
            append("  },\n")
            append("  \"messageCount\": ").append(messageCount).append(",\n")
            append("  \"errorCount\": ").append(errorCount).append(",\n")
            append("  \"notes\": \"").append(escapeJson(notes)).append("\"\n")
            append("}")
        }
    }

    fun toCsvLine(): String {
        val cleanNotes = notes.replace(",", ";").replace("\"", "'")
        return buildString {
            append(device).append(",")
            append(androidVersion).append(",")
            append(phaseName).append(",")
            append(workload).append(",")
            append(language).append(",")
            append(modelName).append(",")
            append(durationMs).append(",")
            append(sampleCount).append(",")
            append(String.format(Locale.US, "%.2f", cpuMean)).append(",")
            append(String.format(Locale.US, "%.2f", cpuMedian)).append(",")
            append(String.format(Locale.US, "%.2f", cpuP95)).append(",")
            append(String.format(Locale.US, "%.2f", cpuPeak)).append(",")
            append(memoryBaselineKb).append(",")
            append(memoryMeanKb).append(",")
            append(memoryP95Kb).append(",")
            append(memoryPeakKb).append(",")
            append(memoryEndKb).append(",")
            append(memoryDeltaKb).append(",")
            append(batteryStartPct).append(",")
            append(batteryEndPct).append(",")
            append(batteryDeltaPct).append(",")
            append(String.format(Locale.US, "%.2f", batteryPctPerHour)).append(",")
            append(if (tempStartC.isNaN()) "N/A" else String.format(Locale.US, "%.1f", tempStartC)).append(",")
            append(if (tempPeakC.isNaN()) "N/A" else String.format(Locale.US, "%.1f", tempPeakC)).append(",")
            append(if (tempEndC.isNaN()) "N/A" else String.format(Locale.US, "%.1f", tempEndC)).append(",")
            append(peakThermalStatus).append(",")
            append(String.format(Locale.US, "%.2f", modelLoadMs)).append(",")
            append(String.format(Locale.US, "%.2f", firstInferenceMs)).append(",")
            append(String.format(Locale.US, "%.2f", steadyInferenceMs)).append(",")
            append(String.format(Locale.US, "%.2f", ttsMs)).append(",")
            append(messageCount).append(",")
            append(errorCount).append(",")
            append("\"").append(cleanNotes).append("\"")
        }
    }

    companion object {
        const val SUMMARY_CSV_HEADER = "device,android_version,phase,workload,language,model,duration_ms,samples,cpu_mean,cpu_median,cpu_p95,cpu_peak,mem_baseline_kb,mem_mean_kb,mem_p95_kb,mem_peak_kb,mem_end_kb,mem_delta_kb,battery_start_pct,battery_end_pct,battery_delta_pct,battery_pct_per_hr,temp_start_c,temp_peak_c,temp_end_c,peak_thermal_status,model_load_ms,first_inf_ms,steady_inf_ms,tts_ms,message_count,errors,notes"

        private fun escapeJson(str: String): String {
            return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }

        fun fromSnapshots(
            phaseName: String,
            workload: String,
            language: String,
            modelName: String,
            device: String,
            androidVersion: String,
            snapshots: List<ResourceSnapshot>,
            modelLoadMs: Double = 0.0,
            firstInferenceMs: Double = 0.0,
            steadyInferenceMs: Double = 0.0,
            ttsMs: Double = 0.0,
            notes: String = ""
        ): ResourceBenchmarkResult {
            if (snapshots.isEmpty()) {
                return ResourceBenchmarkResult(
                    phaseName = phaseName,
                    workload = workload,
                    language = language,
                    modelName = modelName,
                    device = device,
                    androidVersion = androidVersion,
                    durationMs = 0L,
                    sampleCount = 0,
                    cpuMean = 0.0,
                    cpuMedian = 0.0,
                    cpuP95 = 0.0,
                    cpuPeak = 0.0,
                    memoryBaselineKb = 0L,
                    memoryMeanKb = 0L,
                    memoryP95Kb = 0L,
                    memoryPeakKb = 0L,
                    memoryEndKb = 0L,
                    memoryDeltaKb = 0L,
                    batteryStartPct = -1,
                    batteryEndPct = -1,
                    batteryDeltaPct = 0,
                    batteryPctPerHour = 0.0,
                    tempStartC = Double.NaN,
                    tempMeanC = Double.NaN,
                    tempPeakC = Double.NaN,
                    tempEndC = Double.NaN,
                    peakThermalStatus = "UNAVAILABLE",
                    modelLoadMs = modelLoadMs,
                    firstInferenceMs = firstInferenceMs,
                    steadyInferenceMs = steadyInferenceMs,
                    ttsMs = ttsMs,
                    messageCount = 0,
                    errorCount = 0,
                    notes = notes
                )
            }

            val durationMs = (snapshots.last().timestampMs - snapshots.first().timestampMs).coerceAtLeast(snapshots.last().elapsedMs)
            val sampleCount = snapshots.size

            val validCpuValues = snapshots.map { it.processCpuPercent }.filter { !it.isNaN() && it >= 0.0 }
            val cpuMean = if (validCpuValues.isNotEmpty()) validCpuValues.average() else 0.0
            val cpuMedian = computePercentile(validCpuValues, 50.0)
            val cpuP95 = computePercentile(validCpuValues, 95.0)
            val cpuPeak = if (validCpuValues.isNotEmpty()) validCpuValues.maxOrNull() ?: 0.0 else 0.0

            val memValues = snapshots.map {
                if (it.totalProcessMemoryKb > 0) it.totalProcessMemoryKb else it.rssKb
            }.filter { it >= 0L }

            val memoryBaselineKb = memValues.firstOrNull() ?: 0L
            val memoryEndKb = memValues.lastOrNull() ?: 0L
            val memoryMeanKb = if (memValues.isNotEmpty()) memValues.average().roundToInt().toLong() else 0L
            val memoryP95Kb = computeLongPercentile(memValues, 95.0)
            val memoryPeakKb = if (memValues.isNotEmpty()) memValues.maxOrNull() ?: 0L else 0L
            val memoryDeltaKb = memoryEndKb - memoryBaselineKb

            val validBatteryLevels = snapshots.map { it.batteryPct }.filter { it in 0..100 }
            val batteryStartPct = validBatteryLevels.firstOrNull() ?: -1
            val batteryEndPct = validBatteryLevels.lastOrNull() ?: -1
            val batteryDeltaPct = if (batteryStartPct != -1 && batteryEndPct != -1) {
                batteryStartPct - batteryEndPct
            } else {
                0
            }
            val batteryPctPerHour = if (durationMs > 0 && batteryDeltaPct > 0) {
                (batteryDeltaPct.toDouble() * 3600000.0) / durationMs.toDouble()
            } else {
                0.0
            }

            val validTemps = snapshots.map { it.batteryTempCelsius }.filter { !it.isNaN() && it > 0.0 }
            val tempStartC = validTemps.firstOrNull() ?: Double.NaN
            val tempMeanC = if (validTemps.isNotEmpty()) validTemps.average() else Double.NaN
            val tempPeakC = if (validTemps.isNotEmpty()) validTemps.maxOrNull() ?: Double.NaN else Double.NaN
            val tempEndC = validTemps.lastOrNull() ?: Double.NaN

            val peakThermalStatus = findPeakThermalStatus(snapshots.map { it.thermalStatus })
            val totalMessages = snapshots.maxOfOrNull { it.messageCount } ?: 0
            val totalErrors = snapshots.maxOfOrNull { it.errorCount } ?: 0

            return ResourceBenchmarkResult(
                phaseName = phaseName,
                workload = workload,
                language = language,
                modelName = modelName,
                device = device,
                androidVersion = androidVersion,
                durationMs = durationMs,
                sampleCount = sampleCount,
                cpuMean = cpuMean,
                cpuMedian = cpuMedian,
                cpuP95 = cpuP95,
                cpuPeak = cpuPeak,
                memoryBaselineKb = memoryBaselineKb,
                memoryMeanKb = memoryMeanKb,
                memoryP95Kb = memoryP95Kb,
                memoryPeakKb = memoryPeakKb,
                memoryEndKb = memoryEndKb,
                memoryDeltaKb = memoryDeltaKb,
                batteryStartPct = batteryStartPct,
                batteryEndPct = batteryEndPct,
                batteryDeltaPct = batteryDeltaPct,
                batteryPctPerHour = batteryPctPerHour,
                tempStartC = tempStartC,
                tempMeanC = tempMeanC,
                tempPeakC = tempPeakC,
                tempEndC = tempEndC,
                peakThermalStatus = peakThermalStatus,
                modelLoadMs = modelLoadMs,
                firstInferenceMs = firstInferenceMs,
                steadyInferenceMs = steadyInferenceMs,
                ttsMs = ttsMs,
                messageCount = totalMessages,
                errorCount = totalErrors,
                notes = notes
            )
        }

        fun computePercentile(values: List<Double>, percentile: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            if (percentile <= 0.0) return sorted.first()
            if (percentile >= 100.0) return sorted.last()
            val index = (percentile / 100.0 * (sorted.size - 1))
            val lower = index.toInt()
            val fraction = index - lower
            return if (lower + 1 < sorted.size) {
                sorted[lower] + fraction * (sorted[lower + 1] - sorted[lower])
            } else {
                sorted[lower]
            }
        }

        fun computeLongPercentile(values: List<Long>, percentile: Double): Long {
            if (values.isEmpty()) return 0L
            val sorted = values.sorted()
            if (percentile <= 0.0) return sorted.first()
            if (percentile >= 100.0) return sorted.last()
            val index = (percentile / 100.0 * (sorted.size - 1)).roundToInt()
            return sorted[index.coerceIn(0, sorted.size - 1)]
        }

        private fun findPeakThermalStatus(statuses: List<String>): String {
            val severityOrder = listOf("NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
            var highestIdx = -1
            var highestStr = "NONE"
            for (status in statuses) {
                val idx = severityOrder.indexOf(status.uppercase())
                if (idx > highestIdx) {
                    highestIdx = idx
                    highestStr = status.uppercase()
                }
            }
            return if (highestIdx >= 0) highestStr else (statuses.firstOrNull() ?: "UNAVAILABLE")
        }
    }
}
