package algotoolkit.benchmark

import algotoolkit.algorithms.GraphAlgorithm
import algotoolkit.algorithms.ResultKind
import algotoolkit.algorithms.GraphResult
import algotoolkit.algorithms.SearchAlgorithm
import algotoolkit.algorithms.Searching
import algotoolkit.algorithms.SortAlgorithm
import algotoolkit.algorithms.SortContext
import algotoolkit.algorithms.isSortedAscending
import algotoolkit.model.DataGenerator
import algotoolkit.model.DataPattern
import algotoolkit.model.GraphGenerator
import kotlin.random.Random

class BenchmarkConfig(val warmup: Int = 3, val repetitions: Int = 10) {
    init {
        require(warmup >= 0) { "Warm-up runs cannot be negative" }
        require(repetitions >= 1) { "At least one measured repetition is required" }
    }
}

class Timing(val avgMs: Double, val minMs: Double, val medianMs: Double)

data class BenchmarkRow(
    val category: String,
    val algorithm: String,
    val measurement: String,
    val inputSize: Int,
    val dataset: String,
    val repetitions: Int,
    val avgMs: Double,
    val minMs: Double,
    val medianMs: Double,
    val comparisons: Long?,
    val swapsOrMoves: Long?,
    val nodesVisited: Long?,
    val operations: Long?,
    val status: String,
    val note: String
)

/**
 * Measures the algorithm calls only. Input preparation (copying arrays, generating graphs or data) happens
 * outside the timed region, and no UI code is involved. Every algorithm of one size receives identical input.
 */
object Benchmarker {
    const val SEARCH_QUERIES = 500

    @Volatile
    private var sink = 0L

    /** Runs `setup` + `run` for the warm-up and for each repetition; only `run` is timed. */
    fun <T> time(cfg: BenchmarkConfig, setup: () -> T, run: (T) -> Unit): Timing {
        repeat(cfg.warmup) { run(setup()) }
        val times = DoubleArray(cfg.repetitions)
        for (r in times.indices) {
            val input = setup()
            val start = System.nanoTime()
            run(input)
            times[r] = (System.nanoTime() - start) / 1e6
        }
        times.sort()
        val mid = times.size / 2
        val median = if (times.size % 2 == 1) times[mid] else (times[mid - 1] + times[mid]) / 2
        return Timing(times.average(), times.first(), median)
    }

    fun sorting(
        algorithms: List<SortAlgorithm>,
        sizes: List<Int>,
        pattern: DataPattern,
        cfg: BenchmarkConfig,
        seed: Long = 42,
        progress: (String) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): List<BenchmarkRow> {
        require(sizes.all { it >= 0 }) { "Input sizes cannot be negative" }
        val rows = ArrayList<BenchmarkRow>()
        for (n in sizes) {
            val base = DataGenerator.generate(pattern, n, seed + n)
            for (algo in algorithms) {
                if (cancelled()) return rows
                progress("Sorting: ${algo.label}, n = $n")
                var last: SortContext? = null
                val t = time(cfg, { base.copyOf() }) { arr -> last = algo.sort(arr) }
                val ctx = last!!
                val ok = ctx.data.isSortedAscending()
                rows.add(
                    BenchmarkRow(
                        "Sorting", algo.label, "sort", n, "${pattern.label}, $n elements", cfg.repetitions,
                        t.avgMs, t.minMs, t.medianMs, ctx.comparisons, ctx.swaps + ctx.writes, null,
                        ctx.comparisons + ctx.swaps + ctx.writes,
                        if (ok) "OK" else "FAILED: output not sorted", "Identical input for every algorithm"
                    )
                )
            }
        }
        return rows
    }

    /**
     * Each measurement runs [SEARCH_QUERIES] queries (half present, half absent) over the same sorted array,
     * because a single search takes only nanoseconds. Comparisons are totals over the whole batch.
     */
    fun searching(
        algorithms: List<SearchAlgorithm>,
        sizes: List<Int>,
        cfg: BenchmarkConfig,
        seed: Long = 42,
        progress: (String) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): List<BenchmarkRow> {
        require(sizes.all { it >= 1 }) { "Input sizes must be at least 1" }
        val rows = ArrayList<BenchmarkRow>()
        for (n in sizes) {
            val data = IntArray(n) { it * 2 }
            val rnd = Random(seed + n)
            val targets = IntArray(SEARCH_QUERIES) { q ->
                if (q % 2 == 0) rnd.nextInt(n) * 2 else rnd.nextInt(n) * 2 + 1
            }
            val expectedHits = (SEARCH_QUERIES + 1) / 2
            for (algo in algorithms) {
                if (cancelled()) return rows
                progress("Searching: ${algo.label}, n = $n")
                var comparisons = 0L
                var hits = 0
                val t = time(cfg, { Unit }) {
                    var c = 0L
                    var h = 0
                    for (x in targets) {
                        val (index, cmp) =
                            if (algo == SearchAlgorithm.LINEAR) Searching.linear(data, x) else Searching.binary(data, x)
                        c += cmp
                        if (index >= 0) h++
                    }
                    comparisons = c
                    hits = h
                    sink += c
                }
                rows.add(
                    BenchmarkRow(
                        "Searching", algo.label, "search ($SEARCH_QUERIES queries)", n,
                        "sorted, $n elements", cfg.repetitions,
                        t.avgMs, t.minMs, t.medianMs, comparisons, null, null, comparisons,
                        if (hits == expectedHits) "OK" else "FAILED: wrong number of hits",
                        "Time and comparisons are per batch of $SEARCH_QUERIES queries"
                    )
                )
            }
        }
        return rows
    }

    fun graphs(
        algorithms: List<GraphAlgorithm>,
        sizes: List<Int>,
        extraEdgesPerNode: Int,
        cfg: BenchmarkConfig,
        seed: Long = 42,
        progress: (String) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): List<BenchmarkRow> {
        require(sizes.all { it >= 2 }) { "Graphs need at least 2 nodes" }
        require(extraEdgesPerNode >= 0) { "Extra edges per node cannot be negative" }
        val rows = ArrayList<BenchmarkRow>()
        for (n in sizes) {
            val graph = GraphGenerator.randomConnected(n, extraEdgesPerNode * n, seed + n)
            val start = graph.nodes.first().id
            val target = graph.nodes.last().id
            for (algo in algorithms) {
                if (cancelled()) return rows
                progress("Graph: ${algo.label}, n = $n")
                var last: GraphResult? = null
                val t = time(cfg, { Unit }) { last = algo.run(graph, start, target, false) }
                val r = last!!
                rows.add(
                    BenchmarkRow(
                        "Graph", algo.label, algo.kind.label, n,
                        "random connected graph, $n nodes, ${graph.edgeCount} edges", cfg.repetitions,
                        t.avgMs, t.minMs, t.medianMs, null, null, r.nodesVisited, r.operations,
                        r.status.name,
                        if (algo.kind == ResultKind.TRAVERSAL) "Stops at the target; path is not weight-optimal"
                        else r.note
                    )
                )
            }
        }
        return rows
    }
}
