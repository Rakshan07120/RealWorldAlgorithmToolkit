package algotoolkit.model

import kotlin.random.Random

enum class DataPattern(val label: String) {
    RANDOM("Random"),
    SORTED("Sorted"),
    REVERSED("Reversed"),
    NEARLY_SORTED("Nearly sorted"),
    FEW_UNIQUE("Few unique values");

    override fun toString() = label
}

object DataGenerator {
    fun generate(pattern: DataPattern, size: Int, seed: Long): IntArray {
        require(size >= 0) { "Size cannot be negative" }
        val rnd = Random(seed)
        return when (pattern) {
            DataPattern.RANDOM -> IntArray(size) { rnd.nextInt(maxOf(10, size * 4)) }
            DataPattern.SORTED -> IntArray(size) { it }
            DataPattern.REVERSED -> IntArray(size) { size - 1 - it }
            DataPattern.FEW_UNIQUE -> IntArray(size) { rnd.nextInt(10) }
            DataPattern.NEARLY_SORTED -> {
                val a = IntArray(size) { it }
                if (size > 1) {
                    repeat(maxOf(1, size / 20)) {
                        val i = rnd.nextInt(size)
                        val j = rnd.nextInt(size)
                        val t = a[i]; a[i] = a[j]; a[j] = t
                    }
                }
                a
            }
        }
    }
}
