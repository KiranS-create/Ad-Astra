package org.sih.itantra.core.resourcebenchmark

import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Generates deterministic CSV, JSON, and formatted Markdown tables/ASCII charts from benchmark results.
 */
object ResourceReportGenerator {

    fun exportSnapshotsToCsv(
        device: String,
        androidVersion: String,
        snapshots: List<ResourceSnapshot>,
        outputFile: File
    ) {
        outputFile.bufferedWriter().use { writer ->
            writer.write(ResourceSnapshot.CSV_HEADER)
            writer.newLine()
            for (snap in snapshots) {
                writer.write(snap.toCsvLine(device, androidVersion))
                writer.newLine()
            }
        }
    }

    fun exportSummaryToCsv(
        results: List<ResourceBenchmarkResult>,
        outputFile: File
    ) {
        outputFile.bufferedWriter().use { writer ->
            writer.write(ResourceBenchmarkResult.SUMMARY_CSV_HEADER)
            writer.newLine()
            for (res in results) {
                writer.write(res.toCsvLine())
                writer.newLine()
            }
        }
    }

    fun exportToJson(
        device: String,
        androidVersion: String,
        results: List<ResourceBenchmarkResult>,
        outputFile: File
    ) {
        outputFile.bufferedWriter().use { writer ->
            writer.write("{\n")
            writer.write("  \"device\": \"$device\",\n")
            writer.write("  \"androidVersion\": \"$androidVersion\",\n")
            writer.write("  \"generatedAt\": \"${System.currentTimeMillis()}\",\n")
            writer.write("  \"resultCount\": ${results.size},\n")
            writer.write("  \"results\": [\n")
            for ((idx, res) in results.withIndex()) {
                val jsonStr = res.toJsonString().lines().joinToString("\n") { "    $it" }
                writer.write(jsonStr)
                if (idx < results.size - 1) {
                    writer.write(",")
                }
                writer.newLine()
            }
            writer.write("  ]\n")
            writer.write("}\n")
        }
    }

    fun generateAsciiBarChart(
        title: String,
        labels: List<String>,
        values: List<Double>,
        unit: String = "%",
        maxBarWidth: Int = 30
    ): String {
        if (values.isEmpty()) return "$title: (No Data)"
        val maxVal = values.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0

        return buildString {
            append(title).append(":\n")
            val maxLabelLen = labels.maxOfOrNull { it.length } ?: 10
            for (i in values.indices) {
                val label = labels.getOrElse(i) { "Item $i" }.padEnd(maxLabelLen)
                val v = values[i]
                val barLen = ((v / maxVal) * maxBarWidth).roundToInt().coerceIn(0, maxBarWidth)
                val bar = "#".repeat(barLen).padEnd(maxBarWidth)
                append(String.format(Locale.US, "  %s | %s | %6.2f %s\n", label, bar, v, unit))
            }
        }
    }

    fun generateMemoryStabilityTable(cycleSnapshots: List<ResourceSnapshot>): String {
        return buildString {
            append("| Cycle | Pre-Load (KB) | Post-Load (KB) | Post-Infer (KB) | Post-Release (KB) | Net Delta vs Base (KB) |\n")
            append("| :---: | :---: | :---: | :---: | :---: | :---: |\n")

            val baseMem = cycleSnapshots.firstOrNull()?.totalProcessMemoryKb ?: 0L
            val cycles = cycleSnapshots.groupBy {
                val match = Regex("""CYCLE_?(\d+)""", RegexOption.IGNORE_CASE).find(it.phaseName)
                match?.groupValues?.get(1) ?: it.phaseName
            }.toList().sortedBy { (key, _) -> key.toIntOrNull() ?: 0 }

            for ((cycleName, snaps) in cycles) {
                val pre = snaps.find { it.workload == "PRE_LOAD_BASELINE" }?.totalProcessMemoryKb ?: 0L
                val postLoad = snaps.find { it.workload == "MODEL_LOADED" }?.totalProcessMemoryKb ?: 0L
                val postInf = snaps.find { it.workload == "POST_INFERENCE" }?.totalProcessMemoryKb ?: 0L
                val postRel = snaps.find { it.workload == "POST_RELEASE" }?.totalProcessMemoryKb ?: 0L
                val netDelta = postRel - baseMem

                append(String.format(Locale.US, "| %s | %,d | %,d | %,d | %,d | %+,d |\n",
                    cycleName, pre, postLoad, postInf, postRel, netDelta))
            }
        }
    }
}
