package algotoolkit.benchmark

import algotoolkit.algorithms.GraphAlgorithm
import algotoolkit.algorithms.SearchAlgorithm
import algotoolkit.algorithms.SortAlgorithm
import algotoolkit.io.CsvExport
import algotoolkit.model.DataPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BenchmarkTest {
    private val cfg = BenchmarkConfig(warmup = 0, repetitions = 3)

    @Test
    fun sortingProducesOneVerifiedRowPerAlgorithmAndSize() {
        val rows = Benchmarker.sorting(SortAlgorithm.entries, listOf(10, 50), DataPattern.RANDOM, cfg)
        assertEquals(10, rows.size)
        for (r in rows) {
            assertEquals("OK", r.status, r.algorithm)
            assertTrue(r.avgMs >= 0 && r.minMs >= 0 && r.medianMs >= 0)
            assertTrue(r.minMs <= r.medianMs && r.minMs <= r.avgMs)
            assertEquals(3, r.repetitions)
            assertTrue(r.comparisons!! > 0)
            assertTrue(r.operations!! >= r.comparisons!!)
            assertTrue(r.inputSize == 10 || r.inputSize == 50)
        }
    }

    @Test
    fun operationCountsAreDeterministicForIdenticalInput() {
        val a = Benchmarker.sorting(SortAlgorithm.entries, listOf(100), DataPattern.RANDOM, cfg)
        val b = Benchmarker.sorting(SortAlgorithm.entries, listOf(100), DataPattern.RANDOM, cfg)
        assertEquals(a.map { it.comparisons }, b.map { it.comparisons })
        assertEquals(a.map { it.swapsOrMoves }, b.map { it.swapsOrMoves })
    }

    @Test
    fun sortingHandlesEveryPatternAndTinySizes() {
        for (pattern in DataPattern.entries) {
            val rows = Benchmarker.sorting(listOf(SortAlgorithm.QUICK, SortAlgorithm.MERGE), listOf(0, 1, 2, 30), pattern, cfg)
            assertEquals(8, rows.size, pattern.label)
            assertTrue(rows.all { it.status == "OK" }, pattern.label)
        }
    }

    @Test
    fun sortedInputNeedsFewerBubbleComparisonsThanReversed() {
        val sorted = Benchmarker.sorting(listOf(SortAlgorithm.BUBBLE), listOf(200), DataPattern.SORTED, cfg)[0]
        val reversed = Benchmarker.sorting(listOf(SortAlgorithm.BUBBLE), listOf(200), DataPattern.REVERSED, cfg)[0]
        assertEquals(199L, sorted.comparisons!!)
        assertTrue(reversed.comparisons!! > sorted.comparisons!!)
    }

    @Test
    fun searchingMeasuresBatchesOfQueries() {
        val rows = Benchmarker.searching(SearchAlgorithm.entries, listOf(100, 1000), cfg)
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.status == "OK" })
        assertTrue(rows.all { it.measurement.contains("${Benchmarker.SEARCH_QUERIES} queries") })
        val linear = rows.first { it.algorithm == SearchAlgorithm.LINEAR.label && it.inputSize == 1000 }
        val binary = rows.first { it.algorithm == SearchAlgorithm.BINARY.label && it.inputSize == 1000 }
        assertTrue(binary.comparisons!! < linear.comparisons!!)
    }

    @Test
    fun graphRowsDistinguishTraversalFromShortestPath() {
        val rows = Benchmarker.graphs(GraphAlgorithm.entries, listOf(20, 50), 2, cfg)
        assertEquals(8, rows.size)
        for (r in rows) {
            val expected = if (r.algorithm in listOf("Breadth-First Search", "Depth-First Search")) "traversal" else "shortest path"
            assertEquals(expected, r.measurement, r.algorithm)
            assertEquals("PATH_FOUND", r.status)
            assertTrue(r.nodesVisited!! >= 1 && r.operations!! >= 1)
        }
    }

    @Test
    fun dijkstraAndAstarAgreeOnTheNumberOfOutcomesButNotNecessarilyWork() {
        val rows = Benchmarker.graphs(listOf(GraphAlgorithm.DIJKSTRA, GraphAlgorithm.ASTAR), listOf(100), 2, cfg)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.operations!! > 0 })
    }

    @Test
    fun cancellationStopsEarly() {
        assertTrue(Benchmarker.sorting(SortAlgorithm.entries, listOf(10), DataPattern.RANDOM, cfg, cancelled = { true }).isEmpty())
        assertTrue(Benchmarker.searching(SearchAlgorithm.entries, listOf(10), cfg, cancelled = { true }).isEmpty())
        assertTrue(Benchmarker.graphs(GraphAlgorithm.entries, listOf(10), 1, cfg, cancelled = { true }).isEmpty())
    }

    @Test
    fun progressIsReported() {
        val messages = ArrayList<String>()
        Benchmarker.sorting(listOf(SortAlgorithm.BUBBLE), listOf(10), DataPattern.RANDOM, cfg, progress = { messages.add(it) })
        assertEquals(1, messages.size)
    }

    @Test
    fun invalidInputsAndConfigAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BenchmarkConfig(repetitions = 0) }
        assertThrows(IllegalArgumentException::class.java) { BenchmarkConfig(warmup = -1) }
        assertThrows(IllegalArgumentException::class.java) { Benchmarker.searching(SearchAlgorithm.entries, listOf(0), cfg) }
        assertThrows(IllegalArgumentException::class.java) { Benchmarker.graphs(GraphAlgorithm.entries, listOf(1), 1, cfg) }
        assertThrows(IllegalArgumentException::class.java) { Benchmarker.sorting(SortAlgorithm.entries, listOf(-1), DataPattern.RANDOM, cfg) }
    }

    @Test
    fun timeRunsSetupOutsideTheMeasuredRegionAndCountsRuns() {
        var setups = 0
        var runs = 0
        val t = Benchmarker.time(BenchmarkConfig(warmup = 2, repetitions = 4), { setups++ }) { runs++ }
        assertEquals(6, setups)
        assertEquals(6, runs)
        assertTrue(t.minMs <= t.medianMs)
    }

    @Test
    fun csvExportContainsHeaderAndOneLinePerRow() {
        val rows = Benchmarker.sorting(listOf(SortAlgorithm.MERGE, SortAlgorithm.QUICK), listOf(10), DataPattern.RANDOM, cfg)
        val lines = CsvExport.benchmarkCsv(rows).trim().lines()
        assertEquals(rows.size + 1, lines.size)
        assertTrue(lines[0].startsWith("category,algorithm,measurement,input_size"))
        assertTrue(lines[1].startsWith("Sorting,Merge Sort,sort,10,"))
    }

    @Test
    fun csvEscaping() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        val csv = CsvExport.summaryCsv(listOf("Path" to "A, B", "Cost" to "3"))
        assertEquals("metric,value\nPath,\"A, B\"\nCost,3", csv.trim().replace("\r\n", "\n"))
    }
}
