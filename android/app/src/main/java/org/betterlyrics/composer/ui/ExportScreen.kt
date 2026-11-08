package org.betterlyrics.composer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.betterlyrics.composer.domain.generateProjectTtml
import org.betterlyrics.composer.project.ProjectViewModel

/** Read-only TTML preview with a copy button. Saving and sharing come with the sync phase. */
@Composable
fun ExportScreen(project: ProjectViewModel, durationSec: Double, modifier: Modifier = Modifier) {
    val ttml = remember(project.metadata, project.agents, project.lines, durationSec) {
        generateProjectTtml(project.metadata, project.agents, project.lines, durationSec)
    }
    val clipboard = LocalClipboardManager.current

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ttml.isEmpty()) {
            Text(
                "Nothing to export yet. TTML needs at least one timed line. " +
                    "Import an .lrc or .ttml file on the Edit tab, or sync lines once the Sync tab is built.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TTML preview", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
