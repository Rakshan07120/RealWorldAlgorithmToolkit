package algotoolkit.io

import algotoolkit.algorithms.GraphAlgorithm
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DatasetIOTest {
    private fun problems(block: () -> Unit): List<String> =
        assertThrows(DatasetException::class.java) { block() }.problems

    // ---- sample files ----

    @Test
    fun graphCsvSampleLoadsAndSolves() {
        val g = DatasetIO.loadGraph(Path.of("sample-data/graph_small.csv"))
        assertEquals(6, g.nodeCount)
        assertEquals(8, g.edgeCount)
        val r = GraphAlgorithm.DIJKSTRA.run(g, g.nodeByLabel("A")!!.id, g.nodeByLabel("F")!!.id)
        assertEquals(8.0, r.pathCost!!, 1e-9)
        assertEquals(listOf("A", "C", "D", "E", "F"), r.path.map { g.labelOf(it) })
    }

    @Test
    fun graphJsonSampleLoadsWithUsableCoordinates() {
        val g = DatasetIO.loadGraph(Path.of("sample-data/graph_small.json"))
        assertEquals(6, g.nodeCount)
        assertEquals(8, g.edgeCount)
        assertTrue(g.hasConsistentCoordinates())
        val r = GraphAlgorithm.ASTAR.run(g, g.nodeByLabel("A")!!.id, g.nodeByLabel("F")!!.id)
        assertEquals(8.0, r.pathCost!!, 1e-9)
        assertTrue(r.note.startsWith("Euclidean"))
    }

    @Test
    fun csvAndJsonSamplesDescribeTheSameGraph() {
        val csv = DatasetIO.loadGraph(Path.of("sample-data/graph_small.csv"))
        val json = DatasetIO.loadGraph(Path.of("sample-data/graph_small.json"))
        fun edges(g: algotoolkit.model.Graph) =
            g.edges.map { setOf(g.labelOf(it.a), g.labelOf(it.b)) to it.weight }.toSet()
        assertEquals(edges(csv), edges(json))
    }

    @Test
    fun disconnectedSampleHasTwoComponents() {
        val g = DatasetIO.loadGraph(Path.of("sample-data/graph_disconnected.csv"))
        assertEquals(2, g.componentCount())
    }

    @Test
    fun invalidSampleReportsEveryBadRecord() {
        val p = problems { DatasetIO.loadGraph(Path.of("sample-data/invalid_graph.csv")) }
        assertEquals(5, p.size, p.toString())
        assertTrue(p.any { it.startsWith("Line 3") && it.contains("positive") })
        assertTrue(p.any { it.startsWith("Line 4") && it.contains("Self-loops") })
        assertTrue(p.any { it.startsWith("Line 5") && it.contains("abc") })
        assertTrue(p.any { it.startsWith("Line 6") && it.contains("already exists") })
        assertTrue(p.any { it.startsWith("Line 7") && it.contains("3 fields") })
    }

    @Test
    fun arraySamplesLoad() {
        assertArrayEquals(
            intArrayOf(42, 17, 93, 5, 68, 17, 29, 81, 3, 56, 74, 11),
            DatasetIO.loadArray(Path.of("sample-data/array_unsorted.csv"))
        )
        assertEquals(15, DatasetIO.loadArray(Path.of("sample-data/array_sorted.json")).size)
        assertArrayEquals(
            intArrayOf(9, 4, 7, 4, 1, 8, 15, 2, 11, 6),
            DatasetIO.loadArray(Path.of("sample-data/array_unsorted.json"))
        )
    }

    // ---- graph CSV ----

    @Test
    fun graphCsvAcceptsQuotedLabelsBlankLinesAndCrLf() {
        val g = DatasetIO.graphFromCsv("Source,Target,Weight\r\n\"A, one\",B,1.5\r\n\r\nB,C,2\r\n")
        assertEquals(3, g.nodeCount)
        assertEquals(2, g.edgeCount)
        assertEquals(1.5, g.weight(g.nodeByLabel("A, one")!!.id, g.nodeByLabel("B")!!.id)!!, 1e-12)
    }

    @Test
    fun graphCsvEmptyAndHeaderOnlyFiles() {
        assertTrue(problems { DatasetIO.graphFromCsv("") }[0].contains("empty"))
        assertTrue(problems { DatasetIO.graphFromCsv("  \n\n") }[0].contains("empty"))
        assertTrue(problems { DatasetIO.graphFromCsv("source,target,weight\n") }[0].contains("no edges"))
    }

    @Test
    fun graphCsvWrongHeader() {
        assertTrue(problems { DatasetIO.graphFromCsv("a,b,c\nA,B,1") }[0].contains("expected header"))
    }

    @Test
    fun graphCsvInvalidWeights() {
        for (bad in listOf("0", "-1", "abc", "NaN", "Infinity", "")) {
            val p = problems { DatasetIO.graphFromCsv("source,target,weight\nA,B,$bad") }
            assertEquals(1, p.size, "weight '$bad'")
            assertTrue(p[0].startsWith("Line 2"), p[0])
        }
    }

    @Test
    fun graphCsvMissingFieldsAndEmptyNames() {
        assertTrue(problems { DatasetIO.graphFromCsv("source,target,weight\nA,B") }[0].contains("3 fields"))
        assertTrue(problems { DatasetIO.graphFromCsv("source,target,weight\n,B,1") }[0].contains("empty"))
    }

    @Test
    fun graphCsvUnterminatedQuote() {
        assertTrue(problems { DatasetIO.graphFromCsv("source,target,weight\n\"A,B,1") }[0].contains("unterminated"))
    }

    @Test
    fun graphCsvReversedDuplicateIsRejected() {
        val p = problems { DatasetIO.graphFromCsv("source,target,weight\nA,B,1\nB,A,1") }
        assertTrue(p[0].contains("already exists"))
    }

    @Test
    fun graphCsvTooManyProblemsAreSummarised() {
        val text = "source,target,weight\n" + (1..80).joinToString("\n") { "A$it,B$it,x" }
        val p = problems { DatasetIO.graphFromCsv(text) }
        assertEquals(51, p.size)
        assertTrue(p.last().contains("30 more"))
    }

    // ---- graph JSON ----

    @Test
    fun graphJsonWithoutNodesCreatesThemFromEdges() {
        val g = DatasetIO.graphFromJson("""{"edges":[{"source":"X","target":"Y","weight":2},{"source":1,"target":"Y","weight":3}]}""")
        assertEquals(3, g.nodeCount)
        assertEquals(2, g.edgeCount)
    }

    @Test
    fun graphJsonProblems() {
        assertTrue(problems { DatasetIO.graphFromJson("") }[0].contains("empty"))
        assertTrue(problems { DatasetIO.graphFromJson("{not json") }[0].contains("Malformed JSON"))
        assertTrue(problems { DatasetIO.graphFromJson("[1,2]") }[0].contains("object"))
        assertTrue(problems { DatasetIO.graphFromJson("{}") }[0].contains("edges"))
        assertTrue(problems { DatasetIO.graphFromJson("""{"edges":5}""") }[0].contains("array"))
        assertTrue(problems { DatasetIO.graphFromJson("""{"directed":true,"edges":[]}""") }[0].contains("undirected"))
        assertTrue(problems { DatasetIO.graphFromJson("""{"edges":[]}""") }[0].contains("no nodes"))
    }

    @Test
    fun graphJsonEdgeProblems() {
        val p = problems {
            DatasetIO.graphFromJson(
                """{"nodes":["A","B"],"edges":[
                    {"source":"A","target":"B","weight":"4"},
                    {"source":"A","target":"Z","weight":1},
                    {"source":"A","weight":1},
                    {"source":"A","target":"B","weight":-3},
                    5
                ]}"""
            )
        }
        assertEquals(5, p.size, p.toString())
        assertTrue(p[0].startsWith("edges[0]") && p[0].contains("number"))
        assertTrue(p[1].contains("unknown node 'Z'"))
        assertTrue(p[2].contains("'source' and 'target'"))
        assertTrue(p[3].contains("positive"))
        assertTrue(p[4].startsWith("edges[4]"))
    }

    @Test
    fun graphJsonNodeProblems() {
        val p = problems {
            DatasetIO.graphFromJson(
                """{"nodes":[{"id":"A","x":1},{"id":"B"},{"id":"B"},{"x":1,"y":1}],"edges":[]}"""
            )
        }
        assertEquals(3, p.size, p.toString())
        assertTrue(p[0].contains("'x' and 'y'"))
        assertTrue(p[1].contains("duplicate"))
        assertTrue(p[2].contains("'id'"))
    }

    // ---- arrays ----

    @Test
    fun arrayCsvVariants() {
        assertArrayEquals(intArrayOf(3, 1, 2), DatasetIO.arrayFromCsv("3\n1\n2"))
        assertArrayEquals(intArrayOf(3, 1, 2, 9), DatasetIO.arrayFromCsv("value\n3, 1, 2\n9"))
        assertArrayEquals(intArrayOf(-4, 0), DatasetIO.arrayFromCsv("-4\n0\n"))
    }

    @Test
    fun arrayCsvProblems() {
        assertTrue(problems { DatasetIO.arrayFromCsv("") }[0].contains("empty"))
        assertTrue(problems { DatasetIO.arrayFromCsv("value\n") }[0].contains("no values"))
        val p = problems { DatasetIO.arrayFromCsv("1\n2.5\nabc\n4,,5") }
        assertEquals(3, p.size, p.toString())
        assertTrue(p[0].contains("2.5"))
        assertTrue(p[1].contains("abc"))
        assertTrue(p[2].contains("missing"))
    }

    @Test
    fun arrayJsonVariants() {
        assertArrayEquals(intArrayOf(1, 2, 3), DatasetIO.arrayFromJson("[1,2,3]"))
        assertArrayEquals(intArrayOf(1, 2, 3), DatasetIO.arrayFromJson("""{"values":[1,2,3]}"""))
    }

    @Test
    fun arrayJsonProblems() {
        assertTrue(problems { DatasetIO.arrayFromJson("") }[0].contains("empty"))
        assertTrue(problems { DatasetIO.arrayFromJson("[]") }[0].contains("empty"))
        assertTrue(problems { DatasetIO.arrayFromJson("\"text\"") }[0].contains("Expected"))
        val p = problems { DatasetIO.arrayFromJson("""[1, 2.5, "x", 99999999999]""") }
        assertEquals(3, p.size, p.toString())
    }

    // ---- files ----

    @Test
    fun fileLevelErrors(@TempDir dir: Path) {
        assertTrue(problems { DatasetIO.loadGraph(dir.resolve("missing.csv")) }[0].contains("not found"))
        val txt = Files.writeString(dir.resolve("graph.txt"), "source,target,weight\nA,B,1")
        assertTrue(problems { DatasetIO.loadGraph(txt) }[0].contains("Unsupported"))
        assertTrue(problems { DatasetIO.loadArray(txt) }[0].contains("Unsupported"))
        val empty = Files.writeString(dir.resolve("empty.csv"), "")
        assertTrue(problems { DatasetIO.loadArray(empty) }[0].contains("empty"))
        val binary = dir.resolve("binary.json")
        Files.write(binary, byteArrayOf(0x7B, 0xFF.toByte(), 0xFE.toByte(), 0x7D))
        assertTrue(problems { DatasetIO.loadGraph(binary) }[0].contains("UTF-8"))
    }

    @Test
    fun bomIsIgnored(@TempDir dir: Path) {
        val f = Files.writeString(dir.resolve("g.csv"), "\uFEFFsource,target,weight\nA,B,1")
        assertEquals(1, DatasetIO.loadGraph(f).edgeCount)
    }

    @Test
    fun previewsDescribeTheData() {
        val g = DatasetIO.graphFromCsv("source,target,weight\nA,B,4")
        val gp = DatasetPreview.graph(g)
        assertTrue(gp.contains("Nodes: 2") && gp.contains("A - B"))
        val ap = DatasetPreview.array(intArrayOf(3, 1, 2))
        assertTrue(ap.contains("Min: 1") && ap.contains("Max: 3") && ap.contains("sorted ascending: no"))
        assertFalse(ap.contains("sorted ascending: yes"))
    }
}
