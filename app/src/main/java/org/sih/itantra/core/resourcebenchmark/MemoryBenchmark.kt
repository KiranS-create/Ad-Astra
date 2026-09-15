package org.sih.itantra.core.resourcebenchmark

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File

/**
 * High-accuracy memory profiler capturing multi-tier memory metrics.
 */
class MemoryBenchmark(private val context: Context? = null) {

    data class MemoryStats(
        val javaHeapUsedKb: Long,
        val javaHeapTotalKb: Long,
        val nativeHeapAllocatedKb: Long,
        val pssKb: Long,
        val rssKb: Long,
        val totalProcessMemoryKb: Long
    )

    fun sampleMemory(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val javaHeapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        val javaHeapTotalKb = runtime.totalMemory() / 1024L

        val nativeHeapAllocatedKb = try {
            Debug.getNativeHeapAllocatedSize() / 1024L
        } catch (e: Throwable) {
            0L
        }

        val pssKb = try {
            Debug.getPss()
        } catch (e: Throwable) {
            0L
        }

        val rssKb = readRssKb()

        val totalMemoryKb = if (pssKb > 0L) {
            pssKb
        } else if (rssKb > 0L) {
            rssKb
        } else {
            javaHeapUsedKb + nativeHeapAllocatedKb
        }

        return MemoryStats(
            javaHeapUsedKb = javaHeapUsedKb,
            javaHeapTotalKb = javaHeapTotalKb,
            nativeHeapAllocatedKb = nativeHeapAllocatedKb,
            pssKb = pssKb,
            rssKb = rssKb,
            totalProcessMemoryKb = totalMemoryKb
        )
    }

    private fun readRssKb(): Long {
        var rss = 0L
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                statusFile.forEachLine { line ->
                    if (line.startsWith("VmRSS:")) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            val kb = parts[1].toLongOrNull()
                            if (kb != null && rss == 0L) {
                                rss = kb
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
        }
        return rss
    }
}
