package org.betterlyrics.composer.project

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.betterlyrics.composer.domain.Agent
import org.betterlyrics.composer.domain.DEFAULT_AGENTS
import org.betterlyrics.composer.domain.LyricLine
import org.betterlyrics.composer.domain.ParseResult
import org.betterlyrics.composer.domain.ProjectMetadata
import org.betterlyrics.composer.domain.parseLyricsText

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

/** The song being edited: metadata, singers and lyric lines. Main thread only. */
class ProjectViewModel(app: Application) : AndroidViewModel(app) {

    var metadata by mutableStateOf(ProjectMetadata()); private set
    var agents by mutableStateOf<List<Agent>>(DEFAULT_AGENTS); private set
    var lines by mutableStateOf<List<LyricLine>>(emptyList()); private set
    var message by mutableStateOf<String?>(null); private set

    fun dismissMessage() {
        message = null
    }

    fun updateLineText(id: String, text: String) {
        lines = lines.map { if (it.id == id) it.withText(text) else it }
    }

    fun deleteLine(id: String) {
        lines = lines.filterNot { it.id == id }
    }

    fun addLine() {
        lines = lines + LyricLine(text = "")
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
        val skipped = if (result.issues.isEmpty()) "" else ", ${result.issues.size} skipped"
        message = "Imported ${result.lines.size} lines$skipped"
    }
}
