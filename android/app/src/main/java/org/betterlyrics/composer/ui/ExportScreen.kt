package org.betterlyrics.composer.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.betterlyrics.composer.domain.generateProjectTtml
import org.betterlyrics.composer.domain.ttmlFileName
import org.betterlyrics.composer.project.ProjectViewModel

/** The 400 KB cap keeps share-sheet text under Android's binder limit. */
private const val MAX_SHARE_CHARS = 400_000

@Composable
fun ExportScreen(project: ProjectViewModel, durationSec: Double, modifier: Modifier = Modifier) {
    val ttml = remember(project.metadata, project.agents, project.lines, durationSec) {
        generateProjectTtml(project.metadata, project.agents, project.lines, durationSec)
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) project.saveText(uri, ttml)
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        project.message?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = project::dismissMessage) { Text("Dismiss") }
            }
        }

        if (ttml.isEmpty()) {
            Text(
                "Nothing to export yet. TTML needs at least one timed line. " +
                    "Stamp lines on the Sync tab, or import an .lrc or .ttml file on the Edit tab.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("TTML preview", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saver.launch(ttmlFileName(project.metadata.title)) }) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, ttmlFileName(project.metadata.title))
                            putExtra(Intent.EXTRA_TEXT, ttml)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    enabled = ttml.length <= MAX_SHARE_CHARS,
                ) { Text("Share") }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(ttml)) }) { Text("Copy") }
            }
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    ttml,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
