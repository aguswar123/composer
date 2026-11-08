package org.betterlyrics.composer.audio

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the ExoPlayer and exposes Compose-observable playback state. Main thread only. */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }

    var track by mutableStateOf<Track?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null); private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerViewModel.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) positionMs = player.duration.coerceAtLeast(0)
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "Could not play this file"
            }
        })
        // Position ticker: ~60 fps while playing, drives the readout and the seek bar.
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) positionMs = player.currentPosition
                delay(16)
            }
        }
    }

    fun load(uri: Uri) {
        error = null
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Provider doesn't offer persistable grants; the grant lasts for this session.
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        positionMs = 0
        durationMs = 0
        viewModelScope.launch {
            track = withContext(Dispatchers.IO) { readTrack(app, uri) }
        }
    }

    private fun readTrack(app: Application, uri: Uri): Track {
        var name = uri.lastPathSegment ?: "audio"
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> name = n }
        }
        var title: String? = null
        var artist: String? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(app, uri)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } catch (_: Exception) {
        } finally {
            mmr.release()
        }
        return Track(uri, name, title, artist)
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
        positionMs = target
    }

    fun skip(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun pause() = player.pause()

    override fun onCleared() {
        player.release()
    }
}
