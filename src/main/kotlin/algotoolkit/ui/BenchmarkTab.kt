package algotoolkit.ui

import algotoolkit.algorithms.GraphAlgorithm
import algotoolkit.algorithms.SearchAlgorithm
import algotoolkit.algorithms.SortAlgorithm
import algotoolkit.benchmark.BenchmarkConfig
import algotoolkit.benchmark.BenchmarkRow
import algotoolkit.benchmark.Benchmarker
import algotoolkit.io.CsvExport
import algotoolkit.model.DataPattern
import algotoolkit.model.formatMs
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.chart.BarChart
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

class BenchmarkTab : BorderPane() {
    private val sortingName = "Sorting"
    private val searchingName = "Searching"
    private val graphName = "Graph algorithms"

    private val message = MessageBar()
    private val status = Label()
    private val progress = ProgressBar(0.0).apply {
        maxWidth = Double.MAX_VALUE
        isVisible = false
    }
    private val categoryBox = ComboBox<String>().apply {
        items.addAll(sortingName, searchingName, graphName)
        value = sortingName
        maxWidth = Double.MAX_VALUE
    }
    private val algorithmsBox = VBox(4.0)
    private val sizesField = TextField()
    private val patternBox = ComboBox<DataPattern>().apply {
        items.addAll(DataPattern.entries)
        value = DataPattern.RANDOM
        maxWidth = Double.MAX_VALUE
    }
    private val extraSpinner = intSpinner(0, 20, 2)
    private val warmupSpinner = intSpinner(0, 50, 3)
    private val repsSpinner = intSpinner(1, 200, 10)
    private val metricBox = ComboBox<String>().apply {
        items.addAll("Average", "Median", "Minimum")
        value = "Average"
    }
    private val runButton = Button("Run benchmark")
    private val cancelButton = Button("Cancel").apply { isDisable = true }

    private val lineChart = LineChart<Number, Number>(
        NumberAxis().apply { label = "Input size" },
        NumberAxis().apply { label = "Time (ms)" }
    ).apply {
        animated = false
        title = "Execution time vs input size"
    }
    private val barChart = BarChart<String, Number>(
        CategoryAxis(),
        NumberAxis().apply { label = "Operations" }
    ).apply {
        animated = false
        isLegendVisible = false
    }
    private val table = TableView<BenchmarkRow>()

    private val checks = ArrayList<Pair<CheckBox, Any>>()
    private var rows: List<BenchmarkRow> = emptyList()
    private var currentTask: Task<List<BenchmarkRow>>? = null

    init {
        runButton.setOnAction { run() }
        cancelButton.setOnAction { currentTask?.cancel() }
        categoryBox.valueProperty().addListener { _, _, _ -> categoryChanged() }
        metricBox.valueProperty().addListener { _, _, _ -> if (rows.isNotEmpty()) rebuildCharts() }
        buildTable()
        categoryChanged()

        val method = Label(
            "Method: only the algorithm call is timed (System.nanoTime). Inputs are prepared outside the timed region, " +
                "warm-up runs are discarded, and every algorithm sees identical input. Results are measurements on this " +
                "machine, not a proof of theoretical complexity; very small timings are noisy."
        ).apply {
            isWrapText = true
            styleClass.add("hint")
        }
        left = sidePanel(
            VBox(
                14.0,
                section("Category", categoryBox),
                section("Algorithms", algorithmsBox),
                section("Input sizes (comma separated)", sizesField),
                section("Sorting data pattern", patternBox),
                section("Graph: extra edges per node", extraSpinner),
                section("Warm-up runs", warmupSpinner),
                section("Measured repetitions", repsSpinner),
                HBox(8.0, runButton, cancelButton),
                wideButton("Export results (CSV)") { export() },
                method
            ).apply {
                padding = Insets(12.0)
                styleClass.add("panel")
            },
            310.0
        )
        val chartTabs = TabPane(
            Tab("Time vs input size", VBox(6.0, HBox(8.0, Label("Metric"), metricBox), lineChart).apply {
                padding = Insets(6.0)
                VBox.setVgrow(lineChart, Priority.ALWAYS)
            }),
            Tab("Operations at largest size", barChart)
        )
        chartTabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        val split = SplitPane(chartTabs, table).apply {
            orientation = Orientation.VERTICAL
            setDividerPositions(0.58)
        }
        center = VBox(6.0, split, HBox(8.0, progress, status), message).apply {
            padding = Insets(10.0)
            VBox.setVgrow(split, Priority.ALWAYS)
            HBox.setHgrow(progress, Priority.NEVER)
        }
        message.info("Choose a category, algorithms and input sizes, then press Run benchmark.")
    }

    private fun buildTable() {
        fun col(title: String, width: Double, value: (BenchmarkRow) -> String) =
            TableColumn<BenchmarkRow, String>(title).apply {
                prefWidth = width
                setCellValueFactory { SimpleStringProperty(value(it.value)) }
            }
        table.columns.addAll(
            col("Algorithm", 150.0) { it.algorithm },
            col("Measurement", 130.0) { it.measurement },
            col("Size", 70.0) { it.inputSize.toString() },
            col("Avg", 90.0) { formatMs(it.avgMs) },
            col("Median", 90.0) { formatMs(it.medianMs) },
            col("Min", 90.0) { formatMs(it.minMs) },
            col("Comparisons", 100.0) { it.comparisons?.toString() ?: "-" },
            col("Swaps / moves", 100.0) { it.swapsOrMoves?.toString() ?: "-" },
            col("Nodes visited", 100.0) { it.nodesVisited?.toString() ?: "-" },
            col("Operations", 100.0) { it.operations?.toString() ?: "-" },
            col("Runs", 50.0) { it.repetitions.toString() },
            col("Status", 130.0) { it.status },
            col("Dataset", 230.0) { it.dataset },
            col("Note", 320.0) { it.note }
        )
        table.placeholder = Label("No results yet. Run a benchmark.")
    }

    private fun categoryChanged() {
        algorithmsBox.children.clear()
        checks.clear()
        val entries: List<Pair<String, Any>> = when (categoryBox.value) {
            sortingName -> SortAlgorithm.entries.map { it.label to it }
            searchingName -> SearchAlgorithm.entries.map { it.label to it }
            else -> GraphAlgorithm.entries.map { "${it.label} (${it.kind.label})" to it }
        }
        for ((text, algorithm) in entries) {
            val box = CheckBox(text).apply { isSelected = true }
            checks.add(box to algorithm)
            algorithmsBox.children.add(box)
        }
        sizesField.text = when (categoryBox.value) {
            sortingName -> "100, 500, 1000, 2000, 5000"
            searchingName -> "1000, 10000, 100000, 500000"
            else -> "50, 100, 200, 400, 800"
        }
        patternBox.isDisable = categoryBox.value != sortingName
        extraSpinner.isDisable = categoryBox.value != graphName
    }

    private fun parseSizes(): List<Int>? {
        val (min, max) = when (categoryBox.value) {
            sortingName -> 1 to 100_000
            searchingName -> 1 to 1_000_000
            else -> 2 to 20_000
        }
        val tokens = sizesField.text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            message.error("Enter at least one input size.")
            return null
        }
        val sizes = ArrayList<Int>()
        for (token in tokens) {
            val n = token.toIntOrNull()
            if (n == null || n < min || n > max) {
                message.error("Invalid size '$token': use whole numbers from $min to $max.")
                return null
            }
            sizes.add(n)
        }
        return sizes.distinct().sorted()
    }

    private fun run() {
        val sizes = parseSizes() ?: return
        val chosen = checks.filter { it.first.isSelected }.map { it.second }
        if (chosen.isEmpty()) {
            message.error("Select at least one algorithm.")
            return
        }
        val cfg = BenchmarkConfig(warmupSpinner.commit(), repsSpinner.commit())
        val category = categoryBox.value
        val pattern = patternBox.value
        val extra = extraSpinner.commit()
        setRunning(true)
        message.info("Benchmark running in the background. The interface stays responsive.")

        val task = object : Task<List<BenchmarkRow>>() {
            override fun call(): List<BenchmarkRow> {
                val progressCallback: (String) -> Unit = { text -> Platform.runLater { status.text = text } }
                val cancelled: () -> Boolean = { isCancelled }
                return when (category) {
                    sortingName -> Benchmarker.sorting(
                        chosen.filterIsInstance<SortAlgorithm>(), sizes, pattern, cfg, 42, progressCallback, cancelled
                    )
                    searchingName -> Benchmarker.searching(
                        chosen.filterIsInstance<SearchAlgorithm>(), sizes, cfg, 42, progressCallback, cancelled
                    )
                    else -> Benchmarker.graphs(
                        chosen.filterIsInstance<GraphAlgorithm>(), sizes, extra, cfg, 42, progressCallback, cancelled
                    )
                }
            }
        }
        task.setOnSucceeded {
            rows = task.value
            table.items.setAll(rows)
            rebuildCharts()
            setRunning(false)
            val failed = rows.count { !it.status.startsWith("OK") && it.category != "Graph" }
            if (failed > 0) message.error("Finished, but $failed measurement(s) reported a failed correctness check.")
            else message.success("Benchmark finished: ${rows.size} measurements.")
        }
        task.setOnFailed {
            setRunning(false)
            message.error("Benchmark failed: ${task.exception?.message}")
        }
        task.setOnCancelled {
            setRunning(false)
            message.info("Benchmark cancelled; partial results were discarded.")
        }
        currentTask = task
        Thread(task, "benchmark").apply { isDaemon = true }.start()
    }

    private fun setRunning(running: Boolean) {
        runButton.isDisable = running
        cancelButton.isDisable = !running
        progress.isVisible = running
        progress.progress = if (running) ProgressIndicator.INDETERMINATE_PROGRESS else 0.0
        if (!running) status.text = ""
    }

    private fun seriesName(r: BenchmarkRow) =
        if (r.category == "Graph") "${r.algorithm} (${r.measurement})" else r.algorithm

    private fun rebuildCharts() {
        val metric: (BenchmarkRow) -> Double = when (metricBox.value) {
            "Median" -> BenchmarkRow::medianMs
            "Minimum" -> BenchmarkRow::minMs
            else -> BenchmarkRow::avgMs
        }
        lineChart.yAxis.label = "${metricBox.value} time (ms)"
        lineChart.data.clear()
        for ((name, group) in rows.groupBy { seriesName(it) }) {
            val series = XYChart.Series<Number, Number>()
            series.name = name
            for (r in group.sortedBy { it.inputSize }) series.data.add(XYChart.Data<Number, Number>(r.inputSize, metric(r)))
            lineChart.data.add(series)
        }
        barChart.data.clear()
        val largest = rows.maxOfOrNull { it.inputSize } ?: return
        val series = XYChart.Series<String, Number>()
        series.name = "Operations"
        for (r in rows.filter { it.inputSize == largest }) {
            series.data.add(XYChart.Data<String, Number>(seriesName(r), r.operations ?: 0L))
        }
        barChart.data.add(series)
        barChart.title = "Operations at the largest input size (n = $largest)"
    }

    private fun export() {
        if (rows.isEmpty()) {
            message.error("There are no results to export. Run a benchmark first.")
            return
        }
        saveCsv(this, "benchmark_results.csv", CsvExport.benchmarkCsv(rows), message)
    }
}
