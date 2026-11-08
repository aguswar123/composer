package org.betterlyrics.composer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    private fun state(vararg texts: String) = SyncState(texts.map { LyricLine(text = it) })

    @Test
    fun splitsWordsAndSyllables() {
        assertEquals(listOf("hel", "lo ", "world"), splitIntoWords("hel|lo world"))
        assertTrue(splitIntoWords("   ").isEmpty())
    }

    @Test
    fun lineModeStampsAndClosesPreviousEnd() {
        var s = state("A", "B", "C")
        s = applyTap(s, SyncMode.Line, 1.0)
        s = applyTap(s, SyncMode.Line, 3.0)
        s = applyTap(s, SyncMode.Line, 5.0)
        s = applyEnd(s, 6.0)
        assertEquals(1.0, s.lines[0].begin!!, 1e-6)
        assertEquals(3.0, s.lines[0].end!!, 1e-6)
        assertEquals(3.0, s.lines[1].begin!!, 1e-6)
        assertEquals(5.0, s.lines[1].end!!, 1e-6)
        assertEquals(6.0, s.lines[2].end!!, 1e-6)
        assertEquals(3, s.cursor.lineIndex)
    }

    @Test
    fun wordModeBuildsWordsAndAdvancesLines() {
        var s = state("Hello big world", "Next")
        s = applyTap(s, SyncMode.Word, 1.0)
        s = applyTap(s, SyncMode.Word, 1.5)
        s = applyTap(s, SyncMode.Word, 2.0)
        assertEquals(1, s.cursor.lineIndex)
        s = applyTap(s, SyncMode.Word, 4.0)
        val words = s.lines[0].words
        assertEquals(listOf("Hello ", "big ", "world"), words.map { it.text })
        assertEquals(1.5, words[0].end, 1e-6)
        assertEquals(4.0, words[2].end, 1e-6)
        assertTrue(s.lines[0].isWordSynced)
    }

    @Test
    fun skipsBlankLines() {
        var s = state("A", "", "B")
        s = applyTap(s, SyncMode.Line, 1.0)
        assertEquals(2, s.cursor.lineIndex)
        assertEquals(false, s.lines[1].isTimed)
    }

    @Test
    fun tapWhenFinishedReturnsSameState() {
        val s = state("A")
        val done = applyTap(s, SyncMode.Line, 1.0)
        assertTrue(applyTap(done, SyncMode.Line, 2.0) === done)
    }

    @Test
    fun progressCountsLinesAndWords() {
        var s = state("a b", "c")
        s = applyTap(s, SyncMode.Line, 1.0)
        assertEquals(1 to 2, syncProgress(s.lines, SyncMode.Line))
        assertEquals(0 to 3, syncProgress(s.lines, SyncMode.Word))
    }

    @Test
    fun fileNameIsSafe() {
        assertEquals("a_b.ttml", ttmlFileName("a/b"))
        assertEquals("lyrics.ttml", ttmlFileName("  "))
    }
}
