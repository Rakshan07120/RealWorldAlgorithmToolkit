package algotoolkit.ui

import algotoolkit.model.Graph
import javafx.beans.property.SimpleObjectProperty

/** Data shared between tabs. Replacing a value notifies the tabs, which reset their algorithm state. */
class AppState {
    val graph = SimpleObjectProperty(Graph())
    val sortData = SimpleObjectProperty<IntArray?>()
    val searchData = SimpleObjectProperty<IntArray?>()
}
