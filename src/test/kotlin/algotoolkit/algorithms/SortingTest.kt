package algotoolkit.algorithms

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.random.Random

class SortingTest {
    private fun replay(input: IntArray, steps: List<SortStep>): IntArray {
        val a = input.copyOf()
        for (s in steps) {
            when (s) {
                is SortStep.Compare -> {}
                is SortStep.Swap -> {
                    val t = a[s.i]; a[s.i] = a[s.j]; a[s.j] = t
                }
                is SortStep.Write -> a[s.index] = s.value
            }
        }
        return a
    }

    /** Sorts a copy, compares with the reference result and checks that replaying the steps gives the same array. */
    private fun verify(algo: SortAlgorithm, input: IntArray) {
        val expected = input.sortedArray()
        val steps = ArrayList<SortStep>()
        val work = input.copyOf()
        algo.sort(work, steps)
        assertArrayEquals(expected, work, "$algo on ${input.toList()}")
        assertArrayEquals(expected, replay(input, steps), "$algo step replay on ${input.toList()}")
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun emptyInputProducesNoSteps(algo: SortAlgorithm) {
        val steps = ArrayList<SortStep>()
        val ctx = algo.sort(IntArray(0), steps)
        assertTrue(steps.isEmpty())
        assertEquals(0L, ctx.comparisons)
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun singleElement(algo: SortAlgorithm) {
        verify(algo, intArrayOf(7))
        val steps = ArrayList<SortStep>()
        algo.sort(intArrayOf(7), steps)
        assertTrue(steps.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun twoElements(algo: SortAlgorithm) {
        verify(algo, intArrayOf(2, 1))
        verify(algo, intArrayOf(1, 2))
        verify(algo, intArrayOf(5, 5))
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun duplicates(algo: SortAlgorithm) {
        verify(algo, intArrayOf(3, 1, 3, 1, 2, 2, 3, 1))
        verify(algo, intArrayOf(4, 4, 4, 4, 4))
        verify(algo, intArrayOf(2, 1, 2, 1, 2, 1))
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun negativeValues(algo: SortAlgorithm) {
        verify(algo, intArrayOf(0, -5, 3, -1, -5, 8, Int.MIN_VALUE, Int.MAX_VALUE))
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun sortedAndReversed(algo: SortAlgorithm) {
        verify(algo, IntArray(50) { it })
        verify(algo, IntArray(50) { 50 - it })
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm::class)
    fun randomArrays(algo: SortAlgorithm) {
        val rnd = Random(5)
        repeat(20) { round ->
            verify(algo, IntArray(rnd.nextInt(1, 200)) { rnd.nextInt(-50, 50) })
            if (round == 0) verify(algo, IntArray(500) { rnd.nextInt(1000) })
        }
    }

    @Test
    fun bubbleSortOnSortedInputStopsAfterOnePass() {
        val steps = ArrayList<SortStep>()
        val ctx = SortAlgorithm.BUBBLE.sort(intArrayOf(1, 2, 3, 4, 5), steps)
        assertEquals(4L, ctx.comparisons)
        assertEquals(0L, ctx.swaps)
        assertTrue(steps.none { it is SortStep.Swap })
    }

    @Test
    fun bubbleSortCountsOnReversedThree() {
        val ctx = SortAlgorithm.BUBBLE.sort(intArrayOf(3, 2, 1))
        assertEquals(3L, ctx.comparisons)
        assertEquals(3L, ctx.swaps)
    }

    @Test
    fun selectionSortAlwaysMakesAllComparisons() {
        assertEquals(15L, SortAlgorithm.SELECTION.sort(intArrayOf(1, 2, 3, 4, 5, 6)).comparisons)
        assertEquals(15L, SortAlgorithm.SELECTION.sort(intArrayOf(6, 5, 4, 3, 2, 1)).comparisons)
    }

    @Test
    fun selectionSortMakesAtMostNMinusOneSwaps() {
        val ctx = SortAlgorithm.SELECTION.sort(intArrayOf(6, 5, 4, 3, 2, 1))
        assertTrue(ctx.swaps <= 5)
    }

    @Test
    fun insertionSortOnSortedInputIsLinear() {
        val ctx = SortAlgorithm.INSERTION.sort(intArrayOf(1, 2, 3, 4, 5))
        assertEquals(4L, ctx.comparisons)
        assertEquals(0L, ctx.writes)
    }

    @Test
    fun mergeSortUsesWritesNotSwaps() {
        val ctx = SortAlgorithm.MERGE.sort(intArrayOf(4, 3, 2, 1))
        assertEquals(0L, ctx.swaps)
        assertTrue(ctx.writes > 0)
    }

    @Test
    fun isSortedAscendingDetectsOrder() {
        assertTrue(intArrayOf().isSortedAscending())
        assertTrue(intArrayOf(1, 1, 2).isSortedAscending())
        assertTrue(!intArrayOf(2, 1).isSortedAscending())
    }

    @Test
    fun quickSortHandlesLargeSortedInputWithoutDeepRecursion() {
        val a = IntArray(50_000) { it }
        SortAlgorithm.QUICK.sort(a)
        assertTrue(a.isSortedAscending())
    }
}
