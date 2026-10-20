package algotoolkit.ui

import algotoolkit.io.DatasetException
import algotoolkit.io.DatasetIO
import algotoolkit.io.DatasetPreview
import algotoolkit.model.Graph
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

class DatasetTab(private val state: AppState) : BorderPane() {
    private val graphType = "Graph (edges)"
    private val arrayType = "Integer array (sorting / searching)"

    private val message = MessageBar()
    private val typeBox = ComboBox<String>().apply {
        items.addAll(graphType, arrayType)
        value = graphType
        maxWidth = Double.MAX_VALUE
    }
    private val fileLabel = Label("No file loaded").apply { isWrapText = true }
    private val preview = TextArea().apply {
        isEditable = false
        style = "-fx-font-family: 'Monospaced'; -fx-font-size: 12px;"
    }
    private val problems = TextArea().apply {
        isEditable = false
        prefRowCount = 6
        style = "-fx-font-family: 'Monospaced'; -fx-font-size: 12px; -fx-text-fill: #c62828;"
    }
    private val useGraph = Button("Use for Graph Algorithms")
    private val useSort = Button("Use for Sorting")
    private val useSearch = Button("Use for Searching")

    private var loadedGraph: Graph? = null
    private var loadedArray: IntArray? = null

    init {
        useGraph.setOnAction {
            loadedGraph?.let {
                state.graph.set(it.copy())
                message.success("Graph sent to the Graph Algorithms tab.")
            }
        }
        useSort.setOnAction {
            loadedArray?.let {
                state.sortData.set(it.copyOf())
                message.success("Array sent to the Sorting Algorithms tab.")
            }
        }
        useSearch.setOnAction {
            loadedArray?.let {
                state.searchData.set(it.copyOf())
                message.success("Array sent to the Searching Algorithms tab.")
            }
        }
        for (b in listOf(useGraph, useSort, useSearch)) b.maxWidth = Double.MAX_VALUE
        typeBox.valueProperty().addListener { _, _, _ -> clearLoaded() }
        refreshButtons()

        left = sidePanel(
            VBox(
                16.0,
                section("Dataset type", typeBox),
                wideButton("Choose file (CSV / JSON)...") { choose() },
                fileLabel,
                section("Use the loaded dataset", useGraph, useSort, useSearch)
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            310.0
        )
        center = VBox(
            8.0,
            Label("Preview").apply { styleClass.add("section-title") }, preview,
            Label("Validation problems").apply { styleClass.add("section-title") }, problems,
            message
        ).apply {
            padding = Insets(10.0)
            VBox.setVgrow(preview, Priority.ALWAYS)
        }
        right = sidePanel(
            VBox(
                8.0, Label("Supported formats").apply { styleClass.add("section-title") },
                Label(FORMAT_HELP).apply { isWrapText = true }
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            340.0
        )
        message.info("Choose a dataset type, then a file. Samples are in the sample-data folder.")
    }

    private fun clearLoaded() {
        loadedGraph = null
        loadedArray = null
        preview.text = ""
        problems.text = ""
        fileLabel.text = "No file loaded"
        refreshButtons()
    }

    private fun refreshButtons() {
        useGraph.isDisable = loadedGraph == null
        useSort.isDisable = loadedArray == null
        useSearch.isDisable = loadedArray == null
    }

    private fun choose() {
        val path = chooseFile(scene?.window, "Choose dataset") ?: return
        clearLoaded()
        fileLabel.text = path.toString()
        try {
            if (typeBox.value == graphType) {
                val g = DatasetIO.loadGraph(path)
                loadedGraph = g
                preview.text = DatasetPreview.graph(g)
                message.success("Valid graph: ${g.nodeCount} nodes, ${g.edgeCount} edges.")
            } else {
                val a = DatasetIO.loadArray(path)
                loadedArray = a
                preview.text = DatasetPreview.array(a)
                message.success("Valid array with ${a.size} elements.")
            }
        } catch (e: DatasetException) {
            problems.text = e.problems.joinToString("\n")
            message.error("Validation failed with ${e.problems.size} problem(s). Nothing was imported.")
        }
        refreshButtons()
    }

    private companion object {
        val FORMAT_HELP = """
Graph CSV (undirected, header required):
  source,target,weight
  A,B,4
  A,C,2

Graph JSON:
  {
    "directed": false,
    "nodes": [{"id": "A", "x": 0, "y": 0}],
    "edges": [{"source": "A", "target": "B", "weight": 4}]
  }
  'nodes' and the x/y coordinates are optional. If every weight is at least the straight-line distance of its endpoints, A* uses the coordinates as a heuristic.

Array CSV:
  value
  42
  17
  (header optional; one integer per line or comma separated)

Array JSON:
  [42, 17, 93]  or  {"values": [42, 17, 93]}

Rules: weights must be positive finite numbers, no self-loops, no duplicate edges, arrays contain 32-bit integers. Every invalid record is reported with its line; nothing is silently skipped.
        """.trimIndent()
    }
}
