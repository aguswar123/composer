package org.betterlyrics.composer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private fun twoWords() = LyricLine(
        text = "a b",
        words = listOf(WordTiming("a ", 1.0, 2.0), WordTiming("b", 2.0, 3.0)),
    )

    @Test
    fun sharedBoundaryMovesBothWords() {
        val moved = twoWords().withEdge(0, EdgeSide.End, 2.4, 0.0, 10.0)
        assertEquals(2.4, moved.words[0].end, 1e-6)
        assertEquals(2.4, moved.words[1].begin, 1e-6)
    }

    @Test
    fun movingNextWordBeginIsTheSameBoundary() {
        val moved = twoWords().withEdge(1, EdgeSide.Begin, 1.5, 0.0, 10.0)
        assertEquals(1.5, moved.words[0].end, 1e-6)
        assertEquals(1.5, moved.words[1].begin, 1e-6)
    }

    @Test
    fun keepsMinimumDuration() {
        val moved = twoWords().withEdge(0, EdgeSide.End, 0.0, 0.0, 10.0)
        assertEquals(1.0 + MIN_EDIT_DURATION, moved.words[0].end, 1e-6)
    }

    @Test
    fun lastWordEndIsClampedToNextLine() {
        val moved = twoWords().withEdge(1, EdgeSide.End, 99.0, 0.0, 5.0)
        assertEquals(5.0, moved.words[1].end, 1e-6)
    }

    @Test
    fun firstWordBeginIsClampedToPreviousLine() {
        val moved = twoWords().withEdge(0, EdgeSide.Begin, -4.0, 0.5, 10.0)
        assertEquals(0.5, moved.words[0].begin, 1e-6)
    }

    @Test
    fun unlinkedWordsDoNotCrossEachOther() {
        val gap = LyricLine(
            text = "a b",
            words = listOf(WordTiming("a ", 1.0, 2.0), WordTiming("b", 2.5, 3.0)),
        )
        val moved = gap.withEdge(0, EdgeSide.End, 9.0, 0.0, 10.0)
        assertEquals(2.5, moved.words[0].end, 1e-6)
        assertEquals(2.5, moved.words[1].begin, 1e-6)
        assertTrue(!isLinkedToPrevious(gap.words, 1))
    }

    @Test
    fun lineEdgesAreClamped() {
        val line = LyricLine(text = "x", begin = 1.0, end = 3.0)
        assertEquals(3.0 - MIN_EDIT_DURATION, line.withEdge(null, EdgeSide.Begin, 5.0, 0.0, 10.0).begin!!, 1e-6)
        assertEquals(10.0, line.withEdge(null, EdgeSide.End, 50.0, 0.0, 10.0).end!!, 1e-6)
    }

    @Test
    fun edgeTimeReadsWordOrLine() {
        assertEquals(2.0, twoWords().edgeTime(0, EdgeSide.End)!!, 1e-6)
        assertEquals(1.0, LyricLine(text = "x", begin = 1.0, end = 2.0).edgeTime(null, EdgeSide.Begin)!!, 1e-6)
    }
}
