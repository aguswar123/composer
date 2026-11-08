package org.betterlyrics.composer.domain

import java.util.Locale

/** `m:ss.mmm`, the same shape the web app writes into TTML. */
fun formatTime(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00.000"
    val totalMs = Math.round(seconds * 1000.0)
    val mins = totalMs / 60_000
    val secs = (totalMs % 60_000) / 1000
    val ms = totalMs % 1000
    return String.format(Locale.ROOT, "%d:%02d.%03d", mins, secs, ms)
}

/** Accepts `HH:MM:SS.mmm`, `MM:SS.mmm` and `SS.mmm`. */
fun parseTtmlTimestamp(timestamp: String): Double {
    val value = timestamp.trim()
    if (value.isEmpty()) return 0.0
    val parts = value.split(":")
    return when (parts.size) {
        3 -> {
            val hours = parts[0].trim().toIntOrNull() ?: 0
            val minutes = parts[1].trim().toIntOrNull() ?: 0
            hours * 3600.0 + minutes * 60.0 + parseSeconds(parts[2])
        }
        2 -> {
            val minutes = parts[0].trim().toIntOrNull() ?: 0
            minutes * 60.0 + parseSeconds(parts[1])
        }
        else -> parseSeconds(value)
    }
}

private fun parseSeconds(raw: String): Double {
    val secs = raw.substringBefore('.').trim().toIntOrNull() ?: 0
    val fraction = raw.substringAfter('.', "").trim()
    val millis = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').take(3).toIntOrNull() ?: 0
    return secs + millis / 1000.0
}
