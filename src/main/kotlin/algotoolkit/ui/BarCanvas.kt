package algotoolkit.ui

import javafx.geometry.VPos
import javafx.scene.canvas.Canvas
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import kotlin.math.abs

/** Bar chart of an int array with per-index colour overrides; used by the sorting and searching tabs. */
class BarCanvas : Pane() {
    companion object {
        val COMPARE: Color = Color.web("#fdd835")
        val SWAP: Color = Color.web("#e53935")
        val WRITE: Color = Color.web("#1e88e5")
        val DONE: Color = Color.web("#43a047")
        val EXAMINED: Color = Color.web("#ffcc80")
        val CURRENT: Color = Color.web("#fb8c00")
        private val DEFAULT: Color = Color.web("#6b93c4")
        private val DIM: Color = Color.web("#d5dbe1")
        private val TEXT: Color = Color.web("#37474f")
    }

    private val canvas = Canvas()

    var values: IntArray = IntArray(0)
    val colors = HashMap<Int, Color>()

    /** Indices outside this range are drawn dimmed (binary search shows the shrinking range). */
    var dimOutside: IntRange? = null

    init {
        styleClass.add("canvas-frame")
        setMinSize(0.0, 0.0)
        setPrefSize(640.0, 320.0)
        children.add(canvas)
    }

    override fun layoutChildren() {
        super.layoutChildren()
        if (canvas.width != width || canvas.height != height) {
            canvas.width = width
            canvas.height = height
        }
        redraw()
    }

    fun redraw() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val gc = canvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, w, h)
        val n = values.size
        if (n == 0) {
            gc.fill = TEXT
            gc.textAlign = TextAlignment.CENTER
            gc.textBaseline = VPos.CENTER
            gc.font = Font.font(14.0)
            gc.fillText("No data. Generate or load an array.", w / 2, h / 2)
            return
        }
        val padTop = 18.0
        val padBottom = 20.0
        val padX = 10.0
        val plotH = h - padTop - padBottom
        val plotW = w - 2 * padX
        val lo = minOf(0, values.min())
        val hi = maxOf(0, values.max())
        val span = maxOf(hi - lo, 1).toDouble()
        fun yOf(v: Int) = padTop + plotH * (1.0 - (v - lo) / span)
        val zero = yOf(0)
        val barW = plotW / n
        val gap = if (barW > 5) 1.5 else 0.0
        val showValues = barW >= 24
        val dim = dimOutside
        gc.font = Font.font(11.0)
        gc.textAlign = TextAlignment.CENTER
        for (i in 0 until n) {
            val x = padX + i * barW
            val top = yOf(values[i])
            gc.fill = colors[i] ?: if (dim != null && i !in dim) DIM else DEFAULT
            gc.fillRect(x + gap / 2, minOf(top, zero), maxOf(barW - gap, 1.0), maxOf(abs(top - zero), 1.0))
            if (showValues) {
                gc.fill = TEXT
                gc.textBaseline = VPos.BOTTOM
                gc.fillText(values[i].toString(), x + barW / 2, minOf(top, zero) - 2)
                gc.fill = Color.GRAY
                gc.textBaseline = VPos.TOP
                gc.fillText(i.toString(), x + barW / 2, h - padBottom + 3)
            }
        }
    }
}
