package org.sih.itantra.core.resourcebenchmark

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Battery and power profiler querying standard Android system broadcast and BatteryManager APIs.
 */
class BatteryBenchmark(private val context: Context? = null) {

    data class BatteryStats(
        val percentage: Int,
        val temperatureCelsius: Double,
        val isCharging: Boolean,
        val statusString: String,
        val currentMicroAmps: Long
    )

    fun sampleBattery(): BatteryStats {
        if (context == null) {
            return BatteryStats(
                percentage = -1,
                temperatureCelsius = Double.NaN,
                isCharging = false,
                statusString = "UNKNOWN",
                currentMicroAmps = 0L
            )
        }

        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, iFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } else {
            -1
        }

        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperatureCelsius = if (rawTemp > 0) {
            rawTemp.toDouble() / 10.0
        } else {
            Double.NaN
        }

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val statusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentMicroAmps = try {
            bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        } catch (_: Throwable) {
            0L
        }

        return BatteryStats(
            percentage = percentage,
            temperatureCelsius = temperatureCelsius,
            isCharging = isCharging,
            statusString = statusString,
            currentMicroAmps = currentMicroAmps
        )
    }

    companion object {
        fun calculateDrainRatePerHour(deltaPct: Int, durationMs: Long): Double {
            if (durationMs <= 0 || deltaPct <= 0) return 0.0
            return (deltaPct.toDouble() * 3600000.0) / durationMs.toDouble()
        }
    }
}
