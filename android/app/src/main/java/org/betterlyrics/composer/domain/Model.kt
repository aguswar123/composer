package org.betterlyrics.composer.domain

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Default syllable split character, same as the web app's setting. */
const val SPLIT_CHAR = "|"

/** Minimum duration given to a word whose end is unknown. */
const val DEFAULT_MIN_WORD_DURATION = 0.05

fun stripSplitCharacter(text: String): String = text.replace(SPLIT_CHAR, "")

fun newLineId(): String = UUID.randomUUID().toString()

/** Times are in seconds, like the web app. */
data class WordTiming(
    val text: String,
    val begin: Double,
    val end: Double,
    val explicit: Boolean = false,
)

data class Agent(
    val id: String,
    val type: String = "person",
    val name: String? = null,
)

val DEFAULT_AGENTS: List<Agent> = listOf(Agent(id = "v1", type = "person", name = "Lead"))

data class ProjectMetadata(
    val title: String = "",
    val artists: List<String> = emptyList(),
    val album: String = "",
    val language: String? = null,
    val isrc: String? = null,
    val songwriters: List<String> = emptyList(),
    val extra: Map<String, String> = emptyMap(),
)

data class Bounds(val begin: Double, val end: Double)

/**
 * One lyric line. A line is word-synced (words), line-synced (begin and end) or untimed.
 * Never both: call [reconciled] after building one from loose parts.
 */
data class LyricLine(
    val id: String = newLineId(),
    val text: String,
    val agentId: String = "v1",
    val words: List<WordTiming> = emptyList(),
    val begin: Double? = null,
    val end: Double? = null,
    val backgroundText: String? = null,
    val backgroundWords: List<WordTiming> = emptyList(),
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()

    val isLineSynced: Boolean get() = words.isEmpty() && begin != null && end != null

    fun mainBounds(): Bounds? {
        if (words.isNotEmpty()) return Bounds(words.minOf { it.begin }, words.maxOf { it.end })
        val b = begin
        val e = end
        return if (b != null && e != null) Bounds(b, e) else null
    }

    fun bgBounds(): Bounds? {
        if (backgroundWords.isEmpty()) return null
        return Bounds(backgroundWords.minOf { it.begin }, backgroundWords.maxOf { it.end })
    }

    fun effectiveBounds(): Bounds? {
        val main = mainBounds() ?: return null
        val bg = bgBounds() ?: return main
        return Bounds(min(main.begin, bg.begin), max(main.end, bg.end))
    }

    val isTimed: Boolean get() = effectiveBounds() != null

    fun hasMainLyrics(): Boolean = stripSplitCharacter(text).trim().isNotEmpty()

    /** Lines that appear in exported TTML. */
    fun isExported(): Boolean = isTimed && (hasMainLyrics() || !backgroundText.isNullOrBlank())

    /** Enforces "never both word-synced and line-synced". */
    fun reconciled(): LyricLine = when {
        words.isNotEmpty() -> if (begin == null && end == null) this else copy(begin = null, end = null)
        begin != null && end != null -> this
        begin == null && end == null -> this
        else -> copy(begin = null, end = null)
    }

    /**
     * Replaces the text. Word timings no longer match, so a word-synced line
     * falls back to line timing over the same span.
     */
    fun withText(newText: String): LyricLine {
        if (newText == text) return this
        val bounds = mainBounds()
        return copy(
            text = newText,
            words = emptyList(),
            begin = bounds?.begin,
            end = bounds?.end,
        ).reconciled()
    }
}
