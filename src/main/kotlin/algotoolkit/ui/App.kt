package algotoolkit.ui

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.stage.Stage

class App : Application() {
    override fun start(stage: Stage) {
        val state = AppState()
        val tabs = TabPane()
        tabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        tabs.tabs.addAll(
            Tab("Graph Algorithms", GraphTab(state)),
            Tab("Sorting Algorithms", SortingTab(state)),
            Tab("Searching Algorithms", SearchingTab(state)),
            Tab("Performance Benchmarking", BenchmarkTab()),
            Tab("Dataset Management", DatasetTab(state))
        )
        val scene = Scene(tabs, 1320.0, 820.0)
        App::class.java.getResource("/style.css")?.let { scene.stylesheets.add(it.toExternalForm()) }
        stage.title = "Real-World Algorithm Visualization and Performance Analysis Toolkit"
        stage.minWidth = 1020.0
        stage.minHeight = 660.0
        stage.scene = scene
        stage.show()
    }
}
