package com.kokouliss.listanything.feature.spaces

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kokouliss.listanything.core.domain.model.ChatLook
import com.kokouliss.listanything.core.domain.model.ChatMessage
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.MAX_MESSAGE_LENGTH
import com.kokouliss.listanything.core.domain.model.MAX_VOICE_SECONDS
import com.kokouliss.listanything.core.domain.model.MessageKind
import com.kokouliss.listanything.core.domain.model.OpenChat
import com.kokouliss.listanything.core.domain.model.isOnlyEmoji
import com.kokouliss.listanything.core.ui.R as CoreR
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.ConfirmDialog
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.PhotoDialog
import com.kokouliss.listanything.core.ui.component.PlayAudio
import com.kokouliss.listanything.core.ui.component.VoiceRecorder
import com.kokouliss.listanything.core.ui.component.destructiveMenuItemColors
import com.kokouliss.listanything.core.ui.component.rememberBitmap
import com.kokouliss.listanything.core.ui.component.rememberIsOffline
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.theme.chatColors
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.delay

/**
 * The chat of a space: its messages, newest at the bottom, over the bar they are written in.
 * [spaceId] marks the chat as on screen, so the phone stays quiet about messages that land in it.
 */
@Composable
internal fun ChatTab(
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    modifier: Modifier = Modifier,
    spaceId: String? = null,
    now: Long = System.currentTimeMillis(),
) {
    DisposableEffect(spaceId) {
        OpenChat.spaceId = spaceId
        onDispose { OpenChat.spaceId = null }
    }
    val messages = state.messages
    val (fill, onFill) = chatColors(state.look.color)
    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                messages == null -> LoadingBox()
                messages.isEmpty() -> EmptyState(
                    icon = AppIcons.Chat,
                    title = stringResource(R.string.feature_spaces_chat_empty_title),
                    body = stringResource(R.string.feature_spaces_chat_empty_body),
                )
                else -> Messages(state, messages, fill, onFill, now, onAction)
            }
        }
        ChatInput(state.look, fill, onFill, state.overlays.sendingPhoto, onAction)
    }

    state.overlays.photo?.let { photo ->
        PhotoDialog(
            image = photo.image,
            isLoading = photo.isLoading,
            description = stringResource(
                R.string.feature_spaces_chat_photo_description,
                messages?.firstOrNull { it.id == photo.messageId }?.byName.orEmpty(),
            ),
            onClose = { onAction(ChatAction.ClosePhoto) },
        )
    }
    state.overlays.voice?.audio?.let { audio -> PlayAudio(audio, onDone = { onAction(ChatAction.StopVoice) }) }
}

@Composable
private fun Messages(
    state: ChatUiState,
    messages: List<ChatMessage>,
    fill: Color,
    onFill: Color,
    now: Long,
    onAction: (ChatAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val newest = messages.first()
    // A new message pulls the chat down to it, unless the person is reading further up. One's own always does.
    LaunchedEffect(newest.id) {
        if (newest.byId == state.currentUserId || listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val offline = rememberIsOffline()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        // The newest message sits at the bottom, next to where the next one is written.
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val older = messages.getOrNull(index + 1)
            val newer = messages.getOrNull(index - 1)
            val startsDay = older == null || !sameDay(older.atMillis, message.atMillis)
            Column(Modifier.animateItem().fillMaxWidth()) {
                if (startsDay) DayLabel(message.atMillis, now)
                MessageRow(
                    message = message,
                    isMine = message.byId == state.currentUserId,
                    // A run is one person's messages in a row: the name once on top, the face once at the bottom.
                    startsRun = startsDay || older?.byId != message.byId,
                    endsRun = newer == null || newer.byId != message.byId || !sameDay(newer.atMillis, message.atMillis),
                    photo = state.photos[message.byId],
                    fill = fill,
                    onFill = onFill,
                    isPlaying = state.overlays.voice?.messageId == message.id,
                    showsPending = offline && message.isPending,
                    onAction = onAction,
                    onDelete = { deleting = message.id },
                )
            }
        }
        if (state.canLoadMore) {
            item(key = "older") {
                // Reaching the top asks for the next page; each page that arrives may ask again.
                LaunchedEffect(messages.size) { onAction(ChatAction.LoadMore) }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    deleting?.let { messageId ->
        ConfirmDialog(
            title = stringResource(R.string.feature_spaces_chat_delete_message),
            text = stringResource(R.string.feature_spaces_chat_delete_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                deleting = null
                onAction(ChatAction.Delete(messageId))
            },
            onDismiss = { deleting = null },
        )
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}

/** "Today", "Yesterday" or the date, on a slip of its own so the ruled paper does not run through it. */
@Composable
private fun DayLabel(atMillis: Long, now: Long) {
    val context = LocalContext.current
    val day = if (now - atMillis < 2 * DateUtils.DAY_IN_MILLIS && now >= atMillis) {
        DateUtils.getRelativeTimeSpanString(atMillis, now, DateUtils.DAY_IN_MILLIS).toString()
    } else {
        DateUtils.formatDateTime(context, atMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            Text(
                day,
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    isMine: Boolean,
    startsRun: Boolean,
    endsRun: Boolean,
    photo: ImageData?,
    fill: Color,
    onFill: Color,
    isPlaying: Boolean,
    showsPending: Boolean,
    onAction: (ChatAction) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val time = remember(message.atMillis) { DateFormat.getTimeFormat(context).format(Date(message.atMillis)) }
    // One to three emoji and nothing else stand large on the paper, without a bubble.
    val isBig = message.kind == null && isOnlyEmoji(message.text)
    val ink = when {
        isBig -> MaterialTheme.colorScheme.onSurface
        isMine -> onFill
        else -> MaterialTheme.colorScheme.onSurface
    }
    var menuOpen by remember { mutableStateOf(false) }
    val length = DateUtils.formatElapsedTime((message.seconds ?: 0).toLong())
    val tap: (() -> Unit)? = when (message.kind) {
        MessageKind.PHOTO -> { { onAction(ChatAction.OpenPhoto(message.id)) } }
        MessageKind.VOICE -> { { onAction(if (isPlaying) ChatAction.StopVoice else ChatAction.PlayVoice(message.id)) } }
        null -> null
    }
    val tapLabel = when (message.kind) {
        MessageKind.VOICE -> stringResource(if (isPlaying) R.string.feature_spaces_chat_stop else R.string.feature_spaces_chat_play)
        else -> null
    }
    val deleteLabel = stringResource(R.string.feature_spaces_chat_delete_message)
    val voiceLabel = stringResource(R.string.feature_spaces_chat_voice_description, length)

    Row(
        Modifier.fillMaxWidth().padding(top = if (startsRun) 8.dp else 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            if (endsRun) InitialAvatar(message.byName, size = 32.dp, photo = photo) else Spacer(Modifier.width(32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f, fill = false).widthIn(max = 320.dp)) {
            Surface(
                color = when {
                    isBig -> Color.Transparent
                    isMine -> fill
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = ink,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .then(
                        // Only what can be tapped or held says so: a plain message from someone else is just read.
                        if (tap != null || isMine) {
                            Modifier.combinedClickable(
                                onClickLabel = tapLabel,
                                onLongClickLabel = deleteLabel.takeIf { isMine },
                                onLongClick = { menuOpen = true }.takeIf { isMine },
                                onClick = tap ?: {},
                            )
                        } else {
                            Modifier
                        },
                    )
                    // One sentence for a screen reader: who, what, when.
                    .semantics(mergeDescendants = true) { },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!isMine && startsRun) {
                        Text(message.byName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    when (message.kind) {
                        MessageKind.PHOTO -> PhotoThumbnail(message)
                        MessageKind.VOICE -> Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(if (isPlaying) AppIcons.Stop else AppIcons.Play, null)
                            Text(
                                length,
                                Modifier.padding(start = 8.dp).semantics { contentDescription = voiceLabel },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        null -> Text(
                            message.text,
                            style = if (isBig) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text(time, style = MaterialTheme.typography.labelSmall, color = if (isMine || isBig) ink else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (showsPending) {
                            AppIcon(AppIcons.CloudOff, stringResource(R.string.feature_spaces_chat_not_sent), Modifier.padding(start = 4.dp).size(12.dp))
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(deleteLabel) },
                    leadingIcon = { AppIcon(AppIcons.Delete, null) },
                    colors = destructiveMenuItemColors(),
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** The small picture of a photo message; a tap on the bubble opens the full one. */
@Composable
private fun PhotoThumbnail(message: ChatMessage) {
    val bitmap = rememberBitmap(message.thumbnail)
    val description = stringResource(R.string.feature_spaces_chat_photo_description, message.byName)
    // A very tall or very wide picture is cut to a shape that fits a bubble.
    val shape = bitmap?.let { (it.width.toFloat() / it.height).coerceIn(0.6f, 1.8f) } ?: 1.33f
    val frame = Modifier.padding(vertical = 4.dp).width(220.dp).aspectRatio(shape).clip(MaterialTheme.shapes.medium)
    if (bitmap != null) {
        Image(bitmap, description, frame, contentScale = ContentScale.Crop)
    } else {
        // Holds the place while the picture is read, so the chat does not jump.
        Box(frame.background(MaterialTheme.colorScheme.surfaceContainer).semantics { contentDescription = description })
    }
}

/** Where a message is written: a photo, a recording, text, and on the right Send or the quick emoji. */
@Composable
private fun ChatInput(look: ChatLook, fill: Color, onFill: Color, sendingPhoto: Boolean, onAction: (ChatAction) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val recorder = remember { VoiceRecorder(context) }
    // Seconds recorded so far; -1 when not recording.
    var elapsed by remember { mutableIntStateOf(-1) }
    val isRecording = elapsed >= 0
    val sendRecording: () -> Unit = {
        elapsed = -1
        recorder.stop()?.let { seconds -> onAction(ChatAction.SendVoice(recorder.file.path, seconds)) }
    }
    val start: () -> Unit = {
        try {
            recorder.start()
            elapsed = 0
        } catch (e: SecurityException) {
            onAction(ChatAction.Notice(R.string.feature_spaces_chat_mic_denied))
        } catch (e: Exception) {
            // Another app holds the microphone, or the recorder refused the format.
            onAction(ChatAction.Notice(R.string.feature_spaces_chat_voice_failed))
        }
    }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else onAction(ChatAction.Notice(R.string.feature_spaces_chat_mic_denied))
    }
    // The system's own photo picker, which needs no permission.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAction(ChatAction.SendPhoto(uri.toString()))
    }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (elapsed < MAX_VOICE_SECONDS) {
                delay(1_000)
                elapsed++
            }
            sendRecording()
        }
    }
    // Leaving the chat mid-recording drops the recording rather than sending half a sentence.
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    val accent = IconButtonDefaults.filledIconButtonColors(containerColor = fill, contentColor = onFill)
    Surface(color = MaterialTheme.colorScheme.surfaceBright, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isRecording) {
                IconButton(onClick = {
                    elapsed = -1
                    recorder.cancel()
                }) {
                    AppIcon(AppIcons.Delete, stringResource(R.string.feature_spaces_chat_discard_recording))
                }
                Text(
                    stringResource(R.string.feature_spaces_chat_recording, DateUtils.formatElapsedTime(elapsed.toLong())),
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                FilledIconButton(onClick = sendRecording, colors = accent) {
                    AppIcon(AppIcons.Send, stringResource(R.string.feature_spaces_chat_send_recording))
                }
            } else {
                IconButton(
                    onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !sendingPhoto,
                ) {
                    if (sendingPhoto) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        AppIcon(AppIcons.Image, stringResource(R.string.feature_spaces_chat_send_photo))
                    }
                }
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        start()
                    } else {
                        askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    AppIcon(AppIcons.Mic, stringResource(R.string.feature_spaces_chat_record))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_MESSAGE_LENGTH) },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.feature_spaces_chat_message_hint)) },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (text.isBlank()) {
                    // With nothing written, the same spot sends the space's quick emoji in one tap.
                    val sendEmoji = stringResource(R.string.feature_spaces_chat_send_emoji, look.emoji)
                    IconButton(
                        onClick = { onAction(ChatAction.SendEmoji) },
                        modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = sendEmoji },
                    ) {
                        Text(look.emoji, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.headlineSmall)
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            onAction(ChatAction.SendText(text))
                            text = ""
                        },
                        modifier = Modifier.padding(start = 4.dp),
                        colors = accent,
                    ) {
                        AppIcon(AppIcons.Send, stringResource(R.string.feature_spaces_chat_send))
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ChatTabPreview() {
    val now = System.currentTimeMillis()
    ListAnythingTheme {
        Surface {
            ChatTab(
                state = ChatUiState(
                    messages = listOf(
                        ChatMessage("4", "u1", "Panos", "👍", now - 60_000),
                        ChatMessage("3", "u2", "Maria", "", now - 120_000, kind = MessageKind.VOICE, seconds = 12),
                        ChatMessage("2", "u2", "Maria", "Do we still need olive oil?", now - 180_000),
                        ChatMessage("1", "u1", "Panos", "I am at the market now", now - 86_400_000),
                    ),
                    currentUserId = "u1",
                ),
                onAction = {},
                now = now,
            )
        }
    }
}
