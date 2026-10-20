package algotoolkit.algorithms

/** One observable event of a sorting run. Replaying the events on a copy of the input reproduces the sort. */
sealed interface SortStep {
    data class Compare(val i: Int, val j: Int) : SortStep
    data class Swap(val i: Int, val j: Int) : SortStep
    data class Write(val index: Int, val value: Int) : SortStep
}

fun IntArray.isSortedAscending(): Boolean {
    for (i in 1 until size) if (this[i - 1] > this[i]) return false
    return true
}

/**
 * The only way an algorithm touches the data: every comparison, swap and write goes through here,
 * so counters and (optional) recorded steps always describe the real execution.
 */
class SortContext(val data: IntArray, private val steps: MutableList<SortStep>? = null) {
    var comparisons = 0L
        private set
    var swaps = 0L
        private set
    var writes = 0L
        private set

    /** Compares x and y, which stand at positions i and j (positions are only used for visualization). */
    fun compare(i: Int, j: Int, x: Int, y: Int): Int {
        comparisons++
        steps?.add(SortStep.Compare(i, j))
        return x.compareTo(y)
    }

    fun compareAt(i: Int, j: Int): Int = compare(i, j, data[i], data[j])

    fun swap(i: Int, j: Int) {
        swaps++
        steps?.add(SortStep.Swap(i, j))
        val t = data[i]; data[i] = data[j]; data[j] = t
    }

    fun write(index: Int, value: Int) {
        writes++
        steps?.add(SortStep.Write(index, value))
        data[index] = value
    }
}

enum class SortAlgorithm(
    val label: String,
    val best: String,
    val average: String,
    val worst: String,
    val space: String,
    val stable: Boolean,
    val description: String,
    private val impl: (SortContext) -> Unit
) {
    BUBBLE(
        "Bubble Sort", "O(n)", "O(n\u00b2)", "O(n\u00b2)", "O(1)", true,
        "Repeatedly compares adjacent elements and swaps them when out of order, so the largest remaining " +
            "element bubbles to the end of the unsorted part. Stops early after a pass without swaps.",
        { Sorting.bubble(it) }
    ),
    SELECTION(
        "Selection Sort", "O(n\u00b2)", "O(n\u00b2)", "O(n\u00b2)", "O(1)", false,
        "Finds the smallest element of the unsorted part and swaps it into the next position. " +
            "Always makes n(n-1)/2 comparisons but at most n-1 swaps.",
        { Sorting.selection(it) }
    ),
    INSERTION(
        "Insertion Sort", "O(n)", "O(n\u00b2)", "O(n\u00b2)", "O(1)", true,
        "Takes the next element and shifts larger elements of the sorted prefix one place right until " +
            "its position is found. Very fast on nearly sorted data.",
        { Sorting.insertion(it) }
    ),
    MERGE(
        "Merge Sort", "O(n log n)", "O(n log n)", "O(n log n)", "O(n)", true,
        "Splits the array in halves, sorts each half recursively and merges the sorted halves using " +
            "a temporary buffer. Movements are counted as writes.",
        { Sorting.merge(it) }
    ),
    QUICK(
        "Quick Sort", "O(n log n)", "O(n log n)", "O(n\u00b2)", "O(log n)", false,
        "Moves the middle element to the end as pivot, partitions smaller elements to its left and " +
            "recurses on both sides (smaller side first, so stack depth stays O(log n)). " +
            "Inputs with very many equal keys degrade towards O(n\u00b2).",
        { Sorting.quick(it) }
    );

    override fun toString() = label

    /** Sorts [data] in place and returns the context holding the operation counters. */
    fun sort(data: IntArray, steps: MutableList<SortStep>? = null): SortContext {
        val ctx = SortContext(data, steps)
        impl(ctx)
        return ctx
    }
}

object Sorting {
    fun bubble(ctx: SortContext) {
        var end = ctx.data.size - 1
        while (end > 0) {
            var swapped = false
            for (j in 0 until end) {
                if (ctx.compareAt(j, j + 1) > 0) {
                    ctx.swap(j, j + 1)
                    swapped = true
                }
            }
            if (!swapped) return
            end--
        }
    }

    fun selection(ctx: SortContext) {
        val n = ctx.data.size
        for (i in 0 until n - 1) {
            var min = i
            for (j in i + 1 until n) if (ctx.compareAt(j, min) < 0) min = j
            if (min != i) ctx.swap(i, min)
        }
    }

    fun insertion(ctx: SortContext) {
        val a = ctx.data
        for (i in 1 until a.size) {
            val key = a[i]
            var j = i - 1
            while (j >= 0 && ctx.compare(j, j + 1, a[j], key) > 0) {
                ctx.write(j + 1, a[j])
                j--
            }
            if (j + 1 != i) ctx.write(j + 1, key)
        }
    }

    fun merge(ctx: SortContext) {
        val n = ctx.data.size
        if (n < 2) return
        mergeRange(ctx, IntArray(n), 0, n - 1)
    }

    private fun mergeRange(ctx: SortContext, tmp: IntArray, lo: Int, hi: Int) {
        if (lo >= hi) return
        val mid = lo + (hi - lo) / 2
        mergeRange(ctx, tmp, lo, mid)
        mergeRange(ctx, tmp, mid + 1, hi)
        System.arraycopy(ctx.data, lo, tmp, lo, hi - lo + 1)
        var i = lo
        var j = mid + 1
        var k = lo
        while (i <= mid && j <= hi) {
            if (ctx.compare(i, j, tmp[i], tmp[j]) <= 0) ctx.write(k++, tmp[i++]) else ctx.write(k++, tmp[j++])
        }
        // Remaining right-half elements already sit in their final positions (k == j).
        while (i <= mid) ctx.write(k++, tmp[i++])
    }

    fun quick(ctx: SortContext) = quickRange(ctx, 0, ctx.data.size - 1)

    private fun quickRange(ctx: SortContext, from: Int, to: Int) {
        var lo = from
        var hi = to
        while (lo < hi) {
            val mid = lo + (hi - lo) / 2
            if (mid != hi) ctx.swap(mid, hi)
            var p = lo
            for (j in lo until hi) {
                if (ctx.compareAt(j, hi) < 0) {
                    if (j != p) ctx.swap(j, p)
                    p++
                }
            }
            if (p != hi) ctx.swap(p, hi)
            if (p - lo < hi - p) {
                quickRange(ctx, lo, p - 1)
                lo = p + 1
            } else {
                quickRange(ctx, p + 1, hi)
                hi = p - 1
            }
        }
    }
}
