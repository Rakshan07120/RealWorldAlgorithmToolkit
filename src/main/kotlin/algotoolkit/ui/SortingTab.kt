package algotoolkit.ui

import algotoolkit.algorithms.SortAlgorithm
import algotoolkit.algorithms.SortContext
import algotoolkit.algorithms.SortStep
import algotoolkit.algorithms.isSortedAscending
import algotoolkit.benchmark.BenchmarkConfig
import algotoolkit.benchmark.Benchmarker
import algotoolkit.model.formatMs
import javafx.geometry.Insets
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle

class SortingTab(private val state: AppState) : BorderPane() {
    private val bars = BarCanvas()
    private val message = MessageBar()
    private val liveStats = Label()
    private val inputLabel = Label().apply { isWrapText = true }
    private val algoBox = ComboBox<SortAlgorithm>().apply { maxWidth = Double.MAX_VALUE }
    private val explanation = Label().apply {
        isWrapText = true
        styleClass.add("hint")
    }
    private val results = resultsArea()
    private val picker = DataPicker({ state.sortData.get() }, MAX_VISUAL_SIZE, message) { data, description ->
        setInput(data, description)
    }
    private val playback = Playback({ prepare() }, { applyStep(it) }, { resetView() }, { finished() })

    private var input = IntArray(0)
    private var inputDescription = ""
    private var display = IntArray(0)
    private var steps: List<SortStep> = emptyList()
    private var context: SortContext? = null
    private var lastMs = 0.0
    private var compares = 0
    private var swaps = 0
    private var writes = 0
    private var lastSummary: List<Pair<String, String>> = emptyList()

    init {
        algoBox.items.addAll(SortAlgorithm.entries)
        algoBox.value = SortAlgorithm.BUBBLE
        algoBox.valueProperty().addListener { _, _, _ ->
            showExplanation()
            playback.reset()
        }
        showExplanation()
        state.sortData.addListener { _, _, data -> if (data != null) picker.useLoaded() }

        left = sidePanel(
            VBox(
                16.0,
                section("Algorithm", algoBox, explanation),
                picker.node,
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
                wideButton("Export summary (CSV)") { exportSummary(this, "sorting_summary.csv", lastSummary, message) }
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            340.0
        )
        picker.generate()
    }

    private fun legend(): FlowPane {
        fun item(color: javafx.scene.paint.Color, text: String) = HBox(4.0, Rectangle(14.0, 14.0, color), Label(text))
        return FlowPane(
            14.0, 4.0,
            item(BarCanvas.COMPARE, "Comparison"), item(BarCanvas.SWAP, "Swap"),
            item(BarCanvas.WRITE, "Write / move"), item(BarCanvas.DONE, "Sorted")
        )
    }

    private fun showExplanation() {
        val a = algoBox.value
        explanation.text = "${a.description}\n${sortComplexityText(a)}"
    }

    private fun setInput(data: IntArray, description: String) {
        input = data
        inputDescription = description
        inputLabel.text = "${data.size} elements ($description)"
        playback.reset()
        message.info("Input ready: ${data.size} elements ($description).")
    }

    private fun prepare(): Int? {
        if (input.isEmpty()) {
            message.error("There is no input data. Generate or load an array first.")
            return null
        }
        val algo = algoBox.value
        val recorded = ArrayList<SortStep>()
        val work = input.copyOf()
        val ctx = algo.sort(work, recorded)
        if (!work.isSortedAscending()) {
            message.error("Internal error: ${algo.label} produced unsorted output.")
            return null
        }
        context = ctx
        steps = recorded
        lastSummary = emptyList()
        lastMs = Benchmarker.time(BenchmarkConfig(1, 5), { input.copyOf() }) { algo.sort(it) }.medianMs
        compares = 0; swaps = 0; writes = 0
        display = input.copyOf()
        bars.values = display
        bars.colors.clear()
        renderResults(false)
        message.info("Running ${algo.label}...")
        return recorded.size
    }

    private fun applyStep(i: Int) {
        bars.colors.clear()
        when (val step = steps[i]) {
            is SortStep.Compare -> {
                compares++
                bars.colors[step.i] = BarCanvas.COMPARE
                bars.colors[step.j] = BarCanvas.COMPARE
            }
            is SortStep.Swap -> {
                swaps++
                val t = display[step.i]; display[step.i] = display[step.j]; display[step.j] = t
                bars.colors[step.i] = BarCanvas.SWAP
                bars.colors[step.j] = BarCanvas.SWAP
            }
            is SortStep.Write -> {
                writes++
                display[step.index] = step.value
                bars.colors[step.index] = BarCanvas.WRITE
            }
        }
        liveStats.text = "Comparisons: $compares    Swaps: $swaps    Writes: $writes"
        bars.redraw()
    }

    private fun resetView() {
        display = input.copyOf()
        bars.values = display
        bars.colors.clear()
        bars.redraw()
        steps = emptyList()
        context = null
        lastSummary = emptyList()
        liveStats.text = ""
        results.text = ""
    }

    private fun finished() {
        for (i in display.indices) bars.colors[i] = BarCanvas.DONE
        bars.redraw()
        renderResults(true)
        message.success("Sorted ${display.size} elements.")
    }

    private fun renderResults(complete: Boolean) {
        val algo = algoBox.value
        val ctx = context ?: return
        val rows = ArrayList<Pair<String, String>>()
        rows.add("Algorithm" to algo.label)
        rows.add("Input size" to "${input.size} elements ($inputDescription)")
        rows.add("Complexity" to "best ${algo.best}, avg ${algo.average}, worst ${algo.worst}")
        rows.add("Execution time" to "${formatMs(lastMs)} (median of 5 runs after 1 warm-up; excludes animation)")
        if (complete) {
            rows.add("Status" to "SORTED (output verified)")
            rows.add("Comparisons" to ctx.comparisons.toString())
            rows.add("Swaps" to ctx.swaps.toString())
            rows.add("Writes (moves)" to ctx.writes.toString())
            rows.add("Total operations" to (ctx.comparisons + ctx.swaps + ctx.writes).toString())
            rows.add("Steps animated" to steps.size.toString())
            lastSummary = rows
        } else {
            rows.add("Status" to "running...")
        }
        results.text = formatSummary(rows)
    }
}
