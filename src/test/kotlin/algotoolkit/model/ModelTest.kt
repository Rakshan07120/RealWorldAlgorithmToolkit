package algotoolkit.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {
    @Test
    fun graphRejectsInvalidEdges() {
        val g = Graph()
        val a = g.addNode().id
        val b = g.addNode().id
        g.addEdge(a, b, 2.0)
        assertThrows(IllegalArgumentException::class.java) { g.addEdge(a, a, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { g.addEdge(a, b, 3.0) }
        assertThrows(IllegalArgumentException::class.java) { g.addEdge(b, a, 3.0) }
        assertThrows(IllegalArgumentException::class.java) { g.addEdge(a, 99, 1.0) }
        assertEquals(1, g.edgeCount)
    }

    @Test
    fun edgesAreUndirected() {
        val g = Graph()
        val a = g.addNode().id
        val b = g.addNode().id
        g.addEdge(a, b, 2.5)
        assertEquals(2.5, g.weight(b, a)!!, 1e-12)
        assertTrue(g.hasEdge(b, a))
        assertEquals(listOf(b to 2.5), g.neighbors(a))
    }

    @Test
    fun removingANodeRemovesItsEdges() {
        val g = Graph()
        val a = g.addNode().id
        val b = g.addNode().id
        val c = g.addNode().id
        g.addEdge(a, b, 1.0)
        g.addEdge(b, c, 1.0)
        g.removeNode(b)
        assertEquals(2, g.nodeCount)
        assertEquals(0, g.edgeCount)
        assertFalse(g.contains(b))
        assertTrue(g.neighbors(a).isEmpty())
        assertEquals(2, g.componentCount())
    }

    @Test
    fun removedNodeIdsAreNotReused() {
        val g = Graph()
        val a = g.addNode().id
        g.removeNode(a)
        assertTrue(g.addNode().id != a)
    }

    @Test
    fun labelsAreUniqueAndGenerated() {
        val g = Graph()
        assertEquals("A", g.addNode().label)
        assertEquals("B", g.addNode().label)
        g.addNode("custom")
        assertThrows(IllegalArgumentException::class.java) { g.addNode("custom") }
        assertEquals(g.nodeByLabel("custom")!!.id, g.getOrAddNode("custom").id)
        assertEquals(3, g.nodeCount)
    }

    @Test
    fun copyIsIndependent() {
        val g = GraphGenerator.randomConnected(8, 5, 1)
        val c = g.copy()
        assertEquals(g.nodeCount, c.nodeCount)
        assertEquals(g.edgeCount, c.edgeCount)
        c.removeNode(c.nodes.first().id)
        assertEquals(8, g.nodeCount)
    }

    @Test
    fun randomGraphsAreConnectedWithRequestedSize() {
        for (seed in 1L..20L) {
            val g = GraphGenerator.randomConnected(30, 25, seed)
            assertEquals(30, g.nodeCount)
            assertEquals(29 + 25, g.edgeCount)
            assertTrue(g.isConnected())
            assertTrue(g.edges.all { it.weight > 0 && it.weight.isFinite() })
        }
    }

    @Test
    fun randomGraphIsDeterministicForASeed() {
        val a = GraphGenerator.randomConnected(20, 10, 99)
        val b = GraphGenerator.randomConnected(20, 10, 99)
        assertEquals(a.edges, b.edges)
    }

    @Test
    fun extraEdgesAreCappedAtTheMaximum() {
        val g = GraphGenerator.randomConnected(5, 1000, 3)
        assertEquals(10, g.edgeCount)
    }

    @Test
    fun smallestGraphsWork() {
        assertEquals(0, GraphGenerator.randomConnected(1, 5, 1).edgeCount)
        assertEquals(1, GraphGenerator.randomConnected(2, 5, 1).edgeCount)
    }

    @Test
    fun invalidGeneratorArgumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GraphGenerator.randomConnected(0, 0) }
        assertThrows(IllegalArgumentException::class.java) { GraphGenerator.randomConnected(3, -1) }
    }

    @Test
    fun dataPatternsHaveTheirDefiningProperty() {
        assertArrayEquals(IntArray(10) { it }, DataGenerator.generate(DataPattern.SORTED, 10, 1))
        assertArrayEquals(IntArray(10) { 9 - it }, DataGenerator.generate(DataPattern.REVERSED, 10, 1))
        assertTrue(DataGenerator.generate(DataPattern.FEW_UNIQUE, 200, 1).distinct().size <= 10)
        assertEquals(100, DataGenerator.generate(DataPattern.RANDOM, 100, 1).size)
        val nearly = DataGenerator.generate(DataPattern.NEARLY_SORTED, 100, 1)
        assertEquals((0 until 100).toList(), nearly.sorted())
        assertEquals(0, DataGenerator.generate(DataPattern.RANDOM, 0, 1).size)
    }

    @Test
    fun dataGenerationIsDeterministicForASeed() {
        assertArrayEquals(
            DataGenerator.generate(DataPattern.RANDOM, 50, 7),
            DataGenerator.generate(DataPattern.RANDOM, 50, 7)
        )
    }

    @Test
    fun numberFormatting() {
        assertEquals("4", formatNumber(4.0))
        assertEquals("4.5", formatNumber(4.5))
        assertEquals("0.123", formatNumber(0.1234))
        assertTrue(formatMs(0.0005).endsWith("ns"))
        assertTrue(formatMs(5.0).endsWith("ms"))
    }
}
