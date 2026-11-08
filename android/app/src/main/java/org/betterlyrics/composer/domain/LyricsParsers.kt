package org.betterlyrics.composer.domain

private const val CLOCK = """(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?"""
private val LINE_TIMESTAMP = Regex("\\[" + CLOCK + "\\]")
private val INLINE_WORD_TAG = Regex("<" + CLOCK + ">")
private val LENGTH_TAG = Regex("^" + CLOCK + "$")
private val LRC_METADATA_TAG = Regex("^\\[([a-zA-Z]+):(.*)\\]$")
private val SINGER_OR_BACKGROUND_TAG = Regex("^(?:\\[[\\d:.]+\\]\\s*)*v\\d+:|\\[[bB][gG]:")
private const val PENDING_END = -1.0

// -- Plain text ---------------------------------------------------------------

/** One line per lyric line. Leading and trailing blank lines are dropped. */
fun parseTxt(content: String): ParseResult {
    val texts = content.split(Regex("\r?\n")).map { it.trim() }
    val first = texts.indexOfFirst { it.isNotEmpty() }
    val last = texts.indexOfLast { it.isNotEmpty() }
    val lines = if (first == -1) emptyList() else texts.subList(first, last + 1).map { LyricLine(text = it) }
    return ParseResult(lines = lines)
}

// -- LRC ----------------------------------------------------------------------

private fun parseLrcClock(minutes: String, seconds: String, fraction: String): Double? {
    val s = seconds.toIntOrNull() ?: return null
    if (s >= 60) return null
    val milli = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').toIntOrNull() ?: 0
    return (minutes.toIntOrNull() ?: 0) * 60.0 + s + milli / 1000.0
}

private class LooseLine(
    var text: String,
    var begin: Double?,
    var end: Double? = null,
    var words: MutableList<WordTiming>? = null,
)

private class Marker(val timestamp: Double, val start: Int, val end: Int)

private class InlineParse(
    val invalid: Boolean,
    val cleanText: String = "",
    val words: MutableList<WordTiming> = mutableListOf(),
)

private fun parseInlineWordTags(text: String, lineBegin: Double): InlineParse? {
    val markers = mutableListOf<Marker>()
    for (m in INLINE_WORD_TAG.findAll(text)) {
        val ts = parseLrcClock(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            ?: return InlineParse(invalid = true)
        markers += Marker(ts, m.range.first, m.range.last + 1)
    }
    if (markers.isEmpty()) return null

    val words = mutableListOf<WordTiming>()
    val leading = text.substring(0, markers[0].start)
    if (leading.isNotBlank()) words += WordTiming(leading, lineBegin, markers[0].timestamp)

    for (i in markers.indices) {
        val marker = markers[i]
        val next = markers.getOrNull(i + 1)
        val wordText = text.substring(marker.end, next?.start ?: text.length)
        if (wordText.isEmpty()) continue
        words += WordTiming(wordText, marker.timestamp, next?.timestamp ?: PENDING_END)
    }
    if (words.isEmpty()) return null
    return InlineParse(invalid = false, cleanText = reconstructLineText(words), words = words)
}

private fun fillLineEnds(lines: MutableList<LooseLine>, gaps: MutableList<Double>, songEnd: Double?) {
    lines.sortBy { it.begin ?: 0.0 }
    gaps.sort()
    for (i in lines.indices) {
        val begin = lines[i].begin ?: continue
        val nextBegin = lines.getOrNull(i + 1)?.begin
        val gap = gaps.firstOrNull { it > begin }
        lines[i].end = if (gap != null && (nextBegin == null || gap < nextBegin)) gap else nextBegin
    }

    // The last line has no next line to take its end from.
    val last = lines.lastOrNull()
    if (last != null && last.words == null && songEnd != null) {
        val lastBegin = last.begin
        if (lastBegin != null && last.end == null && songEnd > lastBegin) last.end = songEnd
    }

    for (i in lines.indices) {
        val words = lines[i].words ?: continue
        val lastWord = words.lastOrNull() ?: continue
        if (lastWord.end != PENDING_END) continue
        val candidates = listOf(lines[i].end, lines.getOrNull(i + 1)?.begin, songEnd)
        val end = candidates.firstOrNull { it != null && it > lastWord.begin }
            ?: (lastWord.begin + DEFAULT_MIN_WORD_DURATION)
        words[words.lastIndex] = lastWord.copy(end = end)
    }
}

/** Line-timed LRC and enhanced (word-timed) LRC. */
fun parseLrc(content: String, fallbackDuration: Double? = null): ParseResult {
    val rawLines = content.split(Regex("\r?\n"))
    val unsupported = rawLines.indexOfFirst { SINGER_OR_BACKGROUND_TAG.containsMatchIn(it.trim()) }
    if (unsupported != -1) {
        return ParseResult(
            issues = listOf(
                "This LRC uses singer or background tags (v1:, [bg:]) that are not part of LRC. " +
                    "Use a TTML or plain LRC version instead.",
            ),
        )
    }

    var metadata = ProjectMetadata()
    var lengthSeconds: Double? = null
    val lines = mutableListOf<LooseLine>()
    val gaps = mutableListOf<Double>()
    val issues = mutableListOf<String>()

    rawLines.forEachIndexed { index, raw ->
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return@forEachIndexed
        val lineNumber = index + 1

        val meta = LRC_METADATA_TAG.matchEntire(trimmed)
        if (meta != null) {
            val value = meta.groupValues[2].trim()
            if (value.isNotEmpty()) {
                when (meta.groupValues[1].lowercase()) {
                    "ti", "title" -> metadata = metadata.copy(title = value)
                    "ar", "artist" -> metadata = metadata.copy(artists = listOf(value))
                    "al", "album" -> metadata = metadata.copy(album = value)
                    "length" -> LENGTH_TAG.matchEntire(value)?.let {
                        lengthSeconds = parseLrcClock(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                    }
                }
            }
            return@forEachIndexed
        }

        val tagMatches = LINE_TIMESTAMP.findAll(trimmed).toList()
        val timestamps = tagMatches.mapNotNull { parseLrcClock(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        if (tagMatches.isEmpty()) {
            issues += "Line $lineNumber skipped (no timestamp): $trimmed"
            return@forEachIndexed
        }
        if (timestamps.isEmpty()) {
            issues += "Line $lineNumber skipped (invalid timestamp): $trimmed"
            return@forEachIndexed
        }

        val withoutLineTags = LINE_TIMESTAMP.replace(trimmed, "")

        if (timestamps.size == 1) {
            val parsed = parseInlineWordTags(withoutLineTags, timestamps[0])
            if (parsed != null && parsed.invalid) {
                issues += "Line $lineNumber skipped (invalid timestamp): $trimmed"
                return@forEachIndexed
            }
            if (parsed != null) {
                lines += LooseLine(parsed.cleanText, timestamps[0], words = parsed.words)
                return@forEachIndexed
            }
        }

        val cleanText = INLINE_WORD_TAG.replace(withoutLineTags, "").trim()
        if (cleanText.isEmpty()) {
            gaps += timestamps
            return@forEachIndexed
        }
        for (begin in timestamps) lines += LooseLine(cleanText, begin)
    }

    fillLineEnds(lines, gaps, lengthSeconds ?: fallbackDuration)

    val result = lines.map {
        LyricLine(
            text = it.text,
            words = it.words?.toList() ?: emptyList(),
            begin = it.begin,
            end = it.end,
        ).reconciled()
    }
    return ParseResult(
        lines = result,
        metadata = metadata,
        hasTimingData = result.any { it.isTimed },
        issues = issues,
    )
}

// -- Detection ----------------------------------------------------------------

/** Picks a parser from the file extension, falling back to sniffing the content. */
fun parseLyricsText(fileName: String, content: String, fallbackDuration: Double? = null): ParseResult {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val body = content.removePrefix("\uFEFF")
    return when {
        ext == "srt" || ext == "qrc" -> ParseResult(issues = listOf(".$ext import is not supported on Android yet."))
        ext == "ttml" || ext == "xml" || ext == "dfxp" -> parseTtml(body)
        ext == "lrc" -> parseLrc(body, fallbackDuration)
        ext == "txt" -> parseTxt(body)
        body.trimStart().startsWith("<") -> parseTtml(body)
        LINE_TIMESTAMP.containsMatchIn(body) -> parseLrc(body, fallbackDuration)
        else -> parseTxt(body)
    }
}
