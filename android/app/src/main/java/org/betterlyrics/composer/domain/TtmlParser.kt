package org.betterlyrics.composer.domain

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class ParseResult(
    val lines: List<LyricLine> = emptyList(),
    val metadata: ProjectMetadata = ProjectMetadata(),
    val agents: List<Agent> = emptyList(),
    val hasTimingData: Boolean = false,
    val issues: List<String> = emptyList(),
)

private fun NodeList.toNodeList(): List<Node> = (0 until length).map { item(it) }

private fun Element.localTag(): String = tagName.substringAfter(':').lowercase()

/** Attribute lookup that ignores the prefix, so `ttm:role` and `role` both match. */
private fun Element.attrLocal(local: String): String? {
    val attrs = attributes
    for (i in 0 until attrs.length) {
        val attr = attrs.item(i)
        val name = attr.nodeName
        if (name == local || name.endsWith(":$local")) return attr.nodeValue
    }
    return null
}

private fun Element.descendants(): List<Element> =
    getElementsByTagName("*").toNodeList().filterIsInstance<Element>()

private fun buildDocument(content: String): org.w3c.dom.Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    // Block DTDs and external entities where the parser supports it.
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: Exception) {
    }
    val builder = factory.newDocumentBuilder()
    return builder.parse(InputSource(StringReader(content)))
}

fun reconstructLineText(words: List<WordTiming>): String {
    val sb = StringBuilder()
    words.forEachIndexed { i, word ->
        sb.append(word.text)
        if (i < words.size - 1 && !word.text.endsWith(" ")) sb.append(SPLIT_CHAR)
    }
    return sb.toString()
}

private fun readExplicitFlag(el: Element): Boolean {
    val raw = el.attrLocal("explicit") ?: el.attrLocal("obscene") ?: return false
    return when (raw.trim().lowercase()) {
        "", "true", "1", "yes" -> true
        else -> false
    }
}

private fun extractTimedWords(parent: Element, exclude: Element?): List<WordTiming> {
    val words = mutableListOf<WordTiming>()
    for (node in parent.childNodes.toNodeList()) {
        if (node is Element) {
            if (node.attrLocal("role") == "x-bg" || node === exclude) continue
            if (node.localTag() == "span" && node.hasAttribute("begin")) {
                val text = node.textContent ?: ""
                if (text.trim().isNotEmpty()) {
                    words += WordTiming(
                        text = text,
                        begin = parseTtmlTimestamp(node.getAttribute("begin")),
                        end = parseTtmlTimestamp(node.getAttribute("end")),
                        explicit = readExplicitFlag(node),
                    )
                }
            }
        } else if (node.nodeType == Node.TEXT_NODE) {
            // Whitespace between spans belongs to the preceding word.
            val content = node.textContent ?: ""
            if (content.any { it.isWhitespace() } && words.isNotEmpty()) {
                val last = words.last()
                if (!last.text.endsWith(" ")) words[words.lastIndex] = last.copy(text = last.text + " ")
            }
        }
    }
    return words
}

private fun metadataFromPairs(pairs: List<Pair<String, String>>, base: ProjectMetadata): ProjectMetadata {
    var title = base.title
    var album = base.album
    var isrc = base.isrc
    val artists = mutableListOf<String>()
    val songwriters = mutableListOf<String>()
    val extra = linkedMapOf<String, String>()
    for ((key, value) in pairs) {
        if (key.isEmpty() || value.isEmpty()) continue
        when (key) {
            "artists" -> artists += value
            "album" -> album = value
            "isrc" -> isrc = value
            "musicName" -> if (title.isEmpty()) title = value
            "songwriter", "songwriters" -> songwriters += value
            "ytVideoId" -> Unit
            else -> extra[key] = value
        }
    }
    return base.copy(
        title = title,
        album = album,
        isrc = isrc,
        artists = if (artists.isNotEmpty()) artists else base.artists,
        songwriters = if (songwriters.isNotEmpty()) songwriters else base.songwriters,
        extra = if (extra.isNotEmpty()) extra else base.extra,
    )
}

fun parseTtml(content: String): ParseResult {
    val doc = try {
        buildDocument(content.removePrefix("\uFEFF").trim())
    } catch (e: Exception) {
        return ParseResult(issues = listOf("This file is not valid TTML (XML)."))
    }
    val root = doc.documentElement
    val all = root.descendants()

    var metadata = ProjectMetadata()
    all.firstOrNull { it.localTag() == "title" }?.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let {
        metadata = metadata.copy(title = it)
    }
    root.getAttribute("xml:lang").trim().takeIf { it.isNotEmpty() }?.let {
        metadata = metadata.copy(language = it)
    }
    val pairs = all
        .filter { it.localTag() == "meta" && it.hasAttribute("key") && it.hasAttribute("value") }
        .map { it.getAttribute("key") to it.getAttribute("value") }
    metadata = metadataFromPairs(pairs, metadata)

    val agents = all.filter { it.localTag() == "agent" && it.hasAttribute("xml:id") }.map { el ->
        val name = el.descendants().firstOrNull { it.localTag() == "name" }?.textContent?.trim()
        Agent(
            id = el.getAttribute("xml:id"),
            type = el.getAttribute("type").ifEmpty { "person" },
            name = name?.takeIf { it.isNotEmpty() },
        )
    }

    val lines = mutableListOf<LyricLine>()
    for (p in all.filter { it.tagName.substringAfter(':').lowercase() == "p" }) {
        val agentId = p.attrLocal("agent")?.removePrefix("#")?.ifEmpty { null } ?: "v1"

        val bgContainer = p.descendants().firstOrNull { it.localTag() == "span" && it.attrLocal("role") == "x-bg" }
        var backgroundText: String? = null
        var backgroundWords: List<WordTiming> = emptyList()
        if (bgContainer != null) {
            backgroundWords = extractTimedWords(bgContainer, null)
            backgroundText = if (backgroundWords.isNotEmpty()) {
                reconstructLineText(backgroundWords)
            } else {
                bgContainer.textContent?.trim()?.ifEmpty { null }
            }
        }

        val words = extractTimedWords(p, bgContainer)
        if (words.isNotEmpty()) {
            lines += LyricLine(
                text = reconstructLineText(words),
                agentId = agentId,
                words = words,
                backgroundText = backgroundText,
                backgroundWords = backgroundWords,
            ).reconciled()
        } else {
            val sb = StringBuilder()
            for (node in p.childNodes.toNodeList()) {
                if (node is Element) {
                    if (node.attrLocal("role") != "x-bg") sb.append(node.textContent ?: "")
                } else if (node.nodeType == Node.TEXT_NODE) {
                    sb.append(node.textContent ?: "")
                }
            }
            val text = sb.toString().trim()
            if (text.isNotEmpty()) {
                lines += LyricLine(
                    text = text,
                    agentId = agentId,
                    begin = if (p.hasAttribute("begin")) parseTtmlTimestamp(p.getAttribute("begin")) else null,
                    end = if (p.hasAttribute("end")) parseTtmlTimestamp(p.getAttribute("end")) else null,
                    backgroundText = backgroundText,
                    backgroundWords = backgroundWords,
                ).reconciled()
            }
        }
    }

    return ParseResult(
        lines = lines,
        metadata = metadata,
        agents = agents,
        hasTimingData = lines.any { it.isTimed },
    )
}
