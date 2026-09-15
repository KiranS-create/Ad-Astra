package org.sih.itantra.core.resourcebenchmark

import android.os.Process
import android.os.SystemClock
import java.io.File
import kotlin.math.max

/**
 * Non-intrusive process and system CPU sampler.
 * Uses Android's official Process.getElapsedCpuTime() combined with wall-clock elapsedRealtime()
 * to reliably compute process CPU utilization across all Android versions.
 */
class CpuBenchmark(
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors()
) {

    private var lastProcessCpuTimeMs: Long = -1L
    private var lastWallClockMs: Long = -1L

    fun reset() {
        lastProcessCpuTimeMs = readProcessCpuTimeMs()
        lastWallClockMs = readWallClockMs()
    }

    fun sampleProcessCpuPercent(): Double {
        val currentCpuTimeMs = readProcessCpuTimeMs()
        val currentWallClockMs = readWallClockMs()

        if (lastProcessCpuTimeMs < 0 || lastWallClockMs < 0) {
            lastProcessCpuTimeMs = currentCpuTimeMs
            lastWallClockMs = currentWallClockMs
            return 0.0
        }

        val deltaCpuMs = currentCpuTimeMs - lastProcessCpuTimeMs
        val deltaWallMs = currentWallClockMs - lastWallClockMs

        lastProcessCpuTimeMs = currentCpuTimeMs
        lastWallClockMs = currentWallClockMs

        if (deltaWallMs <= 0) {
            return 0.0
        }

        val cores = max(1, availableProcessors)
        val cpuUsagePercent = (deltaCpuMs.toDouble() / (deltaWallMs.toDouble() * cores)) * 100.0
        return cpuUsagePercent.coerceIn(0.0, 100.0)
    }

    internal fun readProcessCpuTimeMs(): Long {
        return try {
            Process.getElapsedCpuTime()
        } catch (e: Throwable) {
            readProcSelfStatCpuMs()
        }
    }

    internal fun readWallClockMs(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
    }

    private fun readProcSelfStatCpuMs(): Long {
        return try {
            val statContent = File("/proc/self/stat").readText()
            val tokens = statContent.split(" ")
            if (tokens.size >= 15) {
                val utime = tokens[13].toLong()
                val stime = tokens[14].toLong()
                (utime + stime) * 10L
            } else {
                0L
            }
        } catch (e: Throwable) {
            0L
        }
    }
}
