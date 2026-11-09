package org.betterlyrics.composer.project

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.betterlyrics.composer.domain.Agent
import org.betterlyrics.composer.domain.DEFAULT_AGENTS
import org.betterlyrics.composer.domain.EdgeSide
import org.betterlyrics.composer.domain.LyricLine
import org.betterlyrics.composer.domain.ParseResult
import org.betterlyrics.composer.domain.PendingEnd
import org.betterlyrics.composer.domain.ProjectMetadata
import org.betterlyrics.composer.domain.SyncCursor
import org.betterlyrics.composer.domain.SyncMode
import org.betterlyrics.composer.domain.SyncState
import org.betterlyrics.composer.domain.applyEnd
import org.betterlyrics.composer.domain.applySelect
import org.betterlyrics.composer.domain.applyTap
import org.betterlyrics.composer.domain.edgeTime
import org.betterlyrics.composer.domain.parseLyricsText
import org.betterlyrics.composer.domain.withEdge

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
private const val MAX_HISTORY = 100

private class Snapshot(val state: SyncState, val timeSec: Double)

/** The song being edited: metadata, singers, lyric lines and the sync cursor. Main thread only. */
class ProjectViewModel(app: Application) : AndroidViewModel(app) {

    var metadata by mutableStateOf(ProjectMetadata()); private set
    var agents by mutableStateOf<List<Agent>>(DEFAULT_AGENTS); private set
    var lines by mutableStateOf<List<LyricLine>>(emptyList()); private set
    var message by mutableStateOf<String?>(null); private set

    var syncMode by mutableStateOf(SyncMode.Line); private set
    var cursor by mutableStateOf(SyncCursor()); private set
    var canUndo by mutableStateOf(false); private set
    var timelineLine by mutableIntStateOf(0); private set

    private var pending: PendingEnd? = null
    private val history = ArrayDeque<Snapshot>()

    fun dismissMessage() {
        message = null
    }

    // -- Editing ---------------------------------------------------------------

    fun updateLineText(id: String, text: String) {
        lines = lines.map { if (it.id == id) it.withText(text) else it }
        resetHistory()
    }

    fun deleteLine(id: String) {
        lines = lines.filterNot { it.id == id }
        resetHistory()
    }

    fun addLine() {
        lines = lines + LyricLine(text = "")
        resetHistory()
    }

    /** Replaces all lyrics from pasted text (plain, LRC or TTML). */
    fun replaceFromText(text: String, fallbackDurationSec: Double?) {
        applyResult(parseLyricsText("pasted", text, fallbackDurationSec))
    }

    fun importFile(uri: Uri, fallbackDurationSec: Double?) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    var name = uri.lastPathSegment ?: "lyrics"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0)?.let { n -> name = n }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    when {
                        bytes == null -> null
                        bytes.size > MAX_IMPORT_BYTES -> name to null
                        else -> name to bytes.toString(Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            when {
                loaded == null -> message = "Could not read that file."
                loaded.second == null -> message = "That file is too large (limit 5 MB)."
                else -> applyResult(parseLyricsText(loaded.first, loaded.second!!, fallbackDurationSec))
            }
        }
    }

    private fun applyResult(result: ParseResult) {
        if (result.lines.isEmpty()) {
            message = result.issues.firstOrNull() ?: "No lyrics found."
            return
        }
        lines = result.lines
        if (result.agents.isNotEmpty()) agents = result.agents
        val m = result.metadata
        metadata = metadata.copy(
            title = m.title.ifEmpty { metadata.title },
            artists = m.artists.ifEmpty { metadata.artists },
            album = m.album.ifEmpty { metadata.album },
            language = m.language ?: metadata.language,
            isrc = m.isrc ?: metadata.isrc,
            songwriters = m.songwriters.ifEmpty { metadata.songwriters },
            extra = m.extra.ifEmpty { metadata.extra },
        )
        cursor = SyncCursor()
        resetHistory()
        val skipped = if (result.issues.isEmpty()) "" else ", ${result.issues.size} skipped"
        message = "Imported ${result.lines.size} lines$skipped"
    }

    // -- Sync ------------------------------------------------------------------

    fun changeSyncMode(mode: SyncMode) {
        if (mode == syncMode) return
        syncMode = mode
        cursor = SyncCursor(cursor.lineIndex, 0)
        pending = null
    }

    fun selectLine(index: Int) = commit(applySelect(currentState(), index))

    /** One tap at [timeSec]: stamps the next line or word and closes the previous end. */
    fun tap(timeSec: Double) {
        val before = currentState()
        val after = applyTap(before, syncMode, timeSec)
        if (after === before) return
        push(before, timeSec)
        commit(after)
    }

    /** Ends the previous line or word at [timeSec] without starting a new one. */
    fun endLine(timeSec: Double) {
        val before = currentState()
        if (before.pending == null) return
        push(before, timeSec)
        commit(applyEnd(before, timeSec))
    }

    /** Reverts the last tap. Returns the time that tap was made at, so the caller can seek back. */
    fun undo(): Double? {
        val snapshot = history.removeLastOrNull() ?: return null
        commit(snapshot.state)
        canUndo = history.isNotEmpty()
        return snapshot.timeSec
    }

    private fun currentState() = SyncState(lines, cursor, pending)

    private fun commit(state: SyncState) {
        lines = state.lines
        cursor = state.cursor
        pending = state.pending
    }

    private fun push(state: SyncState, timeSec: Double) {
        history.addLast(Snapshot(state, timeSec))
        if (history.size > MAX_HISTORY) history.removeFirst()
        canUndo = true
    }

    private fun resetHistory() {
        history.clear()
        canUndo = false
        pending = null
    }

    // -- Timeline --------------------------------------------------------------

    fun selectTimelineLine(index: Int) {
        timelineLine = index.coerceIn(0, maxOf(0, lines.size - 1))
    }

    /** Where an edit of line [index] may go: after the previous timed line, before the next one. */
    private fun limitsFor(index: Int, durationSec: Double): Pair<Double, Double> {
        val lower = lines.take(index).asReversed().firstNotNullOfOrNull { it.effectiveBounds()?.end } ?: 0.0
        val upper = lines.drop(index + 1).firstNotNullOfOrNull { it.effectiveBounds()?.begin }
            ?: (if (durationSec > 0) durationSec else 3_600.0)
        return lower to upper
    }

    /** Drags one word or line edge to [time] (seconds). */
    fun moveEdge(lineId: String, wordIndex: Int?, side: EdgeSide, time: Double, durationSec: Double) {
        val index = lines.indexOfFirst { it.id == lineId }
        if (index < 0) return
        val (lower, upper) = limitsFor(index, durationSec)
        lines = lines.mapIndexed { i, line ->
            if (i == index) line.withEdge(wordIndex, side, time, lower, upper) else line
        }
        resetHistory()
    }

    fun nudgeEdge(lineId: String, wordIndex: Int?, side: EdgeSide, deltaSec: Double, durationSec: Double) {
        val line = lines.firstOrNull { it.id == lineId } ?: return
        val current = line.edgeTime(wordIndex, side) ?: return
        moveEdge(lineId, wordIndex, side, current + deltaSec, durationSec)
    }

    // -- Saving ----------------------------------------------------------------

    fun saveText(uri: Uri, text: String) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            message = if (ok) "Saved." else "Could not save the file."
        }
    }
}
