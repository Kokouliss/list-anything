package com.kokouliss.listanything.feature.lists

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.MAX_VOICE_SECONDS
import com.kokouliss.listanything.core.ui.component.AppIcons
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The voice note chip of the item sheet: records one while the item has none, afterwards plays it
 * or takes it away. [onError] gets a message to show when the microphone is refused or fails.
 */
@Composable
internal fun VoiceNoteChip(
    item: ListItem,
    isPlaying: Boolean,
    onAction: (ListDetailAction) -> Unit,
    onError: (message: Int) -> Unit,
) {
    val seconds = item.voiceSeconds
    if (seconds == null) {
        RecordChip(
            onRecorded = { path, length -> onAction(ListDetailAction.SetVoiceNote(item.id, path, length)) },
            onError = onError,
        )
    } else {
        val length = DateUtils.formatElapsedTime(seconds.toLong())
        HasChip(
            icon = if (isPlaying) AppIcons.Stop else AppIcons.Mic,
            label = length,
            description = stringResource(R.string.feature_lists_voice_note_description, item.text, length),
            onClick = {
                onAction(if (isPlaying) ListDetailAction.StopVoiceNote else ListDetailAction.PlayVoiceNote(item.id))
            },
            removeLabel = stringResource(R.string.feature_lists_remove_voice),
            onRemove = { onAction(ListDetailAction.RemoveVoiceNote(item.id)) },
        )
    }
}

@Composable
private fun RecordChip(onRecorded: (path: String, seconds: Int) -> Unit, onError: (message: Int) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    // Seconds on the chip while recording; -1 when not.
    var elapsed by remember { mutableIntStateOf(-1) }
    val isRecording = elapsed >= 0
    val finish: () -> Unit = {
        elapsed = -1
        recorder.stop()?.let { length -> onRecorded(recorder.file.path, length) }
    }
    val start: () -> Unit = {
        try {
            recorder.start()
            elapsed = 0
        } catch (e: SecurityException) {
            onError(R.string.feature_lists_mic_denied)
        } catch (e: Exception) {
            // Another app holds the microphone, or the recorder refused the format.
            onError(R.string.feature_lists_voice_failed)
        }
    }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else onError(R.string.feature_lists_mic_denied)
    }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (elapsed < MAX_VOICE_SECONDS) {
                delay(1_000)
                elapsed++
            }
            finish()
        }
    }
    // Closing the sheet mid-recording drops the recording rather than saving half a sentence.
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    if (isRecording) {
        DoChip(
            AppIcons.Stop,
            stringResource(R.string.feature_lists_recording, DateUtils.formatElapsedTime(elapsed.toLong())),
            onClick = finish,
        )
    } else {
        DoChip(AppIcons.Mic, stringResource(R.string.feature_lists_record_voice), onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                start()
            } else {
                askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }
        })
    }
}

/** Records speech into one small AAC file in the cache. One file is enough: a note is stored as soon as it ends. */
internal class VoiceRecorder(private val context: Context) {
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
internal fun PlayAudio(audio: ByteArray, onDone: () -> Unit) {
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
