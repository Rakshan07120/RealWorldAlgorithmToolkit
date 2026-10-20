package algotoolkit.ui

import algotoolkit.algorithms.GraphAlgorithm
import algotoolkit.algorithms.GraphResult
import algotoolkit.algorithms.GraphStep
import algotoolkit.algorithms.PathStatus
import algotoolkit.benchmark.BenchmarkConfig
import algotoolkit.benchmark.Benchmarker
import algotoolkit.io.DatasetException
import algotoolkit.io.DatasetIO
import algotoolkit.model.Edge
import algotoolkit.model.Graph
import algotoolkit.model.GraphGenerator
import algotoolkit.model.formatMs
import algotoolkit.model.formatNumber
import javafx.geometry.Insets
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle

class GraphTab(private val state: AppState) : BorderPane() {

    private enum class Mode(val label: String, val hint: String) {
        PAN("Pan / Select", "Drag to pan, scroll to zoom."),
        ADD_NODE("Add Node", "Click empty space to add a node."),
        ADD_EDGE("Add Edge", "Click two nodes to connect them, then enter a positive weight."),
        REMOVE("Remove", "Click a node or an edge to delete it."),
        SET_START("Set Start", "Click a node to make it the start node."),
        SET_TARGET("Set Target", "Click a node to make it the target node.")
    }

    private val canvas = GraphCanvas()
    private val message = MessageBar()
    private val liveStats = Label()
    private val algoBox = ComboBox<GraphAlgorithm>().apply { maxWidth = Double.MAX_VALUE }
    private val explanation = Label().apply {
        isWrapText = true
        styleClass.add("hint")
    }
    private val nodesSpinner = intSpinner(2, 2000, 12)
    private val extraSpinner = intSpinner(0, 20000, 8)
    private val noTarget = CheckBox("No target (traverse whole component)")
    private val graphInfo = Label().apply { isWrapText = true }
    private val modeHint = Label().apply {
        isWrapText = true
        styleClass.add("hint")
    }
    private val results = resultsArea()
    private val playback = Playback({ prepare() }, { applyStep(it) }, { resetView() }, { finished() })

    private var mode = Mode.PAN
    private var pendingEdgeSource: Int? = null
    private var lastResult: GraphResult? = null
    private var lastMs = 0.0
    private var liveVisited = 0
    private var liveExamined = 0
    private var lastSummary: List<Pair<String, String>> = emptyList()

    init {
        algoBox.items.addAll(GraphAlgorithm.entries)
        algoBox.value = GraphAlgorithm.DIJKSTRA
        algoBox.valueProperty().addListener { _, _, _ ->
            showExplanation()
            playback.reset()
        }
        showExplanation()
        modeHint.text = mode.hint
        canvas.onClick = { node, edge, wx, wy -> onCanvasClick(node, edge, wx, wy) }
        state.graph.addListener { _, _, g -> loadGraph(g) }

        left = sidePanel(controls(), 310.0)
        center = VBox(
            6.0, canvas, legend(), playback.node, liveStats, message
        ).apply {
            padding = Insets(10.0)
            VBox.setVgrow(canvas, Priority.ALWAYS)
        }
        right = sidePanel(
            VBox(
                8.0, Label("Results").apply { styleClass.add("section-title") }, results,
                wideButton("Export summary (CSV)") { exportSummary(this, "graph_summary.csv", lastSummary, message) }
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            340.0
        )
        generate()
    }

    // ---- layout ----

    private fun controls(): VBox {
        val group = ToggleGroup()
        val modeButtons = FlowPane(6.0, 6.0)
        for (m in Mode.entries) {
            modeButtons.children.add(ToggleButton(m.label).apply {
                toggleGroup = group
                userData = m
                isSelected = m == Mode.PAN
            })
        }
        group.selectedToggleProperty().addListener { _, previous, selected ->
            if (selected == null) {
                previous?.isSelected = true
            } else {
                mode = selected.userData as Mode
                pendingEdgeSource = null
                canvas.pendingNode = null
                modeHint.text = mode.hint
            }
        }
        return VBox(
            16.0,
            section("Algorithm", algoBox, explanation),
            section(
                "Graph",
                HBox(8.0, Label("Nodes"), nodesSpinner),
                HBox(8.0, Label("Extra edges"), extraSpinner),
                wideButton("Generate random connected graph") { generate() },
                wideButton("Load graph from CSV / JSON...") { loadFromFile() },
                HBox(8.0, button("Re-layout") { canvas.relayout() }, button("Clear graph") { clearGraph() }),
                graphInfo
            ),
            section("Edit mode", modeButtons, modeHint),
            section("Target", noTarget),
            section(
                "View",
                HBox(
                    8.0, button("Zoom In") { canvas.zoomIn() }, button("Zoom Out") { canvas.zoomOut() },
                    button("Fit View") { canvas.fitView() }
                )
            )
        ).apply {
            padding = Insets(12.0)
            styleClass.add("panel")
        }
    }

    private fun legend(): FlowPane {
        fun dot(color: Color, text: String) = HBox(4.0, Circle(6.0, color), Label(text))
        fun line(color: Color, text: String) = HBox(4.0, Rectangle(16.0, 4.0, color), Label(text))
        return FlowPane(
            14.0, 4.0,
            dot(GraphCanvas.START, "Start"), dot(GraphCanvas.TARGET, "Target"), dot(GraphCanvas.CURRENT, "Current"),
            dot(GraphCanvas.VISITED, "Visited"), dot(GraphCanvas.PLAIN, "Unvisited"),
            line(GraphCanvas.EXAMINED, "Examined edge"), line(GraphCanvas.PATH, "Final path")
        )
    }

    private fun showExplanation() {
        val a = algoBox.value
        explanation.text = "${a.description}\nTime complexity: ${a.complexity}\nMeasurement type: ${a.kind.label}"
    }

    // ---- graph handling ----

    private fun generate() {
        val n = nodesSpinner.commit()
        val extra = extraSpinner.commit()
        state.graph.set(GraphGenerator.randomConnected(n, extra))
        val g = state.graph.get()
        val capped = if (g.edgeCount < n - 1 + extra) " (extra edges capped at the maximum possible)" else ""
        message.success("Generated a connected graph with ${g.nodeCount} nodes and ${g.edgeCount} edges$capped.")
    }

    private fun loadFromFile() {
        val path = chooseFile(scene?.window, "Load graph (CSV or JSON)") ?: return
        try {
            val g = DatasetIO.loadGraph(path)
            state.graph.set(g)
            message.success("Loaded ${path.fileName}: ${g.nodeCount} nodes, ${g.edgeCount} edges.")
        } catch (e: DatasetException) {
            message.error("Could not load ${path.fileName}:\n" + e.problems.joinToString("\n"))
        }
    }

    private fun clearGraph() {
        state.graph.set(Graph())
        message.info("Graph cleared.")
    }

    private fun loadGraph(g: Graph) {
        pendingEdgeSource = null
        canvas.setGraph(g)
        canvas.startNode = g.nodes.firstOrNull()?.id
        canvas.targetNode = g.nodes.lastOrNull()?.id
        playback.reset()
        refreshInfo()
        message.info("Graph ready. Start = first node, target = last node (change them in Set Start / Set Target mode).")
    }

    private fun refreshInfo() {
        val g = canvas.graph
        val components = g.componentCount()
        val connectivity = when {
            g.nodeCount == 0 -> "Empty graph"
            components <= 1 -> "Connected"
            else -> "Disconnected ($components components)"
        }
        graphInfo.text = "${g.nodeCount} nodes, ${g.edgeCount} edges\n$connectivity\nUndirected, positive finite weights"
    }

    private fun graphEdited(text: String) {
        playback.reset()
        refreshInfo()
        canvas.redraw()
        message.info(text)
    }

    private fun onCanvasClick(node: Int?, edge: Edge?, wx: Double, wy: Double) {
        val g = canvas.graph
        when (mode) {
            Mode.PAN -> {}
            Mode.ADD_NODE -> if (node == null) {
                val added = g.addNode()
                canvas.place(added.id, wx, wy)
                graphEdited("Added node ${added.label}.")
            }
            Mode.ADD_EDGE -> addEdgeClick(node)
            Mode.REMOVE -> {
                if (node != null) {
                    val label = g.labelOf(node)
                    g.removeNode(node)
                    if (canvas.startNode == node) canvas.startNode = null
                    if (canvas.targetNode == node) canvas.targetNode = null
                    graphEdited("Removed node $label and its edges.")
                } else if (edge != null) {
                    g.removeEdge(edge.a, edge.b)
                    graphEdited("Removed edge ${g.labelOf(edge.a)} - ${g.labelOf(edge.b)}.")
                }
            }
            Mode.SET_START -> if (node != null) {
                canvas.startNode = node
                playback.reset()
                message.info("Start node: ${g.labelOf(node)}.")
            }
            Mode.SET_TARGET -> if (node != null) {
                canvas.targetNode = node
                playback.reset()
                message.info("Target node: ${g.labelOf(node)}.")
            }
        }
    }

    private fun addEdgeClick(node: Int?) {
        if (node == null) {
            pendingEdgeSource = null
            canvas.pendingNode = null
            message.info("Edge creation cancelled.")
            return
        }
        val source = pendingEdgeSource
        if (source == null) {
            pendingEdgeSource = node
            canvas.pendingNode = node
            message.info("Now click the second node.")
            return
        }
        pendingEdgeSource = null
        canvas.pendingNode = null
        if (source == node) {
            message.error("Self-loops are not supported.")
            return
        }
        val g = canvas.graph
        val dialog = TextInputDialog("1").apply {
            title = "Add edge"
            headerText = "Weight of edge ${g.labelOf(source)} - ${g.labelOf(node)}"
            contentText = "Positive number:"
        }
        val answer = dialog.showAndWait()
        if (!answer.isPresent) {
            message.info("Edge creation cancelled.")
            return
        }
        val weight = answer.get().trim().toDoubleOrNull()
        if (weight == null) {
            message.error("'${answer.get().trim()}' is not a valid number.")
            return
        }
        try {
            g.addEdge(source, node, weight)
            graphEdited("Added edge ${g.labelOf(source)} - ${g.labelOf(node)} (weight ${formatNumber(weight)}).")
        } catch (e: IllegalArgumentException) {
            message.error(e.message ?: "Invalid edge.")
        }
    }

    // ---- playback callbacks ----

    private fun prepare(): Int? {
        val g = canvas.graph
        if (g.nodeCount == 0) {
            message.error("The graph is empty.")
            return null
        }
        val start = canvas.startNode
        if (start == null || !g.contains(start)) {
            message.error("Select a start node (Set Start mode).")
            return null
        }
        val target: Int? = if (noTarget.isSelected) null else canvas.targetNode
        if (!noTarget.isSelected && (target == null || !g.contains(target))) {
            message.error("Select a target node (Set Target mode) or tick 'No target'.")
            return null
        }
        val algo = algoBox.value
        val result = try {
            algo.run(g, start, target, record = true)
        } catch (e: IllegalArgumentException) {
            message.error(e.message ?: "The algorithm could not run.")
            return null
        }
        lastResult = result
        lastSummary = emptyList()
        lastMs = Benchmarker.time(BenchmarkConfig(1, 5), { }) { algo.run(g, start, target, false) }.medianMs
        liveVisited = 0
        liveExamined = 0
        renderResults(false)
        message.info("Running ${algo.label}...")
        return result.steps.size
    }

    private fun applyStep(i: Int) {
        val r = lastResult ?: return
        val step = r.steps[i]
        canvas.applyStep(step)
        if (step is GraphStep.Visit) liveVisited++
        if (step is GraphStep.Examine) liveExamined++
        liveStats.text = "Visited so far: $liveVisited    Edges examined so far: $liveExamined"
    }

    private fun resetView() {
        canvas.clearMarks()
        lastResult = null
        lastSummary = emptyList()
        liveVisited = 0
        liveExamined = 0
        liveStats.text = ""
        results.text = ""
    }

    private fun finished() {
        val r = lastResult ?: return
        renderResults(true)
        when (r.status) {
            PathStatus.PATH_FOUND ->
                message.success("Path found: cost ${formatNumber(r.pathCost ?: 0.0)}, ${r.path.size} nodes.")
            PathStatus.NO_PATH ->
                message.error("No path exists: the target is not reachable from the start node.")
            PathStatus.TRAVERSAL_COMPLETE ->
                message.success("Traversal complete: ${r.nodesVisited} nodes visited.")
        }
    }

    private fun renderResults(complete: Boolean) {
        val r = lastResult ?: return
        val g = canvas.graph
        fun name(id: Int?) = if (id == null) "-" else g.labelOf(id)
        val rows = ArrayList<Pair<String, String>>()
        rows.add("Algorithm" to r.algorithm.label)
        rows.add("Measurement" to r.algorithm.kind.label)
        rows.add("Complexity" to r.algorithm.complexity)
        rows.add("Input" to "${g.nodeCount} nodes, ${g.edgeCount} edges (undirected, weighted)")
        rows.add("Start" to name(r.start))
        rows.add("Target" to name(r.target))
        rows.add("Execution time" to "${formatMs(lastMs)} (median of 5 runs after 1 warm-up; excludes animation)")
        if (complete) {
            rows.add(
                "Status" to when (r.status) {
                    PathStatus.PATH_FOUND -> "PATH FOUND"
                    PathStatus.NO_PATH -> "NO PATH (target unreachable)"
                    PathStatus.TRAVERSAL_COMPLETE -> "TRAVERSAL COMPLETE"
                }
            )
            rows.add("Path" to if (r.path.isEmpty()) "-" else r.path.joinToString(" \u2192 ") { g.labelOf(it) })
            rows.add("Path cost" to (r.pathCost?.let { formatNumber(it) } ?: "-"))
            rows.add("Nodes visited" to r.nodesVisited.toString())
            rows.add("Edges examined" to r.edgesExamined.toString())
            rows.add("Queue operations" to r.queueOperations.toString())
            rows.add("Total operations" to r.operations.toString())
            rows.add("Notes" to r.note)
            lastSummary = rows
        } else {
            rows.add("Status" to "running...")
        }
        results.text = formatSummary(rows)
    }
}
