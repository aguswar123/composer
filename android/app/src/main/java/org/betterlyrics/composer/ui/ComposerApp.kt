package org.betterlyrics.composer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.betterlyrics.composer.audio.PlayerViewModel
import org.betterlyrics.composer.project.ProjectViewModel

/** The four steps of the Composer workflow. */
enum class Step(val label: String, val icon: ImageVector, val todo: String) {
    Import("Import", Icons.Default.AudioFile, ""),
    Edit("Edit", Icons.Default.Edit, ""),
    Sync("Sync", Icons.Default.Timer, "Tap along with the music to stamp each word."),
    Export("Export", Icons.Default.Share, "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerApp(player: PlayerViewModel = viewModel(), project: ProjectViewModel = viewModel()) {
    var step by remember { mutableStateOf(Step.Import) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Composer") }) },
        bottomBar = {
            NavigationBar {
                Step.entries.forEach {
                    NavigationBarItem(
                        selected = it == step,
                        onClick = { step = it },
                        icon = { Icon(it.icon, contentDescription = it.label) },
                        label = { Text(it.label) }
                    )
                }
            }
        }
    ) { padding ->
        val body = Modifier.padding(padding)
        val durationSec = player.durationMs / 1000.0
        when (step) {
            Step.Import -> ImportScreen(player, body)
            Step.Edit -> EditScreen(project, durationSec, body)
            Step.Export -> ExportScreen(project, durationSec, body)
            Step.Sync -> Placeholder(step, body)
        }
    }
}

@Composable
private fun Placeholder(step: Step, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(step.label, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Coming soon: ${step.todo}", style = MaterialTheme.typography.bodyMedium)
    }
}
