package com.kokouliss.listanything.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.ChatLook
import com.kokouliss.listanything.core.domain.model.ChatMessage
import com.kokouliss.listanything.core.domain.model.ChatMute
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.chatMutedUntil
import com.kokouliss.listanything.core.domain.model.photos
import com.kokouliss.listanything.core.domain.model.withNames
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.ChatRepository
import com.kokouliss.listanything.core.domain.repository.ChatRepository.Companion.MESSAGES_PAGE
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The full-size photo of one message. [image] stays null when it could not be loaded. */
internal data class ChatPhoto(val messageId: String, val image: ImageData?, val isLoading: Boolean)

/** The voice message that is playing or about to. [audio] stays null while it loads. */
internal class ChatVoice(val messageId: String, val audio: ByteArray?)

/** What is on top of the messages, or on its way. */
internal data class ChatOverlays(
    /** A photo is being shrunk before it is sent. */
    val sendingPhoto: Boolean = false,
    val photo: ChatPhoto? = null,
    val voice: ChatVoice? = null,
    /** A message for the person, as a string resource, shown once. */
    val notice: Int? = null,
)

internal data class ChatUiState(
    /** Newest first. Null until the first ones arrive. */
    val messages: List<ChatMessage>? = null,
    val currentUserId: String = "",
    val look: ChatLook = ChatLook(),
    /** Until when this member hears nothing from the chat, or null when they hear it. */
    val mutedUntil: Long? = null,
    /** The profile photo of each member who has one, by id. */
    val photos: Map<String, ImageData> = emptyMap(),
    /** Older messages than the ones shown may exist. */
    val canLoadMore: Boolean = false,
    val overlays: ChatOverlays = ChatOverlays(),
)

internal sealed interface ChatAction {
    data class SendText(val text: String) : ChatAction

    /** The quick emoji of the space, sent with one tap. */
    data object SendEmoji : ChatAction
    data class SendPhoto(val photoUri: String) : ChatAction
    data class SendVoice(val audioPath: String, val seconds: Int) : ChatAction
    data class Delete(val messageId: String) : ChatAction
    data class OpenPhoto(val messageId: String) : ChatAction
    data object ClosePhoto : ChatAction
    data class PlayVoice(val messageId: String) : ChatAction
    data object StopVoice : ChatAction
    data object LoadMore : ChatAction
    data class SetLook(val look: ChatLook) : ChatAction

    /** Null turns the notifications back on. */
    data class Mute(val mute: ChatMute?) : ChatAction
    data class Notice(val message: Int) : ChatAction
    data object NoticeShown : ChatAction
}

/** The chat of one space. Its own view model, so a visit that never opens the Chat tab reads no message. */
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
internal class ChatViewModel @AssistedInject constructor(
    @Assisted private val key: SpaceDetailKey,
    private val chat: ChatRepository,
    spaceRepository: SpaceRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val spaceId: String get() = key.spaceId

    private val limit = MutableStateFlow(MESSAGES_PAGE)
    private val overlays = MutableStateFlow(ChatOverlays())

    // Refused or cut off, for example before the security rules know the chat: an empty chat, not a crash.
    private val messages = limit.flatMapLatest { limit ->
        chat.observeMessages(key.spaceId, limit).catch { emit(emptyList()) }.map { it to limit }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        messages,
        spaceRepository.observeSpace(key.spaceId),
        authRepository.currentUser,
        overlays,
    ) { (messages, limit), space, user, overlays ->
        val userId = user?.id.orEmpty()
        val members = space?.members.orEmpty()
        ChatUiState(
            messages = messages.withNames(members.associate { it.id to it.name }),
            currentUserId = userId,
            look = space?.chatLook ?: ChatLook(),
            // shortcut: checked when the chat changes, not on a timer, so the bell of a mute that
            // ran out on an open screen stays crossed until the next message. Notifications are right.
            mutedUntil = space?.chatMutedUntil(userId, System.currentTimeMillis()),
            photos = members.photos(),
            canLoadMore = messages.size >= limit,
            overlays = overlays,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.SendText -> launch { chat.sendText(key.spaceId, action.text) }
            ChatAction.SendEmoji -> launch { chat.sendText(key.spaceId, uiState.value.look.emoji) }
            is ChatAction.SendPhoto -> sendPhoto(action.photoUri)
            is ChatAction.SendVoice -> launch { chat.sendVoice(key.spaceId, action.audioPath, action.seconds) }
            is ChatAction.Delete -> launch { chat.deleteMessage(key.spaceId, action.messageId) }
            is ChatAction.OpenPhoto -> openPhoto(action.messageId)
            ChatAction.ClosePhoto -> overlays.update { it.copy(photo = null) }
            is ChatAction.PlayVoice -> playVoice(action.messageId)
            ChatAction.StopVoice -> overlays.update { it.copy(voice = null) }
            ChatAction.LoadMore -> if (uiState.value.canLoadMore) limit.update { it + MESSAGES_PAGE }
            is ChatAction.SetLook -> launch { chat.setLook(key.spaceId, action.look) }
            is ChatAction.Mute -> launch { chat.setMutedUntil(key.spaceId, action.mute?.until(System.currentTimeMillis())) }
            is ChatAction.Notice -> overlays.update { it.copy(notice = action.message) }
            ChatAction.NoticeShown -> overlays.update { it.copy(notice = null) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun sendPhoto(photoUri: String) = launch {
        overlays.update { it.copy(sendingPhoto = true) }
        try {
            chat.sendPhoto(key.spaceId, photoUri)
            overlays.update { it.copy(sendingPhoto = false) }
        } catch (e: IOException) {
            overlays.update { it.copy(sendingPhoto = false, notice = R.string.feature_spaces_chat_photo_failed) }
        } catch (e: IllegalStateException) {
            // Still too big after shrinking.
            overlays.update { it.copy(sendingPhoto = false, notice = R.string.feature_spaces_chat_photo_failed) }
        }
    }

    private fun openPhoto(messageId: String) = launch {
        overlays.update { it.copy(photo = ChatPhoto(messageId, image = null, isLoading = true)) }
        val image = chat.loadPhoto(key.spaceId, messageId)
        // Still the photo that was asked for: the person may have closed it or opened another meanwhile.
        overlays.update { if (it.photo?.messageId == messageId) it.copy(photo = ChatPhoto(messageId, image, isLoading = false)) else it }
    }

    private fun playVoice(messageId: String) = launch {
        overlays.update { it.copy(voice = ChatVoice(messageId, audio = null)) }
        val audio = chat.loadVoice(key.spaceId, messageId)
        overlays.update {
            when {
                it.voice?.messageId != messageId -> it
                audio == null -> it.copy(voice = null, notice = R.string.feature_spaces_chat_voice_not_synced)
                else -> it.copy(voice = ChatVoice(messageId, audio))
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SpaceDetailKey): ChatViewModel
    }
}
