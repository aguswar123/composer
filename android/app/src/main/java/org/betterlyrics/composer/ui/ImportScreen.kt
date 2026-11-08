package org.betterlyrics.composer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.betterlyrics.composer.audio.PlayerViewModel
import org.betterlyrics.composer.audio.formatTime

@Composable
fun ImportScreen(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val pick = { picker.launch(arrayOf("audio/*")) }
    val track = vm.track

    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.AudioFile, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (track == null) {
            Text("Import a song", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("MP3, WAV, M4A, OGG or FLAC", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = pick) { Text("Choose audio file") }
        } else {
            Text(
                track.displayTitle, style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            track.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(24.dp))
            PlayerControls(vm)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = pick) { Text("Change file") }
        }

        vm.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerControls(vm: PlayerViewModel) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val duration = vm.durationMs.coerceAtLeast(1)
    val shownMs = if (dragging) (dragValue * duration).toLong() else vm.positionMs

    Slider(
        value = if (dragging) dragValue else (vm.positionMs.toFloat() / duration).coerceIn(0f, 1f),
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = { vm.seekTo((dragValue * duration).toLong()); dragging = false },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(shownMs), style = MaterialTheme.typography.labelMedium)
        Text(formatTime(vm.durationMs), style = MaterialTheme.typography.labelMedium)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconButton(onClick = { vm.skip(-5_000) }) { Icon(Icons.Default.Replay5, "Back 5 seconds") }
        FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(64.dp)) {
            Icon(
                if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (vm.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { vm.skip(5_000) }) { Icon(Icons.Default.Forward5, "Forward 5 seconds") }
    }
}
