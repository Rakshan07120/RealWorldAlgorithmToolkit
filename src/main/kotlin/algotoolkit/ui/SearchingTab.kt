package algotoolkit.ui

import algotoolkit.algorithms.SearchAlgorithm
import algotoolkit.algorithms.SearchResult
import algotoolkit.algorithms.SearchStep
import algotoolkit.algorithms.Searching
import algotoolkit.benchmark.BenchmarkConfig
import algotoolkit.benchmark.Benchmarker
import algotoolkit.model.formatMs
import javafx.geometry.Insets
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import kotlin.random.Random

class SearchingTab(private val state: AppState) : BorderPane() {
    private val bars = BarCanvas()
    private val message = MessageBar()
    private val liveStats = Label()
    private val inputLabel = Label().apply { isWrapText = true }
    private val algoBox = ComboBox<SearchAlgorithm>().apply { maxWidth = Double.MAX_VALUE }
    private val targetField = TextField().apply { promptText = "integer target" }
    private val explanation = Label().apply {
        isWrapText = true
        styleClass.add("hint")
    }
    private val results = resultsArea()
    private val picker = DataPicker({ state.searchData.get() }, MAX_VISUAL_SIZE, message) { data, description ->
        setInput(data, description)
    }
    private val playback = Playback({ prepare() }, { applyStep(it) }, { resetView() }, { finished() })

    private var input = IntArray(0)
    private var inputDescription = ""
    private var result: SearchResult? = null
    private var lastMs = 0.0
    private var comparesSoFar = 0
    private val examined = HashSet<Int>()
    private var lastSummary: List<Pair<String, String>> = emptyList()

    init {
        algoBox.items.addAll(SearchAlgorithm.entries)
        algoBox.value = SearchAlgorithm.BINARY
        algoBox.valueProperty().addListener { _, _, _ ->
            showExplanation()
            playback.reset()
        }
        targetField.textProperty().addListener { _, _, _ -> playback.reset() }
        showExplanation()
        state.searchData.addListener { _, _, data -> if (data != null) picker.useLoaded() }

        left = sidePanel(
            VBox(
                16.0,
                section("Algorithm", algoBox, explanation),
                picker.node,
                section(
                    "Target value", targetField,
                    wideButton("Pick a random element of the array") { pickRandomTarget() }
                ),
                section("Current input", inputLabel)
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            310.0
        )
        center = VBox(6.0, bars, legend(), playback.node, liveStats, message).apply {
            padding = Insets(10.0)
            VBox.setVgrow(bars, Priority.ALWAYS)
        }
        right = sidePanel(
            VBox(
                8.0, Label("Results").apply { styleClass.add("section-title") }, results,
                wideButton("Export summary (CSV)") { exportSummary(this, "search_summary.csv", lastSummary, message) }
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            340.0
        )
        picker.generate()
        pickRandomTarget()
    }

    private fun legend(): FlowPane {
        fun item(color: javafx.scene.paint.Color, text: String) = HBox(4.0, Rectangle(14.0, 14.0, color), Label(text))
        return FlowPane(
            14.0, 4.0,
            item(BarCanvas.CURRENT, "Examined now"), item(BarCanvas.EXAMINED, "Already examined"),
            item(BarCanvas.DONE, "Target found"), item(javafx.scene.paint.Color.web("#d5dbe1"), "Outside range")
        )
    }

    private fun showExplanation() {
        val a = algoBox.value
        explanation.text = "${a.description}\nBest ${a.best}, average ${a.average}, worst ${a.worst}"
    }

    private fun pickRandomTarget() {
        if (input.isNotEmpty()) targetField.text = input[Random.nextInt(input.size)].toString()
    }

    private fun setInput(data: IntArray, description: String) {
        input = data
        inputDescription = description
        inputLabel.text = "${data.size} elements ($description)"
        playback.reset()
        pickRandomTarget()
        message.info("Input ready: ${data.size} elements ($description).")
    }

    private fun prepare(): Int? {
        if (input.isEmpty()) {
            message.error("There is no input data. Generate or load an array first.")
            return null
        }
        val target = targetField.text.trim().toIntOrNull()
        if (target == null) {
            message.error("Enter an integer target value.")
            return null
        }
        val algo = algoBox.value
        val r = Searching.search(algo, input, target, record = true)
        result = r
        lastSummary = emptyList()
        lastMs = Benchmarker.time(BenchmarkConfig(1, 20), { }) { Searching.search(algo, r.data, target) }.medianMs
        examined.clear()
        comparesSoFar = 0
        bars.values = r.data
        bars.colors.clear()
        bars.dimOutside = null
        renderResults(false)
        message.info(
            if (r.sortedFirst) "Input was not sorted, so a sorted copy (merge sort) is searched. Indices refer to the sorted copy."
            else "Searching for $target with ${algo.label}..."
        )
        return r.steps.size
    }

    private fun applyStep(i: Int) {
        val r = result ?: return
        bars.colors.clear()
        when (val step = r.steps[i]) {
            is SearchStep.Examine -> {
                comparesSoFar++
                examined.add(step.index)
                if (r.algorithm == SearchAlgorithm.BINARY) bars.dimOutside = step.lo..step.hi
                for (e in examined) bars.colors[e] = BarCanvas.EXAMINED
                bars.colors[step.index] = BarCanvas.CURRENT
            }
            is SearchStep.Found -> {
                for (e in examined) bars.colors[e] = BarCanvas.EXAMINED
                bars.colors[step.index] = BarCanvas.DONE
            }
            SearchStep.NotFound -> for (e in examined) bars.colors[e] = BarCanvas.EXAMINED
        }
        liveStats.text = "Comparisons so far: $comparesSoFar"
        bars.redraw()
    }

    private fun resetView() {
        bars.values = input
        bars.colors.clear()
        bars.dimOutside = null
        bars.redraw()
        examined.clear()
        result = null
        lastSummary = emptyList()
        liveStats.text = ""
        results.text = ""
    }

    private fun finished() {
        val r = result ?: return
        renderResults(true)
        if (r.found) message.success("Found ${r.target} at index ${r.index}.")
        else message.error("${r.target} is not in the array (searched ${r.comparisons} elements/steps).")
    }

    private fun renderResults(complete: Boolean) {
        val r = result ?: return
        val rows = ArrayList<Pair<String, String>>()
        rows.add("Algorithm" to r.algorithm.label)
        rows.add("Input size" to "${r.data.size} elements ($inputDescription)")
        rows.add("Target" to r.target.toString())
        rows.add("Sorted first" to if (r.sortedFirst) "yes (explicit sort of a copy)" else "no")
        rows.add("Execution time" to "${formatMs(lastMs)} (single query, median of 20 runs; very small timings are noisy)")
        if (complete) {
            rows.add("Status" to if (r.found) "FOUND" else "NOT FOUND")
            rows.add("Index" to if (r.found) r.index.toString() else "-")
            rows.add("Comparisons" to r.comparisons.toString())
            rows.add("Steps animated" to r.steps.size.toString())
            lastSummary = rows
        } else {
            rows.add("Status" to "running...")
        }
        results.text = formatSummary(rows)
    }
}
