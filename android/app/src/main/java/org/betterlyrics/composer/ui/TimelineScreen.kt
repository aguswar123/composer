package org.betterlyrics.composer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import org.betterlyrics.composer.audio.PlayerViewModel
import org.betterlyrics.composer.domain.EdgeSide
import org.betterlyrics.composer.domain.edgeTime
import org.betterlyrics.composer.domain.formatTime
import org.betterlyrics.composer.domain.isLinkedToPrevious
import org.betterlyrics.composer.project.ProjectViewModel

private val HANDLE_TOUCH = 28.dp
private val LANE_TOP = 48.dp
private val WORD_HEIGHT = 64.dp
private const val NUDGE_SECONDS = 0.01

private fun formatClock(totalSeconds: Int): String =
    String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

/**
 * Timeline editor: pick a line, then drag the word (or line) boundaries.
 * Boundaries shared by touching words move together. Tap the overview strip to change line,
 * tap the lane to seek.
 */
@Composable
fun TimelineScreen(project: ProjectViewModel, player: PlayerViewModel, modifier: Modifier = Modifier) {
    val lines = project.lines
    val timedIndexes = remember(lines) { lines.indices.filter { lines[it].isTimed } }
    if (timedIndexes.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(
                "No timed lines yet. Stamp lines on the Sync tab, or import an .lrc or .ttml file.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val index = timedIndexes.firstOrNull { it >= project.timelineLine } ?: timedIndexes.last()
    val line = lines[index]
    val bounds = line.mainBounds()
    val position = timedIndexes.indexOf(index)

    val durationSec = player.durationMs / 1000.0
    val totalSec = remember(lines, durationSec) {
        max(durationSec, lines.maxOf { it.effectiveBounds()?.end ?: 0.0 } + 2.0).coerceAtLeast(5.0)
    }

    val dens = LocalDensity.current.density
    // Compose cannot lay out a single child wider than about 60000 px, so cap the zoom.
    val maxPps = min(400f, 50_000f / dens / totalSec.toFloat())
    val minPps = min(20f, maxPps)
    var zoom by remember { mutableFloatStateOf(120f) }
    val pps = zoom.coerceIn(minPps, maxPps)

    val scroll = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    var selectedWord by remember(line.id) { mutableIntStateOf(0) }
    val wordIndex: Int? = if (line.isWordSynced) selectedWord.coerceIn(0, line.words.lastIndex) else null

    LaunchedEffect(line.id) {
        val begin = bounds?.begin ?: 0.0
        scroll.animateScrollTo(max(0f, (begin * pps * dens).toFloat() - viewportPx * 0.2f).toInt())
    }

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { project.selectTimelineLine(timedIndexes[position - 1]) },
                enabled = position > 0,
            ) { Icon(Icons.Default.ChevronLeft, "Previous line") }
            Column(Modifier.weight(1f)) {
                Text("Line ${index + 1} of ${lines.size}", style = MaterialTheme.typography.labelLarge)
                Text(
                    line.text.replace("|", "").ifBlank { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { project.selectTimelineLine(timedIndexes[position + 1]) },
                enabled = position < timedIndexes.lastIndex,
            ) { Icon(Icons.Default.ChevronRight, "Next line") }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .onSizeChanged { viewportPx = it.width }
                .horizontalScroll(scroll),
        ) {
            Box(Modifier.width((totalSec * pps).dp).fillMaxHeight()) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(pps, lines) {
                        detectTapGestures { offset ->
                            val t = (offset.x / dens / pps).toDouble()
                            if (offset.y < 40.dp.toPx()) {
                                var best = -1
                                var bestDistance = Double.MAX_VALUE
                                lines.forEachIndexed { li, l ->
                                    val b = l.effectiveBounds() ?: return@forEachIndexed
                                    val d = when {
                                        t < b.begin -> b.begin - t
                                        t > b.end -> t - b.end
                                        else -> 0.0
                                    }
                                    if (d < bestDistance) {
                                        best = li
                                        bestDistance = d
                                    }
                                }
                                if (best >= 0) project.selectTimelineLine(best)
                            } else {
                                player.seekTo(max(0L, (t * 1000).toLong()))
                            }
                        }
                    },
                ) {
                    val perSecond = pps * dens
                    val tickStep = if (pps >= 60f) 1 else 5
                    for (s in 0..totalSec.toInt() step tickStep) {
                        val x = s * perSecond
                        val major = s % 5 == 0
                        drawLine(
                            labelColor,
                            Offset(x, 0f),
                            Offset(x, if (major) 12.dp.toPx() else 6.dp.toPx()),
                        )
                        if (major) {
                            drawText(
                                measurer,
                                formatClock(s),
                                Offset(x + 3f, 0f),
                                style = TextStyle(fontSize = 10.sp, color = labelColor),
                            )
                        }
                    }
                    val laneTop = 22.dp.toPx()
                    val laneHeight = 14.dp.toPx()
                    drawRect(labelColor.copy(alpha = 0.08f), Offset(0f, laneTop), Size(size.width, laneHeight))
                    lines.forEachIndexed { li, l ->
                        val b = l.effectiveBounds() ?: return@forEachIndexed
                        drawRect(
                            if (li == index) primary else onSurface.copy(alpha = 0.3f),
                            Offset((b.begin * perSecond).toFloat(), laneTop),
                            Size(max(2f, ((b.end - b.begin) * perSecond).toFloat()), laneHeight),
                        )
                    }
                }

                if (line.isWordSynced) {
                    line.words.forEachIndexed { i, word ->
                        WordBlock(
                            text = word.text.trim(),
                            begin = word.begin,
                            end = word.end,
                            pps = pps,
                            selected = i == wordIndex,
                            onClick = {
                                selectedWord = i
                                player.seekTo((word.begin * 1000).toLong())
                            },
                        )
                    }
                    line.words.forEachIndexed { i, word ->
                        if (!isLinkedToPrevious(line.words, i)) {
                            EdgeHandle(word.begin, pps, dens) { t ->
                                project.moveEdge(line.id, i, EdgeSide.Begin, t, durationSec)
                            }
                        }
                        EdgeHandle(word.end, pps, dens) { t ->
                            project.moveEdge(line.id, i, EdgeSide.End, t, durationSec)
                        }
                    }
                } else if (bounds != null) {
                    WordBlock(
                        text = line.text.replace("|", ""),
                        begin = bounds.begin,
                        end = bounds.end,
                        pps = pps,
                        selected = true,
                        onClick = { player.seekTo((bounds.begin * 1000).toLong()) },
                    )
                    EdgeHandle(bounds.begin, pps, dens) { t ->
                        project.moveEdge(line.id, null, EdgeSide.Begin, t, durationSec)
                    }
                    EdgeHandle(bounds.end, pps, dens) { t ->
                        project.moveEdge(line.id, null, EdgeSide.End, t, durationSec)
                    }
                }

                Playhead(player, pps, dens, scroll, viewportPx)
            }
        }

        if (maxPps > minPps) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = pps,
                    onValueChange = { zoom = it },
                    valueRange = minPps..maxPps,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
        }

        val startTime = line.edgeTime(wordIndex, EdgeSide.Begin)
        val endTime = line.edgeTime(wordIndex, EdgeSide.End)
        Text(
            (if (wordIndex != null) "Word ${wordIndex + 1}: " else "Line: ") +
                "${formatTime(startTime ?: 0.0)} to ${formatTime(endTime ?: 0.0)}",
            style = MaterialTheme.typography.labelLarge,
        )

        val nudge: (EdgeSide, Double) -> Unit = { side, delta ->
            project.nudgeEdge(line.id, wordIndex, side, delta, durationSec)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nudge(EdgeSide.Begin, -NUDGE_SECONDS) }, modifier = Modifier.weight(1f)) {
                Text("Start -10ms")
            }
            OutlinedButton(onClick = { nudge(EdgeSide.Begin, NUDGE_SECONDS) }, modifier = Modifier.weight(1f)) {
                Text("Start +10ms")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nudge(EdgeSide.End, -NUDGE_SECONDS) }, modifier = Modifier.weight(1f)) {
                Text("End -10ms")
            }
            OutlinedButton(onClick = { nudge(EdgeSide.End, NUDGE_SECONDS) }, modifier = Modifier.weight(1f)) {
                Text("End +10ms")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = {
                val begin = bounds?.begin ?: 0.0
                player.seekTo(max(0L, ((begin - 0.3) * 1000).toLong()))
                if (!player.isPlaying) player.togglePlay()
            }) { Text("Play line") }
            FilledIconButton(onClick = player::togglePlay, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                )
            }
            ClockText(player)
        }
    }
}

/** Reads the playback position itself, so only this small text redraws every frame. */
@Composable
private fun ClockText(player: PlayerViewModel) {
    Text(formatTime(player.positionMs / 1000.0), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun WordBlock(text: String, begin: Double, end: Double, pps: Float, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .offset(x = (begin * pps).dp, y = LANE_TOP)
            .width(((end - begin) * pps).dp.coerceAtLeast(2.dp))
            .height(WORD_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(primary.copy(alpha = if (selected) 0.45f else 0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * A draggable boundary. It tracks the total drag from where the finger went down, so the
 * edge follows the finger even though the clamped value comes back through state a frame later.
 */
@Composable
private fun EdgeHandle(time: Double, pps: Float, dens: Float, onMove: (Double) -> Unit) {
    val currentTime by rememberUpdatedState(time)
    val currentPps by rememberUpdatedState(pps)
    val currentOnMove by rememberUpdatedState(onMove)
    val x: Dp = (time * pps).dp - HANDLE_TOUCH / 2
    Box(
        Modifier
            .offset(x = x, y = LANE_TOP)
            .width(HANDLE_TOUCH)
            .height(WORD_HEIGHT)
            .pointerInput(Unit) {
                var base = 0.0
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        base = currentTime
                        travelled = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        travelled += amount
                        currentOnMove(base + travelled / dens / currentPps)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun Playhead(player: PlayerViewModel, pps: Float, dens: Float, scroll: ScrollState, viewportPx: Int) {
    val x = (player.positionMs / 1000.0 * pps).dp
    Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))

    // While playing, keep the playhead in view.
    LaunchedEffect(player.isPlaying, pps, viewportPx) {
        if (!player.isPlaying) return@LaunchedEffect
        snapshotFlow { player.positionMs }.collect { ms ->
            val px = (ms / 1000.0 * pps * dens).toInt()
            if (px < scroll.value || px > scroll.value + viewportPx * 0.85f) {
                scroll.scrollTo(max(0, (px - viewportPx * 0.15f).toInt()))
            }
        }
    }
}
