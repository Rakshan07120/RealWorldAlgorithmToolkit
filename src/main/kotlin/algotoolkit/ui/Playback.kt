package algotoolkit.ui

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.FlowPane
import javafx.util.Duration
import java.util.Locale
import kotlin.math.pow

/**
 * Start / Pause / Resume / Step / Reset controls plus a speed slider. The algorithm has already run to
 * completion in [prepare] (which returns the number of recorded steps, or null on invalid input); this class
 * only replays the recorded steps on the JavaFX thread via a Timeline, so the UI never blocks.
 */
class Playback(
    private val prepare: () -> Int?,
    private val applyStep: (Int) -> Unit,
    private val onReset: () -> Unit,
    private val onFinished: () -> Unit
) {
    enum class State { IDLE, PLAYING, PAUSED, DONE }

    private val startButton = button("Start") { start() }
    private val pauseButton = button("Pause") { pause() }
    private val resumeButton = button("Resume") { resume() }
    private val stepButton = button("Step") { step() }
    private val resetButton = button("Reset") { reset() }
    private val speed = Slider(0.0, 100.0, 45.0).apply { prefWidth = 130.0 }
    private val speedLabel = Label()
    private val stepLabel = Label("Step 0 / 0")

    val node = FlowPane(
        8.0, 6.0, startButton, pauseButton, resumeButton, stepButton, resetButton,
        Label("Speed"), speed, speedLabel, stepLabel
    )

    private var total = 0
    private var index = 0
    private var accumulator = 0.0

    var state = State.IDLE
        private set(value) {
            field = value
            updateButtons()
        }

    private val timeline = Timeline(KeyFrame(Duration.millis(30.0), EventHandler<ActionEvent> { tick() })).apply {
        cycleCount = Animation.INDEFINITE
    }

    private val stepsPerSecond: Double get() = 10.0.pow(speed.value / 100.0 * 3.0)

    init {
        speed.valueProperty().addListener { _, _, _ -> speedLabel.text = speedText() }
        speedLabel.text = speedText()
        updateButtons()
    }

    fun start() {
        timeline.stop()
        onReset()
        begin(play = true)
    }

    fun pause() {
        if (state == State.PLAYING) {
            timeline.pause()
            state = State.PAUSED
        }
    }

    fun resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING
            timeline.play()
        }
    }

    fun step() {
        when (state) {
            State.IDLE -> {
                onReset()
                if (begin(play = false)) advance()
            }
            State.PLAYING -> {
                pause()
                advance()
            }
            State.PAUSED -> advance()
            State.DONE -> {}
        }
    }

    fun reset() {
        timeline.stop()
        state = State.IDLE
        total = 0
        index = 0
        refreshLabel()
        onReset()
    }

    private fun begin(play: Boolean): Boolean {
        val steps = prepare() ?: return false
        total = steps
        index = 0
        accumulator = 0.0
        refreshLabel()
        if (steps == 0) {
            state = State.DONE
            onFinished()
            return true
        }
        if (play) {
            state = State.PLAYING
            timeline.play()
        } else {
            state = State.PAUSED
        }
        return true
    }

    private fun tick() {
        accumulator += stepsPerSecond * 0.03
        var n = accumulator.toInt()
        accumulator -= n
        while (n-- > 0 && state == State.PLAYING) advance()
    }

    private fun advance() {
        if (index >= total) return
        applyStep(index)
        index++
        refreshLabel()
        if (index >= total) {
            timeline.stop()
            state = State.DONE
            onFinished()
        }
    }

    private fun refreshLabel() {
        stepLabel.text = "Step $index / $total"
    }

    private fun speedText() = String.format(Locale.ROOT, "%.0f steps/s", stepsPerSecond)

    private fun updateButtons() {
        pauseButton.isDisable = state != State.PLAYING
        resumeButton.isDisable = state != State.PAUSED
        stepButton.isDisable = state == State.DONE
    }
}
