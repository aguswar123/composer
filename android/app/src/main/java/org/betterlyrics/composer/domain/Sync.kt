package org.betterlyrics.composer.domain

import kotlin.math.max

enum class SyncMode { Line, Word }

data class SyncCursor(val lineIndex: Int = 0, val wordIndex: Int = 0)

/** The line or word whose end time is waiting for the next tap. */
data class PendingEnd(val lineId: String, val wordIndex: Int?)

data class SyncState(
    val lines: List<LyricLine>,
    val cursor: SyncCursor = SyncCursor(),
    val pending: PendingEnd? = null,
)

/**
 * Splits a line into syncable word slots. A trailing space marks a real word break;
 * syllables joined by the split character stay adjacent (no trailing space).
 */
fun splitIntoWords(text: String): List<String> {
    val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val result = mutableListOf<String>()
    tokens.forEachIndexed { tokenIndex, token ->
        val parts = token.split(SPLIT_CHAR).filter { it.isNotEmpty() }
        parts.forEachIndexed { partIndex, part ->
            val endsToken = partIndex == parts.lastIndex && tokenIndex < tokens.lastIndex
            result += if (endsToken) "$part " else part
        }
    }
    return result
}

/** First line at or after [from] that has lyrics to sync, or null when finished. */
fun nextSyncableIndex(lines: List<LyricLine>, from: Int): Int? {
    val start = from.coerceAtLeast(0)
    for (i in start until lines.size) if (lines[i].hasMainLyrics()) return i
    return null
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { i, item -> if (i == index) value else item }

private fun closePending(lines: List<LyricLine>, pending: PendingEnd?, t: Double): List<LyricLine> {
    if (pending == null) return lines
    return lines.map { line ->
        if (line.id != pending.lineId) return@map line
        val wordIndex = pending.wordIndex
        if (wordIndex == null) {
            val begin = line.begin ?: return@map line
            line.copy(end = max(t, begin + DEFAULT_MIN_WORD_DURATION))
        } else {
            val word = line.words.getOrNull(wordIndex) ?: return@map line
            val words = line.words.toMutableList()
            words[wordIndex] = word.copy(end = max(t, word.begin + DEFAULT_MIN_WORD_DURATION))
            line.copy(words = words)
        }
    }
}

/**
 * One tap at time [t] seconds. Stamps the next line (or word), and closes the end
 * of the previous one at the same instant. Returns [state] itself when nothing is left to sync.
 */
fun applyTap(state: SyncState, mode: SyncMode, t: Double): SyncState {
    val i = nextSyncableIndex(state.lines, state.cursor.lineIndex) ?: return state
    val wordIndex = if (i == state.cursor.lineIndex) state.cursor.wordIndex else 0
    val closed = closePending(state.lines, state.pending, t)
    val line = closed[i]

    if (mode == SyncMode.Line) {
        val updated = line.copy(words = emptyList(), begin = t, end = t + DEFAULT_MIN_WORD_DURATION).reconciled()
        val next = nextSyncableIndex(closed, i + 1) ?: closed.size
        return SyncState(closed.replaced(i, updated), SyncCursor(next, 0), PendingEnd(line.id, null))
    }

    val slots = splitIntoWords(line.text)
    if (slots.isEmpty()) return state
    val k = wordIndex.coerceIn(0, slots.size - 1)
    val kept = if (k == 0) emptyList() else line.words.take(k)
    val word = WordTiming(slots[k], t, t + DEFAULT_MIN_WORD_DURATION)
    val updated = line.copy(words = kept + word, begin = null, end = null).reconciled()
    val cursor = if (k + 1 < slots.size) {
        SyncCursor(i, k + 1)
    } else {
        SyncCursor(nextSyncableIndex(closed, i + 1) ?: closed.size, 0)
    }
    return SyncState(closed.replaced(i, updated), cursor, PendingEnd(line.id, k))
}

/** Closes the pending end at [t] without starting anything (instrumental gaps, song end). */
fun applyEnd(state: SyncState, t: Double): SyncState =
    state.copy(lines = closePending(state.lines, state.pending, t), pending = null)

fun applySelect(state: SyncState, index: Int): SyncState =
    state.copy(cursor = SyncCursor(index.coerceIn(0, max(0, state.lines.size - 1)), 0), pending = null)

/** (done, total) in lines for [SyncMode.Line] and in word slots for [SyncMode.Word]. */
fun syncProgress(lines: List<LyricLine>, mode: SyncMode): Pair<Int, Int> {
    var done = 0
    var total = 0
    for (line in lines) {
        if (!line.hasMainLyrics()) continue
        if (mode == SyncMode.Line) {
            total += 1
            if (line.mainBounds() != null) done += 1
        } else {
            val slots = splitIntoWords(line.text).size
            total += slots
            done += minOf(line.words.size, slots)
        }
    }
    return done to total
}

/** Safe `.ttml` file name from the song title. */
fun ttmlFileName(title: String): String {
    val cleaned = title.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "_").ifEmpty { "lyrics" }
    return "$cleaned.ttml"
}
