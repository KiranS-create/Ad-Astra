package org.sih.itantra.core.resourcebenchmark

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Immutable point-in-time snapshot of process and device hardware resources.
 * Designed to be zero-allocation during steady logging and fully serializable.
 */
data class ResourceSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0L,
    val phaseName: String = "UNKNOWN",
    val workload: String = "IDLE",
    val language: String = "NONE",
    val modelName: String = "NONE",

    // CPU Metrics
    val processCpuPercent: Double = 0.0,
    val systemCpuPercent: Double = 0.0,

    // Memory Metrics (in Kilobytes)
    val rssKb: Long = 0L,
    val pssKb: Long = 0L,
    val javaHeapUsedKb: Long = 0L,
    val javaHeapTotalKb: Long = 0L,
    val nativeHeapAllocatedKb: Long = 0L,
    val totalProcessMemoryKb: Long = 0L,

    // Battery Metrics
    val batteryPct: Int = -1,
    val batteryTempCelsius: Double = Double.NaN,
    val isCharging: Boolean = false,
    val batteryCurrentMicroAmps: Long = 0L,

    // Thermal Metrics
    val thermalStatus: String = "UNAVAILABLE", // NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN

    // Performance Timings (in Milliseconds)
    val modelLoadMs: Double = 0.0,
    val inferenceMs: Double = 0.0,
    val ttsMs: Double = 0.0,
    val messageCount: Int = 0,
    val errorCount: Int = 0,
    val notes: String = ""
) : Serializable {

    fun formatIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    /**
     * Serializes this snapshot into a standard CSV line conforming to Section 19 specification.
     */
    fun toCsvLine(device: String, androidVersion: String): String {
        val cleanNotes = notes.replace(",", ";").replace("\"", "'")
        return buildString {
            append(formatIsoTimestamp()).append(",")
            append(device).append(",")
            append(androidVersion).append(",")
            append(phaseName).append(",")
            append(workload).append(",")
            append(language).append(",")
            append(modelName).append(",")
            append(elapsedMs).append(",")
            append(String.format(Locale.US, "%.2f", processCpuPercent)).append(",")
            append(rssKb).append(",")
            append(totalProcessMemoryKb).append(",")
            append(batteryPct).append(",")
            append(if (batteryTempCelsius.isNaN()) "N/A" else String.format(Locale.US, "%.1f", batteryTempCelsius)).append(",")
            append(thermalStatus).append(",")
            append(String.format(Locale.US, "%.2f", modelLoadMs)).append(",")
            append(String.format(Locale.US, "%.2f", inferenceMs)).append(",")
            append(String.format(Locale.US, "%.2f", ttsMs)).append(",")
            append(messageCount).append(",")
            append(errorCount).append(",")
            append("\"").append(cleanNotes).append("\"")
        }
    }

    companion object {
        const val CSV_HEADER = "timestamp,device,android_version,phase,workload,language,model,elapsed_ms,cpu_percent,memory_rss_kb,memory_total_kb,battery_pct,battery_temp_c,thermal_status,model_load_ms,inference_ms,tts_ms,message_count,errors,notes"
    }
}
