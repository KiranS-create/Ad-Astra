package org.sih.itantra.core.resourcebenchmark

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Thermal monitor inspecting Android's official PowerManager.getThermalStatus().
 */
class ThermalBenchmark(private val context: Context? = null) {

    data class ThermalStats(
        val thermalStatus: String,
        val isThrottling: Boolean,
        val isCriticallyHot: Boolean
    )

    fun sampleThermalStatus(): ThermalStats {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ThermalStats(
                thermalStatus = "UNAVAILABLE",
                isThrottling = false,
                isCriticallyHot = false
            )
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return ThermalStats(
                thermalStatus = "UNAVAILABLE",
                isThrottling = false,
                isCriticallyHot = false
            )

        val statusCode = try {
            powerManager.currentThermalStatus
        } catch (_: Throwable) {
            -1
        }

        val statusString = when (statusCode) {
            PowerManager.THERMAL_STATUS_NONE -> "NONE"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN"
        }

        val isThrottling = statusCode >= PowerManager.THERMAL_STATUS_MODERATE
        val isCriticallyHot = statusCode >= PowerManager.THERMAL_STATUS_SEVERE

        return ThermalStats(
            thermalStatus = statusString,
            isThrottling = isThrottling,
            isCriticallyHot = isCriticallyHot
        )
    }
}
