package algotoolkit.io

import algotoolkit.benchmark.BenchmarkRow
import java.util.Locale

object CsvExport {
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\""
        else field

    fun benchmarkCsv(rows: List<BenchmarkRow>): String = buildString {
        appendLine(
            "category,algorithm,measurement,input_size,dataset,repetitions,avg_ms,min_ms,median_ms," +
                "comparisons,swaps_or_moves,nodes_visited,operations,status,note"
        )
        for (r in rows) {
            appendLine(
                listOf(
                    r.category, r.algorithm, r.measurement, r.inputSize.toString(), r.dataset, r.repetitions.toString(),
                    ms(r.avgMs), ms(r.minMs), ms(r.medianMs),
                    r.comparisons?.toString() ?: "", r.swapsOrMoves?.toString() ?: "",
                    r.nodesVisited?.toString() ?: "", r.operations?.toString() ?: "",
                    r.status, r.note
                ).joinToString(",") { escape(it) }
            )
        }
    }

    fun summaryCsv(rows: List<Pair<String, String>>): String = buildString {
        appendLine("metric,value")
        for ((k, v) in rows) appendLine(escape(k) + "," + escape(v))
    }

    private fun ms(v: Double) = String.format(Locale.ROOT, "%.6f", v)
}
