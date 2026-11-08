package org.betterlyrics.composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.betterlyrics.composer.audio.PlayerViewModel
import org.betterlyrics.composer.domain.LyricLine
import org.betterlyrics.composer.domain.SyncMode
import org.betterlyrics.composer.domain.formatTime
import org.betterlyrics.composer.domain.nextSyncableIndex
import org.betterlyrics.composer.domain.splitIntoWords
import org.betterlyrics.composer.domain.syncProgress
import org.betterlyrics.composer.project.ProjectViewModel

@Composable
fun SyncScreen(project: ProjectViewModel, player: PlayerViewModel, modifier: Modifier = Modifier) {
    if (project.lines.isEmpty() || player.track == null) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(
                if (project.lines.isEmpty()) "Add lyrics on the Edit tab first." else "Load a song on the Import tab first.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val cursor = project.cursor
    val progress = remember(project.lines, project.syncMode) { syncProgress(project.lines, project.syncMode) }
    val finished = remember(project.lines, cursor) { nextSyncableIndex(project.lines, cursor.lineIndex) == null }

    LaunchedEffect(cursor.lineIndex) {
        val last = project.lines.lastIndex
        if (last >= 0) listState.animateScrollToItem(cursor.lineIndex.coerceIn(0, last))
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = project.syncMode == SyncMode.Line,
                onClick = { project.setSyncMode(SyncMode.Line) },
                label = { Text("Line") },
            )
            FilterChip(
                selected = project.syncMode == SyncMode.Word,
                onClick = { project.setSyncMode(SyncMode.Word) },
                label = { Text("Word") },
            )
            Text(
                "${progress.first}/${progress.second}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(formatTime(player.positionMs / 1000.0), style = MaterialTheme.typography.labelLarge)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(project.lines, key = { _, line -> line.id }) { index, line ->
                SyncLineRow(
                    line = line,
                    isCurrent = index == cursor.lineIndex && !finished,
                    wordIndex = cursor.wordIndex,
                    mode = project.syncMode,
                    onClick = { project.selectLine(index) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = { player.skip(-5_000) }) { Icon(Icons.Default.Replay5, "Back 5 seconds") }
            FilledIconButton(onClick = player::togglePlay, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                )
            }
            IconButton(onClick = { player.skip(5_000) }) { Icon(Icons.Default.Forward5, "Forward 5 seconds") }
        }

        TapPad(
            enabled = !finished,
            label = when {
                finished -> "All lines stamped"
                project.syncMode == SyncMode.Line -> "TAP: next line"
                else -> "TAP: next word"
            },
            onTap = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                project.tap(player.currentSeconds())
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    project.undo()?.let { player.seekTo(((it - 1.0).coerceAtLeast(0.0) * 1000).toLong()) }
                },
                enabled = project.canUndo,
                modifier = Modifier.weight(1f),
            ) { Text("Undo") }
            OutlinedButton(
                onClick = { project.endLine(player.currentSeconds()) },
                modifier = Modifier.weight(1f),
            ) { Text("End line here") }
        }
    }
}

@Composable
private fun SyncLineRow(line: LyricLine, isCurrent: Boolean, wordIndex: Int, mode: SyncMode, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val normal = MaterialTheme.colorScheme.onSurface
    val shaded = if (isCurrent) Modifier.background(primary.copy(alpha = 0.15f)) else Modifier
    val bounds = line.mainBounds()

    Column(
        Modifier.fillMaxWidth().then(shaded).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (isCurrent && mode == SyncMode.Word) {
            val slots = splitIntoWords(line.text)
            Text(
                buildAnnotatedString {
                    slots.forEachIndexed { i, slot ->
                        val style = when {
                            i < wordIndex -> SpanStyle(color = primary)
                            i == wordIndex -> SpanStyle(color = normal, fontWeight = FontWeight.Bold)
                            else -> SpanStyle(color = normal)
                        }
                        withStyle(style) { append(slot) }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Text(
                line.text.replace("|", "").ifBlank { "..." },
                style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (bounds != null) primary else normal,
            )
        }
        if (bounds != null) {
            Text(
                "${formatTime(bounds.begin)} to ${formatTime(bounds.end)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Fires on press, not release, so the stamp lands as close to the beat as possible. */
@Composable
private fun TapPad(enabled: Boolean, label: String, onTap: () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(enabled) {
                detectTapGestures(onPress = {
                    if (enabled) currentOnTap()
                    tryAwaitRelease()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
