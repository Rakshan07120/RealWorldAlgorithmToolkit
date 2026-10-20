package algotoolkit.algorithms

import algotoolkit.model.Graph
import java.util.PriorityQueue

sealed interface GraphStep {
    /** [node] is taken from the frontier and expanded. */
    data class Visit(val node: Int) : GraphStep
    data class Examine(val from: Int, val to: Int) : GraphStep
    /** The best known cost of reaching [node] improved (Dijkstra / A* only). */
    data class Update(val node: Int, val cost: Double) : GraphStep
    /** Last step of every run; [path] is empty when there is no path or no target. */
    data class Finish(val path: List<Int>) : GraphStep
}

enum class ResultKind(val label: String) {
    TRAVERSAL("traversal"),
    SHORTEST_PATH("shortest path")
}

enum class PathStatus { PATH_FOUND, NO_PATH, TRAVERSAL_COMPLETE }

class GraphResult(
    val algorithm: GraphAlgorithm,
    val start: Int,
    val target: Int?,
    val status: PathStatus,
    val visitOrder: List<Int>,
    val path: List<Int>,
    val pathCost: Double?,
    val nodesVisited: Long,
    val edgesExamined: Long,
    val queueOperations: Long,
    val steps: List<GraphStep>,
    val note: String
) {
    /** Node expansions + edge examinations + frontier (queue/stack/heap) insertions and removals. */
    val operations: Long get() = nodesVisited + edgesExamined + queueOperations
}

/** Counters and optional step recording shared by all graph algorithms. */
class GraphContext(private val steps: MutableList<GraphStep>?) {
    var nodesVisited = 0L
        private set
    var edgesExamined = 0L
        private set
    var queueOperations = 0L
        private set
    val visitOrder = ArrayList<Int>()

    fun visit(node: Int) {
        nodesVisited++
        visitOrder.add(node)
        steps?.add(GraphStep.Visit(node))
    }

    fun examine(from: Int, to: Int) {
        edgesExamined++
        steps?.add(GraphStep.Examine(from, to))
    }

    fun update(node: Int, cost: Double) {
        steps?.add(GraphStep.Update(node, cost))
    }

    fun queueOp() {
        queueOperations++
    }

    fun finish(path: List<Int>) {
        steps?.add(GraphStep.Finish(path))
    }
}

enum class GraphAlgorithm(
    val label: String,
    val kind: ResultKind,
    val complexity: String,
    val description: String
) {
    BFS(
        "Breadth-First Search", ResultKind.TRAVERSAL, "O(V + E)",
        "Explores the graph level by level with a FIFO queue. The path to the target has the fewest edges, " +
            "which is not necessarily the smallest total weight. Stops when the target is dequeued."
    ),
    DFS(
        "Depth-First Search", ResultKind.TRAVERSAL, "O(V + E)",
        "Follows each branch as deep as possible before backtracking (iterative, explicit stack). " +
            "The path it finds is neither shortest nor lightest in general. Stops when the target is reached."
    ),
    DIJKSTRA(
        "Dijkstra's Shortest Path", ResultKind.SHORTEST_PATH, "O((V + E) log V)",
        "Always expands the unvisited node with the smallest known distance from the start. Gives a " +
            "minimum-weight path because all weights are positive. Without a target it computes distances to every reachable node."
    ),
    ASTAR(
        "A* Search", ResultKind.SHORTEST_PATH, "O(E log V) worst case",
        "Dijkstra guided by a heuristic. With node coordinates whose straight-line distances never exceed " +
            "the edge weights it uses the Euclidean distance (admissible and consistent), otherwise a zero heuristic."
    );

    override fun toString() = label

    fun run(graph: Graph, start: Int, target: Int?, record: Boolean = false): GraphResult {
        require(graph.contains(start)) { "The start node does not exist in the graph" }
        require(target == null || graph.contains(target)) { "The target node does not exist in the graph" }
        if (this == ASTAR) requireNotNull(target) { "A* needs a target node" }
        val steps: MutableList<GraphStep>? = if (record) ArrayList() else null
        val ctx = GraphContext(steps)
        val outcome = when (this) {
            BFS -> bfs(graph, start, target, ctx)
            DFS -> dfs(graph, start, target, ctx)
            DIJKSTRA -> bestFirst(
                graph, start, target, ctx,
                "All edge weights are positive and finite, so settled distances are final."
            ) { 0.0 }
            ASTAR -> astar(graph, start, checkNotNull(target), ctx)
        }
        val path = if (target != null && outcome.found) reconstruct(outcome.parent, start, target) else emptyList()
        ctx.finish(path)
        val cost = if (path.isEmpty()) null
        else path.zipWithNext().fold(0.0) { acc, (a, b) -> acc + graph.weight(a, b)!! }
        val status = when {
            target == null -> PathStatus.TRAVERSAL_COMPLETE
            outcome.found -> PathStatus.PATH_FOUND
            else -> PathStatus.NO_PATH
        }
        return GraphResult(
            this, start, target, status, ctx.visitOrder, path, cost,
            ctx.nodesVisited, ctx.edgesExamined, ctx.queueOperations, steps ?: emptyList(), outcome.note
        )
    }
}

private class Outcome(val found: Boolean, val parent: Map<Int, Int>, val note: String)

private class QueueEntry(val node: Int, val priority: Double)

private fun reconstruct(parent: Map<Int, Int>, start: Int, target: Int): List<Int> {
    val path = ArrayList<Int>()
    var current = target
    path.add(current)
    while (current != start) {
        current = parent.getValue(current)
        path.add(current)
    }
    path.reverse()
    return path
}

private fun bfs(g: Graph, start: Int, target: Int?, ctx: GraphContext): Outcome {
    val parent = HashMap<Int, Int>()
    val seen = HashSet<Int>()
    val queue = ArrayDeque<Int>()
    seen.add(start)
    queue.addLast(start)
    ctx.queueOp()
    var found = false
    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        ctx.queueOp()
        ctx.visit(u)
        if (u == target) {
            found = true
            break
        }
        for ((v, _) in g.neighbors(u)) {
            ctx.examine(u, v)
            if (seen.add(v)) {
                parent[v] = u
                queue.addLast(v)
                ctx.queueOp()
            }
        }
    }
    return Outcome(found, parent, "Traversal measurement: path has the fewest edges, not necessarily the lowest weight.")
}

private fun dfs(g: Graph, start: Int, target: Int?, ctx: GraphContext): Outcome {
    val parent = HashMap<Int, Int>()
    val visited = HashSet<Int>()
    val stack = ArrayDeque<Pair<Int, Int>>() // (node, node it was discovered from, or -1)
    stack.addLast(start to -1)
    ctx.queueOp()
    var found = false
    while (stack.isNotEmpty()) {
        val (u, from) = stack.removeLast()
        ctx.queueOp()
        if (!visited.add(u)) continue
        if (from >= 0) parent[u] = from
        ctx.visit(u)
        if (u == target) {
            found = true
            break
        }
        for ((v, _) in g.neighbors(u).asReversed()) {
            ctx.examine(u, v)
            if (v !in visited) {
                stack.addLast(v to u)
                ctx.queueOp()
            }
        }
    }
    return Outcome(found, parent, "Traversal measurement: path follows depth-first order and is not optimal.")
}

private fun astar(g: Graph, start: Int, target: Int, ctx: GraphContext): Outcome {
    val useCoordinates = g.hasConsistentCoordinates()
    val note = if (useCoordinates) "Euclidean heuristic (admissible and consistent for these weights)."
    else "Zero heuristic: node coordinates are missing or inconsistent with the edge weights, so A* behaves like Dijkstra."
    return bestFirst(g, start, target, ctx, note) { if (useCoordinates) g.euclid(it, target) else 0.0 }
}

/** Dijkstra (heuristic 0) and A* (consistent heuristic): settled nodes are final, so closed nodes are never reopened. */
private fun bestFirst(
    g: Graph, start: Int, target: Int?, ctx: GraphContext, note: String, heuristic: (Int) -> Double
): Outcome {
    val cost = HashMap<Int, Double>()
    val parent = HashMap<Int, Int>()
    val closed = HashSet<Int>()
    val queue = PriorityQueue<QueueEntry>(compareBy<QueueEntry>({ it.priority }, { it.node }))
    cost[start] = 0.0
    queue.add(QueueEntry(start, heuristic(start)))
    ctx.queueOp()
    ctx.update(start, 0.0)
    var found = false
    while (queue.isNotEmpty()) {
        val u = queue.poll().node
        ctx.queueOp()
        if (!closed.add(u)) continue
        ctx.visit(u)
        if (u == target) {
            found = true
            break
        }
        val du = cost.getValue(u)
        for ((v, w) in g.neighbors(u)) {
            ctx.examine(u, v)
            if (v in closed) continue
            val candidate = du + w
            if (candidate < (cost[v] ?: Double.POSITIVE_INFINITY)) {
                cost[v] = candidate
                parent[v] = u
                queue.add(QueueEntry(v, candidate + heuristic(v)))
                ctx.queueOp()
                ctx.update(v, candidate)
            }
        }
    }
    return Outcome(found, parent, note)
}
