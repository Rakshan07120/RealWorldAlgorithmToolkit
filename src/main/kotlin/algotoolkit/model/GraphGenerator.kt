package algotoolkit.model

import kotlin.math.ceil
import kotlin.random.Random

object GraphGenerator {
    /**
     * Random connected graph: a random spanning tree plus [extraEdges] further distinct edges
     * (capped at the number of possible edges). Nodes get random coordinates in [0,100]^2 and every
     * weight is at least the Euclidean distance of its endpoints, so the A* heuristic is valid.
     */
    fun randomConnected(nodeCount: Int, extraEdges: Int, seed: Long = System.nanoTime()): Graph {
        require(nodeCount >= 1) { "A graph needs at least one node" }
        require(extraEdges >= 0) { "The number of extra edges cannot be negative" }
        val rnd = Random(seed)
        val g = Graph()
        repeat(nodeCount) { g.addNode(coord = Coord(rnd.nextDouble() * 100, rnd.nextDouble() * 100)) }
        val ids = g.nodes.map { it.id }

        fun link(a: Int, b: Int) {
            val weight = ceil(g.euclid(a, b) * (1.0 + rnd.nextDouble() * 0.5) * 10) / 10
            g.addEdge(a, b, maxOf(0.1, weight))
        }

        for (i in 1 until nodeCount) link(ids[i], ids[rnd.nextInt(i)])
        val maxExtra = nodeCount.toLong() * (nodeCount - 1) / 2 - (nodeCount - 1)
        val wanted = minOf(extraEdges.toLong(), maxExtra).toInt()
        var added = 0
        while (added < wanted) {
            val a = ids[rnd.nextInt(nodeCount)]
            val b = ids[rnd.nextInt(nodeCount)]
            if (a != b && !g.hasEdge(a, b)) {
                link(a, b)
                added++
            }
        }
        return g
    }
}
