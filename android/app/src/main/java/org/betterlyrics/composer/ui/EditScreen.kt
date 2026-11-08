package org.betterlyrics.composer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import org.betterlyrics.composer.domain.LyricLine
import org.betterlyrics.composer.domain.formatTime
import org.betterlyrics.composer.project.ProjectViewModel

private fun timingLabel(line: LyricLine): String {
    val bounds = line.mainBounds()
    return when {
        line.isWordSynced -> "Word-synced, ${formatTime(bounds?.begin ?: 0.0)}"
        bounds != null -> "Line-synced, ${formatTime(bounds.begin)}"
        else -> "Not synced"
    }
}

@Composable
fun EditScreen(project: ProjectViewModel, durationSec: Double, modifier: Modifier = Modifier) {
    var showPaste by remember { mutableStateOf(false) }
    val fallbackDuration = durationSec.takeIf { it > 0 }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) project.importFile(uri, fallbackDuration)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import file") }
            OutlinedButton(onClick = { showPaste = true }) { Text("Paste") }
            OutlinedButton(onClick = project::addLine) { Text("Add line") }
        }

        project.message?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = project::dismissMessage) { Text("Dismiss") }
            }
        }

        if (project.lines.isEmpty()) {
            Text(
                "No lyrics yet. Import a .txt, .lrc or .ttml file, paste lyrics, or add a line.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(project.lines, key = { it.id }) { line ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        OutlinedTextField(
                            value = line.text,
                            onValueChange = { project.updateLineText(line.id, it) },
                            modifier = Modifier.weight(1f),
                            supportingText = { Text(timingLabel(line)) },
                        )
                        IconButton(onClick = { project.deleteLine(line.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete line")
                        }
                    }
                }
            }
        }
    }

    if (showPaste) {
        PasteDialog(
            onDismiss = { showPaste = false },
            onConfirm = {
                project.replaceFromText(it, fallbackDuration)
                showPaste = false
            },
        )
    }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste lyrics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 10,
                    placeholder = { Text("Plain text, LRC or TTML") },
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) {
                    Text("From clipboard")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Replace lyrics") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
