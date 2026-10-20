package algotoolkit.algorithms

import algotoolkit.model.Coord
import algotoolkit.model.Graph
import algotoolkit.model.GraphGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphAlgorithmsTest {
    private fun graphOf(vararg edges: Triple<String, String, Double>): Graph {
        val g = Graph()
        for ((a, b, w) in edges) g.addEdge(g.getOrAddNode(a).id, g.getOrAddNode(b).id, w)
        return g
    }

    private fun id(g: Graph, label: String) = g.nodeByLabel(label)!!.id

    private fun labels(g: Graph, r: GraphResult) = r.path.map { g.labelOf(it) }

    /** A-B 4, A-C 2, B-D 5, C-D 1: cheapest A to D is A-C-D (3). */
    private fun sample() = graphOf(
        Triple("A", "B", 4.0), Triple("A", "C", 2.0), Triple("B", "D", 5.0), Triple("C", "D", 1.0)
    )

    @Test
    fun dijkstraFindsCheapestPath() {
        val g = sample()
        val r = GraphAlgorithm.DIJKSTRA.run(g, id(g, "A"), id(g, "D"))
        assertEquals(PathStatus.PATH_FOUND, r.status)
        assertEquals(listOf("A", "C", "D"), labels(g, r))
        assertEquals(3.0, r.pathCost!!, 1e-9)
    }

    @Test
    fun astarFindsCheapestPathWithoutCoordinates() {
        val g = sample()
        val r = GraphAlgorithm.ASTAR.run(g, id(g, "A"), id(g, "D"))
        assertEquals(listOf("A", "C", "D"), labels(g, r))
        assertEquals(3.0, r.pathCost!!, 1e-9)
        assertTrue(r.note.startsWith("Zero heuristic"))
    }

    @Test
    fun bfsMinimisesEdgesAndDijkstraMinimisesWeight() {
        val g = graphOf(
            Triple("A", "D", 10.0), Triple("A", "B", 1.0), Triple("B", "C", 1.0), Triple("C", "D", 1.0)
        )
        val bfs = GraphAlgorithm.BFS.run(g, id(g, "A"), id(g, "D"))
        assertEquals(listOf("A", "D"), labels(g, bfs))
        assertEquals(10.0, bfs.pathCost!!, 1e-9)
        val dijkstra = GraphAlgorithm.DIJKSTRA.run(g, id(g, "A"), id(g, "D"))
        assertEquals(listOf("A", "B", "C", "D"), labels(g, dijkstra))
        assertEquals(3.0, dijkstra.pathCost!!, 1e-9)
        assertEquals(ResultKind.TRAVERSAL, bfs.algorithm.kind)
        assertEquals(ResultKind.SHORTEST_PATH, dijkstra.algorithm.kind)
    }

    @Test
    fun dfsReturnsAValidPath() {
        val g = sample()
        val r = GraphAlgorithm.DFS.run(g, id(g, "A"), id(g, "D"))
        assertEquals(PathStatus.PATH_FOUND, r.status)
        assertEquals(id(g, "A"), r.path.first())
        assertEquals(id(g, "D"), r.path.last())
        for ((a, b) in r.path.zipWithNext()) assertTrue(g.hasEdge(a, b))
    }

    @Test
    fun cyclesAreHandledAndEveryNodeIsVisitedOnce() {
        val g = graphOf(Triple("A", "B", 1.0), Triple("B", "C", 1.0), Triple("C", "A", 1.0))
        for (algo in GraphAlgorithm.entries.filter { it != GraphAlgorithm.ASTAR }) {
            val r = algo.run(g, id(g, "A"), null)
            assertEquals(3, r.visitOrder.size, "$algo")
            assertEquals(r.visitOrder.distinct(), r.visitOrder, "$algo")
            assertEquals(PathStatus.TRAVERSAL_COMPLETE, r.status)
        }
    }

    @Test
    fun missingPathIsDistinctFromAValidPath() {
        val g = graphOf(Triple("A", "B", 1.0), Triple("C", "D", 1.0))
        for (algo in GraphAlgorithm.entries) {
            val r = algo.run(g, id(g, "A"), id(g, "D"))
            assertEquals(PathStatus.NO_PATH, r.status, "$algo")
            assertTrue(r.path.isEmpty(), "$algo")
            assertNull(r.pathCost, "$algo")
        }
    }

    @Test
    fun traversalOfDisconnectedGraphStaysInTheStartComponent() {
        val g = graphOf(Triple("A", "B", 1.0), Triple("B", "C", 1.0), Triple("D", "E", 1.0))
        assertEquals(3, GraphAlgorithm.BFS.run(g, id(g, "A"), null).nodesVisited.toInt())
        assertEquals(3, GraphAlgorithm.DFS.run(g, id(g, "A"), null).nodesVisited.toInt())
        assertEquals(3, GraphAlgorithm.DIJKSTRA.run(g, id(g, "A"), null).nodesVisited.toInt())
        assertEquals(2, g.componentCount())
        assertFalse(g.isConnected())
    }

    @Test
    fun startEqualsTarget() {
        val g = sample()
        for (algo in GraphAlgorithm.entries) {
            val r = algo.run(g, id(g, "A"), id(g, "A"))
            assertEquals(PathStatus.PATH_FOUND, r.status, "$algo")
            assertEquals(listOf(id(g, "A")), r.path, "$algo")
            assertEquals(0.0, r.pathCost!!, 0.0, "$algo")
        }
    }

    @Test
    fun singleNodeGraph() {
        val g = Graph()
        val a = g.addNode().id
        val r = GraphAlgorithm.BFS.run(g, a, null)
        assertEquals(listOf(a), r.visitOrder)
    }

    @Test
    fun unknownNodesAreRejected() {
        val g = sample()
        assertThrows(IllegalArgumentException::class.java) { GraphAlgorithm.BFS.run(g, 999, null) }
        assertThrows(IllegalArgumentException::class.java) { GraphAlgorithm.BFS.run(g, id(g, "A"), 999) }
    }

    @Test
    fun astarRequiresATarget() {
        val g = sample()
        assertThrows(IllegalArgumentException::class.java) { GraphAlgorithm.ASTAR.run(g, id(g, "A"), null) }
    }

    @Test
    fun invalidWeightsCannotEnterAGraph() {
        val g = Graph()
        val a = g.addNode().id
        val b = g.addNode().id
        for (w in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java, { g.addEdge(a, b, w) }, "weight $w")
        }
        assertEquals(0, g.edgeCount)
    }

    @Test
    fun astarMatchesDijkstraOnRandomGraphsAndUsesCoordinates() {
        for (seed in 1L..30L) {
            val g = GraphGenerator.randomConnected(40, 60, seed)
            assertTrue(g.hasConsistentCoordinates())
            val start = g.nodes.first().id
            val target = g.nodes.last().id
            val d = GraphAlgorithm.DIJKSTRA.run(g, start, target)
            val a = GraphAlgorithm.ASTAR.run(g, start, target)
            assertEquals(d.pathCost!!, a.pathCost!!, 1e-6, "seed $seed")
            assertTrue(a.note.startsWith("Euclidean"))
        }
    }

    @Test
    fun astarFallsBackToZeroHeuristicWhenCoordinatesContradictWeights() {
        val g = Graph()
        val a = g.addNode("A", Coord(0.0, 0.0)).id
        val b = g.addNode("B", Coord(100.0, 0.0)).id
        g.addEdge(a, b, 1.0)
        assertFalse(g.hasConsistentCoordinates())
        val r = GraphAlgorithm.ASTAR.run(g, a, b)
        assertEquals(1.0, r.pathCost!!, 1e-9)
        assertTrue(r.note.startsWith("Zero heuristic"))
    }

    @Test
    fun recordedStepsAlwaysEndWithFinish() {
        val g = sample()
        for (algo in GraphAlgorithm.entries) {
            val r = algo.run(g, id(g, "A"), id(g, "D"), record = true)
            val last = r.steps.last()
            assertTrue(last is GraphStep.Finish, "$algo")
            assertEquals(r.path, (last as GraphStep.Finish).path)
            assertEquals(r.nodesVisited.toInt(), r.steps.count { it is GraphStep.Visit })
            assertEquals(r.edgesExamined.toInt(), r.steps.count { it is GraphStep.Examine })
        }
        val notFound = graphOf(Triple("A", "B", 1.0), Triple("C", "D", 1.0))
        val r = GraphAlgorithm.BFS.run(notFound, id(notFound, "A"), id(notFound, "D"), record = true)
        assertEquals(GraphStep.Finish(emptyList()), r.steps.last())
    }

    @Test
    fun stepsAreNotRecordedUnlessRequested() {
        val g = sample()
        assertTrue(GraphAlgorithm.BFS.run(g, id(g, "A"), id(g, "D")).steps.isEmpty())
    }

    @Test
    fun countersArePopulated() {
        val g = sample()
        val r = GraphAlgorithm.DIJKSTRA.run(g, id(g, "A"), id(g, "D"))
        assertTrue(r.nodesVisited >= 1 && r.edgesExamined >= 1 && r.queueOperations >= 2)
        assertEquals(r.nodesVisited + r.edgesExamined + r.queueOperations, r.operations)
        assertNotNull(r.visitOrder)
    }
}
