package org.betterlyrics.composer.audio

import android.net.Uri

data class Track(
    val uri: Uri,
    val fileName: String,
    val title: String?,
    val artist: String?,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: fileName
}

fun formatTime(ms: Long): String {
    val totalCs = (ms.coerceAtLeast(0) / 10)
    val cs = totalCs % 100
    val s = (totalCs / 100) % 60
    val m = totalCs / 6000
    return "%d:%02d.%02d".format(m, s, cs)
}
