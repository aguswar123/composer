package org.betterlyrics.composer.domain

import kotlin.math.abs

enum class EdgeSide { Begin, End }

/** Shortest word or line the timeline editor will allow, in seconds. */
const val MIN_EDIT_DURATION = 0.02

/** Word boundaries closer than this count as one shared boundary and move together. */
private const val LINK_EPSILON = 0.0005

private fun roundMs(t: Double): Double = Math.round(t * 1000.0) / 1000.0

/** Time of one edge: a word edge when [wordIndex] is set, otherwise the line edge. */
fun LyricLine.edgeTime(wordIndex: Int?, side: EdgeSide): Double? {
    if (wordIndex != null) {
        val word = words.getOrNull(wordIndex) ?: return null
        return if (side == EdgeSide.Begin) word.begin else word.end
    }
    return if (side == EdgeSide.Begin) begin else end
}

/** True when word [index] starts exactly where the previous word ends. */
fun isLinkedToPrevious(words: List<WordTiming>, index: Int): Boolean =
    index > 0 && index < words.size && abs(words[index - 1].end - words[index].begin) < LINK_EPSILON

/**
 * Moves one edge to [time], clamped so words never overlap, never get shorter than
 * [MIN_EDIT_DURATION], and stay inside [limitMin]..[limitMax] (the neighbouring lines).
 * A boundary shared by two touching words moves both. Returns the line unchanged when
 * the move is not possible.
 */
fun LyricLine.withEdge(
    wordIndex: Int?,
    side: EdgeSide,
    time: Double,
    limitMin: Double,
    limitMax: Double,
): LyricLine = if (wordIndex != null) {
    withWordEdge(wordIndex, side, time, limitMin, limitMax)
} else {
    withLineEdge(side, time, limitMin, limitMax)
}

private fun LyricLine.withWordEdge(
    i: Int,
    side: EdgeSide,
    time: Double,
    limitMin: Double,
    limitMax: Double,
): LyricLine {
    val ws = words
    if (i !in ws.indices) return this
    val word = ws[i]
    val out = ws.toMutableList()

    if (side == EdgeSide.Begin) {
        val linked = isLinkedToPrevious(ws, i)
        val lower = when {
            i == 0 -> limitMin
            linked -> ws[i - 1].begin + MIN_EDIT_DURATION
            else -> ws[i - 1].end
        }
        val upper = word.end - MIN_EDIT_DURATION
        if (lower > upper) return this
        val t = roundMs(time.coerceIn(lower, upper))
        out[i] = word.copy(begin = t)
        if (linked) out[i - 1] = ws[i - 1].copy(end = t)
    } else {
        val linked = i < ws.lastIndex && abs(ws[i + 1].begin - word.end) < LINK_EPSILON
        val upper = when {
            i == ws.lastIndex -> limitMax
            linked -> ws[i + 1].end - MIN_EDIT_DURATION
            else -> ws[i + 1].begin
        }
        val lower = word.begin + MIN_EDIT_DURATION
        if (lower > upper) return this
        val t = roundMs(time.coerceIn(lower, upper))
        out[i] = word.copy(end = t)
        if (linked) out[i + 1] = ws[i + 1].copy(begin = t)
    }
    return copy(words = out)
}

private fun LyricLine.withLineEdge(side: EdgeSide, time: Double, limitMin: Double, limitMax: Double): LyricLine {
    val b = begin ?: return this
    val e = end ?: return this
    return if (side == EdgeSide.Begin) {
        val upper = e - MIN_EDIT_DURATION
        if (limitMin > upper) this else copy(begin = roundMs(time.coerceIn(limitMin, upper)))
    } else {
        val lower = b + MIN_EDIT_DURATION
        if (lower > limitMax) this else copy(end = roundMs(time.coerceIn(lower, limitMax)))
    }
}
