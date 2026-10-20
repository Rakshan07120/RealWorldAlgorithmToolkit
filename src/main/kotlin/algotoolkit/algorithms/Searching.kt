package algotoolkit.algorithms

sealed interface SearchStep {
    /** Element [index] is being compared with the target; [lo]..[hi] is the range still under consideration. */
    data class Examine(val index: Int, val lo: Int, val hi: Int) : SearchStep
    data class Found(val index: Int) : SearchStep
    data object NotFound : SearchStep
}

class SearchResult(
    val algorithm: SearchAlgorithm,
    val target: Int,
    val index: Int,
    val comparisons: Long,
    val steps: List<SearchStep>,
    /** The array that was actually searched (a sorted copy when binary search needed one). */
    val data: IntArray,
    val sortedFirst: Boolean
) {
    val found: Boolean get() = index >= 0
}

enum class SearchAlgorithm(
    val label: String,
    val best: String,
    val average: String,
    val worst: String,
    val description: String
) {
    LINEAR(
        "Linear Search", "O(1)", "O(n)", "O(n)",
        "Examines elements from left to right until the target is found. Works on any array and " +
            "returns the first matching position."
    ),
    BINARY(
        "Binary Search", "O(1)", "O(log n)", "O(log n)",
        "Requires sorted data (unsorted input is sorted explicitly first). Compares the target with the " +
            "middle element and discards half of the range each step. With duplicates it returns " +
            "one matching position, not necessarily the first. A three-way comparison counts as one comparison."
    );

    override fun toString() = label
}

object Searching {
    /** Returns (index or -1, comparisons). */
    fun linear(a: IntArray, target: Int, steps: MutableList<SearchStep>? = null): Pair<Int, Long> {
        var comparisons = 0L
        for (i in a.indices) {
            steps?.add(SearchStep.Examine(i, 0, a.size - 1))
            comparisons++
            if (a[i] == target) {
                steps?.add(SearchStep.Found(i))
                return i to comparisons
            }
        }
        steps?.add(SearchStep.NotFound)
        return -1 to comparisons
    }

    /** Requires [a] to be sorted ascending. Returns (index or -1, comparisons). */
    fun binary(a: IntArray, target: Int, steps: MutableList<SearchStep>? = null): Pair<Int, Long> {
        var lo = 0
        var hi = a.size - 1
        var comparisons = 0L
        while (lo <= hi) {
            val mid = lo + (hi - lo) / 2
            steps?.add(SearchStep.Examine(mid, lo, hi))
            comparisons++
            val c = a[mid].compareTo(target)
            if (c == 0) {
                steps?.add(SearchStep.Found(mid))
                return mid to comparisons
            }
            if (c < 0) lo = mid + 1 else hi = mid - 1
        }
        steps?.add(SearchStep.NotFound)
        return -1 to comparisons
    }

    /** Runs [algorithm]; binary search first sorts a copy of unsorted input and reports that in the result. */
    fun search(algorithm: SearchAlgorithm, data: IntArray, target: Int, record: Boolean = false): SearchResult {
        var array = data
        var sortedFirst = false
        if (algorithm == SearchAlgorithm.BINARY && !data.isSortedAscending()) {
            array = data.copyOf()
            SortAlgorithm.MERGE.sort(array)
            sortedFirst = true
        }
        val steps: MutableList<SearchStep>? = if (record) ArrayList() else null
        val (index, comparisons) = when (algorithm) {
            SearchAlgorithm.LINEAR -> linear(array, target, steps)
            SearchAlgorithm.BINARY -> binary(array, target, steps)
        }
        return SearchResult(algorithm, target, index, comparisons, steps ?: emptyList(), array, sortedFirst)
    }
}
