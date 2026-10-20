package algotoolkit.ui

import algotoolkit.algorithms.GraphStep
import algotoolkit.model.Edge
import algotoolkit.model.Graph
import algotoolkit.model.edgeKey
import algotoolkit.model.formatNumber
import javafx.geometry.VPos
import javafx.scene.canvas.Canvas
import javafx.scene.input.MouseButton
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Zoomable, pannable graph view. Node positions come from a force-directed layout and live here, not in the
 * model. Large graphs hide weight and node labels until the user zooms in; the graph data is never touched.
 */
class GraphCanvas : Pane() {
    companion object {
        val START: Color = Color.web("#2e7d32")
        val TARGET: Color = Color.web("#c62828")
        val CURRENT: Color = Color.web("#fb8c00")
        val VISITED: Color = Color.web("#64b5f6")
        val PLAIN: Color = Color.web("#cfd8dc")
        val EXAMINED: Color = Color.web("#ffa726")
        val PATH: Color = Color.web("#2e7d32")
        private val EDGE: Color = Color.web("#b0bec5")
        private val INK: Color = Color.web("#263238")
    }

    var graph = Graph()
        private set
    var startNode: Int? = null
        set(value) {
            field = value
            redraw()
        }
    var targetNode: Int? = null
        set(value) {
            field = value
            redraw()
        }
    var pendingNode: Int? = null
        set(value) {
            field = value
            redraw()
        }

    /** Called for a click without dragging: node hit (or null), edge hit (or null), world x, world y. */
    var onClick: ((Int?, Edge?, Double, Double) -> Unit)? = null

    private val canvas = Canvas()
    private val pos = HashMap<Int, DoubleArray>()
    private var scale = 1.0
    private var tx = 0.0
    private var ty = 0.0
    private var pendingFit = true

    private val visited = HashSet<Int>()
    private val examined = HashSet<Long>()
    private val pathNodes = HashSet<Int>()
    private val pathEdges = HashSet<Long>()
    private val costs = HashMap<Int, Double>()
    private var current: Int? = null
    private var currentEdge: Long? = null

    private var pressX = 0.0
    private var pressY = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var moved = false

    init {
        styleClass.add("canvas-frame")
        setMinSize(0.0, 0.0)
        setPrefSize(640.0, 420.0)
        children.add(canvas)
        canvas.setOnMousePressed { e ->
            pressX = e.x; pressY = e.y; lastX = e.x; lastY = e.y
            moved = false
        }
        canvas.setOnMouseDragged { e ->
            if (hypot(e.x - pressX, e.y - pressY) > 4) moved = true
            if (moved) {
                tx += e.x - lastX
                ty += e.y - lastY
                redraw()
            }
            lastX = e.x
            lastY = e.y
        }
        canvas.setOnMouseReleased { e ->
            if (!moved && e.button == MouseButton.PRIMARY) handleClick(e.x, e.y)
        }
        canvas.setOnScroll { e -> zoomAt(e.x, e.y, if (e.deltaY > 0) 1.15 else 1 / 1.15) }
    }

    override fun layoutChildren() {
        super.layoutChildren()
        if (canvas.width != width || canvas.height != height) {
            canvas.width = width
            canvas.height = height
            if (pendingFit) fitView() else redraw()
        }
    }

    // ---- graph and layout ----

    fun setGraph(g: Graph) {
        graph = g
        startNode = null
        targetNode = null
        pendingNode = null
        clearMarks()
        pos.clear()
        pos.putAll(ForceLayout.compute(g))
        pendingFit = true
        fitView()
    }

    fun relayout() {
        pos.clear()
        pos.putAll(ForceLayout.compute(graph))
        fitView()
    }

    fun place(id: Int, worldX: Double, worldY: Double) {
        pos[id] = doubleArrayOf(worldX, worldY)
    }

    // ---- view control ----

    fun zoomIn() = zoomAt(width / 2, height / 2, 1.25)
    fun zoomOut() = zoomAt(width / 2, height / 2, 0.8)

    private fun zoomAt(sx: Double, sy: Double, factor: Double) {
        val newScale = (scale * factor).coerceIn(0.05, 8.0)
        val ratio = newScale / scale
        tx = sx - (sx - tx) * ratio
        ty = sy - (sy - ty) * ratio
        scale = newScale
        redraw()
    }

    fun fitView() {
        if (width <= 0 || height <= 0) return
        val points = pos.values
        if (points.isEmpty()) {
            scale = 1.0; tx = 0.0; ty = 0.0
            redraw()
            return
        }
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (p in points) {
            minX = min(minX, p[0]); maxX = max(maxX, p[0])
            minY = min(minY, p[1]); maxY = max(maxY, p[1])
        }
        val bw = max(maxX - minX, 1.0)
        val bh = max(maxY - minY, 1.0)
        val margin = 45.0
        scale = min((width - 2 * margin) / bw, (height - 2 * margin) / bh).coerceIn(0.05, 4.0)
        tx = (width - bw * scale) / 2 - minX * scale
        ty = (height - bh * scale) / 2 - minY * scale
        pendingFit = false
        redraw()
    }

    // ---- algorithm marks ----

    fun clearMarks() {
        visited.clear(); examined.clear(); pathNodes.clear(); pathEdges.clear(); costs.clear()
        current = null
        currentEdge = null
        redraw()
    }

    fun applyStep(step: GraphStep) {
        when (step) {
            is GraphStep.Visit -> {
                current = step.node
                visited.add(step.node)
            }
            is GraphStep.Examine -> {
                val key = edgeKey(step.from, step.to)
                examined.add(key)
                currentEdge = key
            }
            is GraphStep.Update -> costs[step.node] = step.cost
            is GraphStep.Finish -> {
                current = null
                currentEdge = null
                pathNodes.clear(); pathEdges.clear()
                pathNodes.addAll(step.path)
                for (i in 0 until step.path.size - 1) pathEdges.add(edgeKey(step.path[i], step.path[i + 1]))
            }
        }
        redraw()
    }

    // ---- drawing ----

    private fun sx(x: Double) = x * scale + tx
    private fun sy(y: Double) = y * scale + ty

    private fun nodeRadius() = when {
        graph.nodeCount <= 60 -> 13.0
        graph.nodeCount <= 200 -> 9.0
        else -> 5.0
    }

    fun redraw() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val gc = canvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, w, h)
        if (graph.nodeCount == 0) {
            gc.fill = Color.GRAY
            gc.textAlign = TextAlignment.CENTER
            gc.textBaseline = VPos.CENTER
            gc.font = Font.font(14.0)
            gc.fillText("Empty graph. Generate a random graph, load a dataset, or use Add Node mode.", w / 2, h / 2)
            return
        }
        val showWeights = graph.edgeCount <= 40 || scale >= 1.6
        val showLabels = graph.nodeCount <= 120 || scale >= 1.2
        val edges = graph.edges

        for (e in edges) {
            val p = pos[e.a] ?: continue
            val q = pos[e.b] ?: continue
            val key = edgeKey(e.a, e.b)
            val (color, lineWidth) = when {
                key in pathEdges -> PATH to 4.0
                key == currentEdge -> Color.web("#e53935") to 3.0
                key in examined -> EXAMINED to 2.0
                else -> EDGE to 1.0
            }
            gc.stroke = color
            gc.lineWidth = lineWidth
            gc.strokeLine(sx(p[0]), sy(p[1]), sx(q[0]), sy(q[1]))
        }

        if (showWeights) {
            gc.font = Font.font(11.0)
            gc.textAlign = TextAlignment.CENTER
            gc.textBaseline = VPos.CENTER
            for (e in edges) {
                val p = pos[e.a] ?: continue
                val q = pos[e.b] ?: continue
                val text = formatNumber(e.weight)
                val mx = (sx(p[0]) + sx(q[0])) / 2
                val my = (sy(p[1]) + sy(q[1])) / 2
                val tw = text.length * 6.4 + 4
                gc.fill = Color(1.0, 1.0, 1.0, 0.85)
                gc.fillRect(mx - tw / 2, my - 8, tw, 16.0)
                gc.fill = Color.web("#455a64")
                gc.fillText(text, mx, my)
            }
        }

        val r = nodeRadius()
        gc.font = Font.font(11.0)
        for (n in graph.nodes) {
            val p = pos[n.id] ?: continue
            val x = sx(p[0])
            val y = sy(p[1])
            if (x < -r || y < -r || x > w + r || y > h + r) continue
            gc.fill = when {
                n.id == current -> CURRENT
                n.id == startNode -> START
                n.id == targetNode -> TARGET
                n.id in visited -> VISITED
                else -> PLAIN
            }
            gc.fillOval(x - r, y - r, 2 * r, 2 * r)
            val onPath = n.id in pathNodes
            gc.stroke = when {
                n.id == pendingNode -> Color.web("#6a1b9a")
                onPath -> PATH
                else -> Color.web("#546e7a")
            }
            gc.lineWidth = if (n.id == pendingNode || onPath) 3.5 else 1.2
            gc.strokeOval(x - r, y - r, 2 * r, 2 * r)
            if (showLabels) {
                val cost = costs[n.id]
                val text = if (cost != null) "${n.label} (${formatNumber(cost)})" else n.label
                gc.fill = INK
                gc.textAlign = TextAlignment.CENTER
                gc.textBaseline = VPos.TOP
                gc.fillText(text, x, y + r + 2)
            }
        }
    }

    // ---- hit testing ----

    private fun handleClick(x: Double, y: Double) {
        val node = nodeAt(x, y)
        val edge = if (node == null) edgeAt(x, y) else null
        onClick?.invoke(node, edge, (x - tx) / scale, (y - ty) / scale)
    }

    private fun nodeAt(x: Double, y: Double): Int? {
        val r = nodeRadius() + 3
        for (n in graph.nodes.asReversed()) {
            val p = pos[n.id] ?: continue
            if (hypot(sx(p[0]) - x, sy(p[1]) - y) <= r) return n.id
        }
        return null
    }

    private fun edgeAt(x: Double, y: Double): Edge? {
        var best: Edge? = null
        var bestDistance = 6.0
        for (e in graph.edges) {
            val p = pos[e.a] ?: continue
            val q = pos[e.b] ?: continue
            val d = distanceToSegment(x, y, sx(p[0]), sy(p[1]), sx(q[0]), sy(q[1]))
            if (d < bestDistance) {
                bestDistance = d
                best = e
            }
        }
        return best
    }

    private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
