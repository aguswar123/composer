package org.betterlyrics.composer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test
    fun formatsTime() {
        assertEquals("1:15.500", formatTime(75.5))
        assertEquals("0:00.000", formatTime(-1.0))
    }

    @Test
    fun ttmlRoundTripKeepsWordsAndBounds() {
        val line = LyricLine(
            text = "Hello world",
            words = listOf(WordTiming("Hello ", 1.0, 1.5), WordTiming("world", 1.5, 2.25)),
            backgroundText = "ooh",
            backgroundWords = listOf(WordTiming("ooh", 2.0, 2.5)),
        )
        val ttml = generateProjectTtml(
            ProjectMetadata(title = "Song", artists = listOf("Artist")),
            DEFAULT_AGENTS,
            listOf(line),
            30.0,
        )
        val parsed = parseTtml(ttml)
        assertEquals("Song", parsed.metadata.title)
        assertEquals(listOf("Artist"), parsed.metadata.artists)
        assertEquals(1, parsed.lines.size)
        val back = parsed.lines[0]
        assertEquals(2, back.words.size)
        assertEquals("Hello world", back.text)
        assertEquals(1.0, back.effectiveBounds()!!.begin, 1e-6)
        assertEquals(2.5, back.effectiveBounds()!!.end, 1e-6)
        assertEquals("ooh", back.backgroundText)
    }

    @Test
    fun untimedProjectExportsNothing() {
        assertEquals("", generateProjectTtml(ProjectMetadata(), DEFAULT_AGENTS, listOf(LyricLine(text = "Hi")), 0.0))
    }

    @Test
    fun parsesLineLrc() {
        val result = parseLrc("[ti:Song]\n[00:01.00]Hello\n[00:03.50]World", fallbackDuration = 10.0)
        assertEquals("Song", result.metadata.title)
        assertEquals(2, result.lines.size)
        assertEquals(1.0, result.lines[0].begin!!, 1e-6)
        assertEquals(3.5, result.lines[0].end!!, 1e-6)
        assertEquals(10.0, result.lines[1].end!!, 1e-6)
    }

    @Test
    fun parsesEnhancedLrcWords() {
        val result = parseLrc("[00:01.00]<00:01.00>Hello <00:02.00>world\n[00:04.00]Next")
        val first = result.lines[0]
        assertTrue(first.isWordSynced)
        assertEquals(2, first.words.size)
        assertEquals(4.0, first.words[1].end, 1e-6)
    }

    @Test
    fun parsesPlainTextTrimmingOuterBlankLines() {
        val result = parseTxt("\n\nOne\n\nTwo\n\n")
        assertEquals(listOf("One", "", "Two"), result.lines.map { it.text })
    }

    @Test
    fun editingWordSyncedLineFallsBackToLineTiming() {
        val line = LyricLine(text = "a b", words = listOf(WordTiming("a ", 1.0, 1.5), WordTiming("b", 1.5, 2.0)))
        val edited = line.withText("a c")
        assertTrue(edited.isLineSynced)
        assertEquals(1.0, edited.begin!!, 1e-6)
        assertEquals(2.0, edited.end!!, 1e-6)
    }
}
