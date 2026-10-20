package algotoolkit.model

import java.util.TreeMap
import kotlin.math.hypot

/** Geometric position of a node. Used only by the A* heuristic, never for on-screen layout. */
data class Coord(val x: Double, val y: Double)

class Node(val id: Int, val label: String, var coord: Coord? = null)

data class Edge(val a: Int, val b: Int, val weight: Double)

/** Order-independent key of an undirected edge. */
fun edgeKey(a: Int, b: Int): Long = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()

/**
 * Undirected, weighted, simple graph: no self-loops, no parallel edges.
 * Every edge weight is a positive finite number, which is what Dijkstra and A* require.
 * Node ids are stable and never reused; labels are unique.
 */
class Graph {
    private val nodeMap = LinkedHashMap<Int, Node>()
    private val labelIndex = HashMap<String, Int>()
    private val edgeMap = LinkedHashMap<Long, Edge>()
    private val adjacency = HashMap<Int, TreeMap<Int, Double>>()
    private var nextId = 0

    val nodes: List<Node> get() = nodeMap.values.toList()
    val edges: List<Edge> get() = edgeMap.values.toList()
    val nodeCount: Int get() = nodeMap.size
    val edgeCount: Int get() = edgeMap.size

    fun contains(id: Int): Boolean = nodeMap.containsKey(id)
    fun node(id: Int): Node = nodeMap[id] ?: throw IllegalArgumentException("Unknown node id $id")
    fun labelOf(id: Int): String = nodeMap[id]?.label ?: "?"
    fun nodeByLabel(label: String): Node? = labelIndex[label]?.let { nodeMap[it] }
    fun getOrAddNode(label: String): Node = nodeByLabel(label) ?: addNode(label)

    /** Adds a node. An explicit label must be unique; without one a unique label is generated. */
    fun addNode(label: String? = null, coord: Coord? = null): Node {
        val explicit = label?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) require(!labelIndex.containsKey(explicit)) { "Node label '$explicit' already exists" }
        var name = explicit ?: defaultLabel(nextId)
        while (explicit == null && labelIndex.containsKey(name)) name += "'"
        val node = Node(nextId++, name, coord)
        nodeMap[node.id] = node
        labelIndex[name] = node.id
        adjacency[node.id] = TreeMap()
        return node
    }

    fun removeNode(id: Int) {
        val node = nodeMap[id] ?: throw IllegalArgumentException("Unknown node id $id")
        for (other in adjacency.getValue(id).keys.toList()) removeEdge(id, other)
        adjacency.remove(id)
        nodeMap.remove(id)
        labelIndex.remove(node.label)
    }

    fun addEdge(a: Int, b: Int, weight: Double): Edge {
        require(nodeMap.containsKey(a) && nodeMap.containsKey(b)) { "Both endpoints must be existing nodes" }
        require(a != b) { "Self-loops are not supported" }
        require(weight.isFinite() && weight > 0.0) { "Edge weight must be a positive finite number (got $weight)" }
        val key = edgeKey(a, b)
        require(!edgeMap.containsKey(key)) { "Edge ${labelOf(a)}-${labelOf(b)} already exists" }
        val edge = Edge(minOf(a, b), maxOf(a, b), weight)
        edgeMap[key] = edge
        adjacency.getValue(a)[b] = weight
        adjacency.getValue(b)[a] = weight
        return edge
    }

    fun removeEdge(a: Int, b: Int): Boolean {
        if (edgeMap.remove(edgeKey(a, b)) == null) return false
        adjacency[a]?.remove(b)
        adjacency[b]?.remove(a)
        return true
    }

    fun hasEdge(a: Int, b: Int): Boolean = edgeMap.containsKey(edgeKey(a, b))
    fun weight(a: Int, b: Int): Double? = adjacency[a]?.get(b)

    /** Neighbours with edge weights, ordered by node id so every run is deterministic. */
    fun neighbors(id: Int): List<Pair<Int, Double>> =
        adjacency[id]?.entries?.map { it.key to it.value } ?: emptyList()

    fun componentCount(): Int {
        val seen = HashSet<Int>()
        var count = 0
        for (start in nodeMap.keys) {
            if (start in seen) continue
            count++
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            seen.add(start)
            while (stack.isNotEmpty()) {
                val u = stack.removeLast()
                for (v in adjacency.getValue(u).keys) if (seen.add(v)) stack.addLast(v)
            }
        }
        return count
    }

    fun isConnected(): Boolean = componentCount() <= 1

    fun euclid(a: Int, b: Int): Double {
        val p = node(a).coord ?: throw IllegalStateException("Node ${labelOf(a)} has no coordinates")
        val q = node(b).coord ?: throw IllegalStateException("Node ${labelOf(b)} has no coordinates")
        return hypot(p.x - q.x, p.y - q.y)
    }

    /**
     * True when every node has coordinates and every edge weight is at least the straight-line
     * distance of its endpoints. Only then is the Euclidean heuristic admissible and consistent.
     */
    fun hasConsistentCoordinates(): Boolean {
        if (nodeMap.values.any { it.coord == null }) return false
        return edgeMap.values.all { it.weight + 1e-9 >= euclid(it.a, it.b) }
    }

    fun copy(): Graph {
        val g = Graph()
        val ids = HashMap<Int, Int>()
        for (n in nodes) ids[n.id] = g.addNode(n.label, n.coord).id
        for (e in edges) g.addEdge(ids.getValue(e.a), ids.getValue(e.b), e.weight)
        return g
    }

    private fun defaultLabel(id: Int): String = if (id < 26) ('A' + id).toString() else "N$id"
}
