package algotoolkit.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

fun formatNumber(value: Double): String =
    if (value == floor(value) && abs(value) < 1e15) value.toLong().toString()
    else String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

fun formatMs(ms: Double): String = when {
    ms < 0.001 -> String.format(Locale.ROOT, "%.0f ns", ms * 1e6)
    ms < 1.0 -> String.format(Locale.ROOT, "%.2f \u00b5s", ms * 1e3)
    ms < 1000.0 -> String.format(Locale.ROOT, "%.3f ms", ms)
    else -> String.format(Locale.ROOT, "%.3f s", ms / 1000.0)
}
