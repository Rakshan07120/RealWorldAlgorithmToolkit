package algotoolkit.ui

import algotoolkit.model.Graph
import kotlin.math.sqrt
import kotlin.random.Random

/** Fruchterman-Reingold force-directed layout. Returns world coordinates (x, y) per node id. */
object ForceLayout {
    fun compute(graph: Graph, seed: Long = 7L): Map<Int, DoubleArray> {
        val ids = graph.nodes.map { it.id }
        val n = ids.size
        if (n == 0) return emptyMap()
        val side = maxOf(600.0, sqrt(n.toDouble()) * 110.0)
        if (n == 1) return mapOf(ids[0] to doubleArrayOf(side / 2, side / 2))

        val rnd = Random(seed)
        val index = HashMap<Int, Int>()
        ids.forEachIndexed { i, id -> index[id] = i }
        val x = DoubleArray(n) { rnd.nextDouble() * side }
        val y = DoubleArray(n) { rnd.nextDouble() * side }
        val edges = graph.edges.map { index.getValue(it.a) to index.getValue(it.b) }
        val k = sqrt(side * side / n) * 0.8
        val iterations = when {
            n <= 200 -> 300
            n <= 600 -> 150
            else -> 60
        }
        var temperature = side / 8
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        repeat(iterations) {
            dx.fill(0.0)
            dy.fill(0.0)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var ddx = x[i] - x[j]
                    var ddy = y[i] - y[j]
                    var d2 = ddx * ddx + ddy * ddy
                    if (d2 < 0.01) {
                        ddx = rnd.nextDouble() - 0.5
                        ddy = rnd.nextDouble() - 0.5
                        d2 = maxOf(ddx * ddx + ddy * ddy, 0.01)
                    }
                    val f = k * k / d2
                    dx[i] += ddx * f; dy[i] += ddy * f
                    dx[j] -= ddx * f; dy[j] -= ddy * f
                }
            }
            for ((a, b) in edges) {
                val ddx = x[a] - x[b]
                val ddy = y[a] - y[b]
                val d = maxOf(sqrt(ddx * ddx + ddy * ddy), 0.01)
                val f = d / k
                dx[a] -= ddx * f; dy[a] -= ddy * f
                dx[b] += ddx * f; dy[b] += ddy * f
            }
            for (i in 0 until n) {
                dx[i] -= (x[i] - side / 2) * 0.02
                dy[i] -= (y[i] - side / 2) * 0.02
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (len > 0) {
                    val step = minOf(len, temperature)
                    x[i] += dx[i] / len * step
                    y[i] += dy[i] / len * step
                }
            }
            temperature = maxOf(temperature * 0.97, 0.5)
        }
        return ids.mapIndexed { i, id -> id to doubleArrayOf(x[i], y[i]) }.toMap()
    }
}
