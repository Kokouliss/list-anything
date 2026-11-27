package com.kokouliss.listanything.core.ui.component

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.kokouliss.listanything.core.domain.model.MAX_VOICE_SECONDS
import java.io.File
import kotlin.math.roundToInt

// Shared by the voice note of a list item and the voice message of a chat.

/** Records speech into one small AAC file in the cache. One file is enough: a note is stored as soon as it ends. */
class VoiceRecorder(private val context: Context) {
    val file = File(context.cacheDir, "voice/note.m4a").apply { parentFile?.mkdirs() }
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        cancel()
        @Suppress("DEPRECATION")
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        recorder = fresh.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Speech quality: two minutes come to about 360 KB, well under the 800 KB the rules allow.
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(24_000)
            setOutputFile(file.path)
            prepare()
            start()
        }
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Ends the recording. Returns its length in whole seconds, or null when nothing usable was recorded. */
    fun stop(): Int? {
        val recorder = recorder ?: return null
        this.recorder = null
        // stop() throws when no audio arrived at all, for example after a tap that ended at once.
        val recorded = try {
            recorder.stop()
            true
        } catch (e: RuntimeException) {
            false
        }
        recorder.release()
        if (!recorded) return null
        return ((SystemClock.elapsedRealtime() - startedAt) / 1_000.0).roundToInt().coerceIn(1, MAX_VOICE_SECONDS)
    }

    fun cancel() {
        recorder?.release()
        recorder = null
    }
}

/** Plays [audio] once and calls [onDone] when it ends or cannot be played. Leaving the screen stops it. */
@Composable
fun PlayAudio(audio: ByteArray, onDone: () -> Unit) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    DisposableEffect(audio) {
        // MediaPlayer reads files, not byte arrays. The next note overwrites this copy.
        val file = File(context.cacheDir, "voice/play.m4a").apply { parentFile?.mkdirs() }
        val player = MediaPlayer()
        try {
            file.writeBytes(audio)
            player.setDataSource(file.path)
            player.setOnCompletionListener { done() }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            // A note another device recorded in a format this one cannot play.
            done()
        }
        onDispose { player.release() }
    }
}
