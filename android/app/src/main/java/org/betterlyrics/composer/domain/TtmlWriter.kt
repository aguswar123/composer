package org.betterlyrics.composer.domain

const val APPLE_LYRIC_NS = "http://music.apple.com/lyric-ttml-internal"
const val COMPOSER_NS = "https://composer.betterlyrics.org/ttml"

fun escapeXml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")

fun escapeXmlAttribute(text: String): String = escapeXml(text).replace("\"", "&quot;")

private fun emitWordSpan(word: WordTiming, text: String): String {
    val explicitAttr = if (word.explicit) " composer:explicit=\"true\"" else ""
    return "<span begin=\"${formatTime(word.begin)}\" end=\"${formatTime(word.end)}\"$explicitAttr>${escapeXml(text)}</span>"
}

private fun wordsContent(words: List<WordTiming>): String {
    val sb = StringBuilder()
    words.forEachIndexed { i, word ->
        sb.append(emitWordSpan(word, word.text.trimEnd()))
        if (i < words.size - 1 && word.text.endsWith(" ")) sb.append(' ')
    }
    return sb.toString()
}

fun toComposerMeta(metadata: ProjectMetadata): List<Pair<String, String>> {
    val pairs = mutableListOf<Pair<String, String>>()
    metadata.artists.filter { it.isNotEmpty() }.forEach { pairs += "artists" to it }
    if (metadata.album.isNotEmpty()) pairs += "album" to metadata.album
    if (!metadata.isrc.isNullOrEmpty()) pairs += "isrc" to metadata.isrc
    metadata.songwriters.filter { it.isNotEmpty() }.forEach { pairs += "songwriter" to it }
    metadata.extra.keys.sorted().forEach { key ->
        val value = metadata.extra[key]
        if (!value.isNullOrEmpty()) pairs += key to value
    }
    return pairs
}

/**
 * Port of the web app's generateTTML for the core vocabulary: head, agents, lines,
 * word spans and x-bg background vocals. Linked groups, translations and
 * transliterations are not written yet.
 */
fun generateTtml(
    metadata: ProjectMetadata,
    agents: List<Agent>,
    lines: List<LyricLine>,
    durationSeconds: Double = 0.0,
    writeWords: Boolean = true,
    minify: Boolean = false,
): String {
    val nl = if (minify) "" else "\n"
    fun ind(n: Int): String = if (minify) "" else "  ".repeat(n)

    val timingValue = if (writeWords && lines.any { it.isWordSynced }) "Word" else "Line"
    val exported = lines.filter { it.isExported() }
    val keyById = exported.mapIndexed { index, line -> line.id to "L${index + 1}" }.toMap()

    val language = metadata.language?.trim().orEmpty()
    val langAttr = if (language.isNotEmpty()) " xml:lang=\"${escapeXmlAttribute(language)}\"" else ""

    val parts = mutableListOf<String>()
    parts += "<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" " +
        "xmlns:ttp=\"http://www.w3.org/ns/ttml#parameter\" xmlns:itunes=\"$APPLE_LYRIC_NS\" " +
        "xmlns:composer=\"$COMPOSER_NS\" ttp:timeBase=\"media\"$langAttr " +
        "itunes:timing=\"$timingValue\" composer:timing=\"$timingValue\">"

    parts += "${ind(1)}<head>"
    parts += "${ind(2)}<metadata>"
    if (metadata.title.isNotEmpty()) {
        parts += "${ind(3)}<ttm:title>${escapeXml(metadata.title)}</ttm:title>"
    }
    for ((key, value) in toComposerMeta(metadata)) {
        parts += "${ind(3)}<composer:meta key=\"${escapeXmlAttribute(key)}\" value=\"${escapeXmlAttribute(value)}\"/>"
    }
    for (agent in agents) {
        val name = agent.name
        if (!name.isNullOrEmpty()) {
            parts += "${ind(3)}<ttm:agent xml:id=\"${escapeXmlAttribute(agent.id)}\" type=\"${agent.type}\">"
            parts += "${ind(4)}<ttm:name>${escapeXml(name)}</ttm:name>"
            parts += "${ind(3)}</ttm:agent>"
        } else {
            parts += "${ind(3)}<ttm:agent xml:id=\"${escapeXmlAttribute(agent.id)}\" type=\"${agent.type}\"/>"
        }
    }
    parts += "${ind(2)}</metadata>"
    parts += "${ind(1)}</head>"

    val durAttr = if (durationSeconds > 0) " dur=\"${formatTime(durationSeconds)}\"" else ""
    parts += "${ind(1)}<body$durAttr>"
    parts += "${ind(2)}<div>"

    for (line in exported) {
        val lineTiming = line.effectiveBounds() ?: continue
        val agentAttr = if (line.agentId.isNotEmpty()) " ttm:agent=\"${escapeXmlAttribute(line.agentId)}\"" else ""
        val keyAttr = keyById[line.id]?.let { " itunes:key=\"$it\"" } ?: ""

        var content = if (writeWords && line.isWordSynced) {
            wordsContent(line.words)
        } else {
            escapeXml(stripSplitCharacter(line.text))
        }

        val bgText = line.backgroundText
        if (writeWords && !bgText.isNullOrEmpty() && line.backgroundWords.isNotEmpty()) {
            content += "<span ttm:role=\"x-bg\">${wordsContent(line.backgroundWords)}</span>"
        } else if (!bgText.isNullOrEmpty()) {
            val bg = line.bgBounds() ?: lineTiming
            content += "<span ttm:role=\"x-bg\"><span begin=\"${formatTime(bg.begin)}\" " +
                "end=\"${formatTime(bg.end)}\">${escapeXml(stripSplitCharacter(bgText))}</span></span>"
        }

        parts += "${ind(3)}<p begin=\"${formatTime(lineTiming.begin)}\" end=\"${formatTime(lineTiming.end)}\"" +
            "$keyAttr$agentAttr>$content</p>"
    }

    parts += "${ind(2)}</div>"
    parts += "${ind(1)}</body>"
    parts += "</tt>"
    return parts.joinToString(nl)
}

/** Empty string until at least one line has timing, like the web app. */
fun generateProjectTtml(
    metadata: ProjectMetadata,
    agents: List<Agent>,
    lines: List<LyricLine>,
    durationSeconds: Double,
): String = if (lines.none { it.isExported() }) "" else generateTtml(metadata, agents, lines, durationSeconds)
