package algotoolkit.algorithms

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchingTest {
    @Test
    fun linearFindsFirstOccurrence() {
        val r = Searching.search(SearchAlgorithm.LINEAR, intArrayOf(5, 3, 5, 1), 5, record = true)
        assertEquals(0, r.index)
        assertEquals(1L, r.comparisons)
        assertTrue(r.steps.last() is SearchStep.Found)
    }

    @Test
    fun linearReportsAbsentTarget() {
        val r = Searching.search(SearchAlgorithm.LINEAR, intArrayOf(5, 3, 5, 1), 9, record = true)
        assertFalse(r.found)
        assertEquals(-1, r.index)
        assertEquals(4L, r.comparisons)
        assertEquals(SearchStep.NotFound, r.steps.last())
    }

    @Test
    fun linearOnEmptyArray() {
        val r = Searching.search(SearchAlgorithm.LINEAR, IntArray(0), 1, record = true)
        assertEquals(-1, r.index)
        assertEquals(0L, r.comparisons)
        assertEquals(listOf<SearchStep>(SearchStep.NotFound), r.steps)
    }

    @Test
    fun binaryFindsEveryElementOfSortedArray() {
        val a = intArrayOf(1, 3, 5, 7, 9)
        for ((i, v) in a.withIndex()) assertEquals(i, Searching.search(SearchAlgorithm.BINARY, a, v).index)
    }

    @Test
    fun binaryReportsAbsentTargets() {
        val a = intArrayOf(1, 3, 5, 7, 9)
        for (v in listOf(0, 2, 4, 6, 8, 10)) assertEquals(-1, Searching.search(SearchAlgorithm.BINARY, a, v).index)
    }

    @Test
    fun binaryOnEmptyAndSingleElementArrays() {
        assertEquals(-1, Searching.search(SearchAlgorithm.BINARY, IntArray(0), 1).index)
        assertEquals(0, Searching.search(SearchAlgorithm.BINARY, intArrayOf(4), 4).index)
        assertEquals(-1, Searching.search(SearchAlgorithm.BINARY, intArrayOf(4), 5).index)
    }

    @Test
    fun binaryWithDuplicatesReturnsAMatchingPosition() {
        val a = intArrayOf(2, 2, 2, 2)
        val r = Searching.search(SearchAlgorithm.BINARY, a, 2)
        assertTrue(r.found)
        assertEquals(2, a[r.index])
    }

    @Test
    fun binaryOnUnsortedInputSortsACopyExplicitly() {
        val input = intArrayOf(9, 1, 5, 3)
        val r = Searching.search(SearchAlgorithm.BINARY, input, 5)
        assertTrue(r.sortedFirst)
        assertArrayEquals(intArrayOf(1, 3, 5, 9), r.data)
        assertEquals(2, r.index)
        assertArrayEquals(intArrayOf(9, 1, 5, 3), input, "the caller's array must not be modified")
    }

    @Test
    fun linearNeverSortsAndSortedBinaryInputIsNotCopied() {
        assertFalse(Searching.search(SearchAlgorithm.LINEAR, intArrayOf(3, 1, 2), 2).sortedFirst)
        assertFalse(Searching.search(SearchAlgorithm.BINARY, intArrayOf(1, 2, 3), 2).sortedFirst)
    }

    @Test
    fun binaryComparisonsStayLogarithmic() {
        val a = IntArray(1000) { it }
        var worst = 0L
        for (v in a) worst = maxOf(worst, Searching.search(SearchAlgorithm.BINARY, a, v).comparisons)
        assertTrue(worst <= 10, "worst case was $worst")
    }

    @Test
    fun stepsStartWithExamineAndEndWithOutcome() {
        val r = Searching.search(SearchAlgorithm.BINARY, intArrayOf(1, 3, 5, 7, 9), 7, record = true)
        assertTrue(r.steps.first() is SearchStep.Examine)
        assertEquals(SearchStep.Found(3), r.steps.last())
        assertEquals(r.comparisons.toInt() + 1, r.steps.size)
    }

    @Test
    fun stepsAreNotRecordedUnlessRequested() {
        assertTrue(Searching.search(SearchAlgorithm.LINEAR, intArrayOf(1, 2), 2).steps.isEmpty())
    }
}
