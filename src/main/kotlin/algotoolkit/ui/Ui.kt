package algotoolkit.ui

import algotoolkit.algorithms.SortAlgorithm
import algotoolkit.io.CsvExport
import algotoolkit.model.DataGenerator
import algotoolkit.model.DataPattern
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import javafx.scene.control.TextArea
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Largest array the step-by-step visualizations accept; benchmarking handles bigger inputs. */
const val MAX_VISUAL_SIZE = 300

val DATA_FILTERS = listOf(
    FileChooser.ExtensionFilter("CSV or JSON", "*.csv", "*.json"),
    FileChooser.ExtensionFilter("All files", "*.*")
)

fun section(title: String, vararg content: Node): VBox =
    VBox(6.0, Label(title).apply { styleClass.add("section-title") }, *content)

fun button(text: String, onClick: () -> Unit): Button = Button(text).apply { setOnAction { onClick() } }

fun wideButton(text: String, onClick: () -> Unit): Button =
    button(text, onClick).apply { maxWidth = Double.MAX_VALUE }

fun sidePanel(content: Region, width: Double): ScrollPane = ScrollPane(content).apply {
    isFitToWidth = true
    hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
    prefWidth = width
    minWidth = width
}

fun resultsArea(): TextArea = TextArea().apply {
    isEditable = false
    isWrapText = true
    prefRowCount = 20
    style = "-fx-font-family: 'Monospaced'; -fx-font-size: 12px;"
}

fun formatSummary(rows: List<Pair<String, String>>): String =
    rows.joinToString("\n") { it.first.padEnd(17) + it.second }

fun intSpinner(min: Int, max: Int, initial: Int): Spinner<Int> =
    Spinner<Int>(min, max, initial).apply {
        isEditable = true
        prefWidth = 110.0
    }

/** Reads what the user typed (even without pressing Enter), clamps it to the allowed range and returns it. */
fun Spinner<Int>.commit(): Int {
    val factory = valueFactory as SpinnerValueFactory.IntegerSpinnerValueFactory
    val typed = editor.text.trim().toIntOrNull()
    val value = (typed ?: factory.value).coerceIn(factory.min, factory.max)
    factory.value = value
    return value
}

fun chooseFile(
    owner: Window?, title: String, save: Boolean = false,
    filters: List<FileChooser.ExtensionFilter> = DATA_FILTERS, initialName: String? = null
): Path? {
    val chooser = FileChooser()
    chooser.title = title
    chooser.extensionFilters.addAll(filters)
    if (!save) File("sample-data").takeIf { it.isDirectory }?.let { chooser.initialDirectory = it }
    if (initialName != null) chooser.initialFileName = initialName
    val file = if (save) chooser.showSaveDialog(owner) else chooser.showOpenDialog(owner)
    return file?.toPath()
}

fun saveCsv(owner: Node, suggestedName: String, content: String, message: MessageBar) {
    val path = chooseFile(
        owner.scene?.window, "Export CSV", save = true,
        filters = listOf(FileChooser.ExtensionFilter("CSV files", "*.csv")), initialName = suggestedName
    ) ?: return
    try {
        Files.writeString(path, content)
        message.success("Exported to $path")
    } catch (e: IOException) {
        message.error("Could not write the file: ${e.message}")
    }
}

fun exportSummary(owner: Node, name: String, summary: List<Pair<String, String>>, message: MessageBar) {
    if (summary.isEmpty()) {
        message.error("Run the algorithm to completion first; there is nothing to export yet.")
        return
    }
    saveCsv(owner, name, CsvExport.summaryCsv(summary), message)
}

/** One-line status area: neutral, success or error colouring. */
class MessageBar : Label() {
    init {
        isWrapText = true
        maxWidth = Double.MAX_VALUE
        minHeight = Region.USE_PREF_SIZE
    }

    fun info(text: String) = show(text, "#37474f")
    fun success(text: String) = show(text, "#2e7d32")
    fun error(text: String) = show(text, "#c62828")
    fun clear() {
        this.text = ""
    }

    private fun show(text: String, color: String) {
        this.text = text
        style = "-fx-text-fill: $color; -fx-font-weight: bold;"
    }
}

/**
 * Input-data chooser shared by the sorting and searching tabs: a generated pattern or the dataset loaded
 * in the Dataset Management tab. Reports the chosen array through [onData].
 */
class DataPicker(
    private val loaded: () -> IntArray?,
    private val maxSize: Int,
    private val message: MessageBar,
    private val onData: (IntArray, String) -> Unit
) {
    private val patterns = DataPattern.entries
    private val loadedLabel = "Loaded dataset"
    private val sourceBox = ComboBox<String>().apply {
        items.addAll(patterns.map { it.label } + loadedLabel)
        value = patterns[0].label
        maxWidth = Double.MAX_VALUE
    }
    private val sizeSpinner = intSpinner(2, maxSize, 40)

    val node: VBox = section(
        "Input data",
        sourceBox,
        HBox(8.0, Label("Size"), sizeSpinner),
        wideButton("Generate / apply") { generate() }
    )

    fun useLoaded() {
        sourceBox.value = loadedLabel
        generate()
    }

    fun generate() {
        val index = sourceBox.selectionModel.selectedIndex
        if (index >= patterns.size) {
            val data = loaded()
            if (data == null) {
                message.error("No dataset loaded yet. Load an array in the Dataset Management tab.")
                return
            }
            if (data.size > maxSize) {
                message.error(
                    "The dataset has ${data.size} elements; the visualization supports at most $maxSize. " +
                        "Use Performance Benchmarking for larger inputs."
                )
                return
            }
            onData(data.copyOf(), loadedLabel.lowercase())
        } else {
            val size = sizeSpinner.commit()
            onData(DataGenerator.generate(patterns[index], size, System.nanoTime()), patterns[index].label.lowercase())
        }
    }
}

fun sortComplexityText(a: SortAlgorithm): String =
    "Best ${a.best}, average ${a.average}, worst ${a.worst}\nExtra space ${a.space}, ${if (a.stable) "stable" else "not stable"}"
