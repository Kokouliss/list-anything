package com.kokouliss.listanything.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.orderAtEndOfSection
import com.kokouliss.listanything.core.domain.model.sortedForDisplay
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import com.kokouliss.listanything.core.ui.component.Undoable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The full-size photo of one item. [image] stays null when it could not be loaded. */
internal data class PhotoViewer(val itemId: String, val image: ImageData?, val isLoading: Boolean)

/** The voice note of one item, playing or about to. [audio] stays null while it loads. */
internal data class VoicePlayback(val itemId: String, val audio: ByteArray?)

internal sealed interface ListDetailUiState {
    data object Loading : ListDetailUiState

    /** The list was deleted, or this user lost access to its space. The screen closes itself. */
    data object Gone : ListDetailUiState

    data class Ready(
        val list: SharedList,
        val items: List<ListItem>,
        /** Items whose new photo is still being shrunk and saved. */
        val photoBusyItemIds: Set<String>,
        val photoViewer: PhotoViewer?,
        val currentUserId: String = "",
        val voice: VoicePlayback? = null,
    ) : ListDetailUiState {
        /** On the user's own wish list the app keeps who takes what a surprise. */
        val hidesClaims: Boolean get() = list.giftForId == currentUserId
    }
}

internal sealed interface ListDetailAction {
    /** [sectionId] is the heading the item goes under. Null adds it at the end of the list. */
    data class AddItem(val text: String, val sectionId: String? = null) : ListDetailAction
    data class AddSection(val name: String) : ListDetailAction
    data class SetChecked(val itemId: String, val checked: Boolean) : ListDetailAction
    data class UpdateText(val itemId: String, val text: String) : ListDetailAction
    data class DeleteItem(val itemId: String) : ListDetailAction
    data class SetPhoto(val itemId: String, val photoUri: String) : ListDetailAction
    data class RemovePhoto(val itemId: String) : ListDetailAction

    /** [audioPath] is the file the recorder wrote. */
    data class SetVoiceNote(val itemId: String, val audioPath: String, val seconds: Int) : ListDetailAction
    data class RemoveVoiceNote(val itemId: String) : ListDetailAction
    data class PlayVoiceNote(val itemId: String) : ListDetailAction
    data object StopVoiceNote : ListDetailAction
    data class SetLocation(val itemId: String, val location: ItemLocation?) : ListDetailAction

    /** [link] is already checked with `normalizeLink`. Null removes the link. */
    data class SetLink(val itemId: String, val link: String?) : ListDetailAction
    data class SetNote(val itemId: String, val note: String?) : ListDetailAction
    data class SetQuantity(val itemId: String, val quantity: Int) : ListDetailAction
    data class SetPrice(val itemId: String, val priceCents: Long?) : ListDetailAction
    data class SetDueDay(val itemId: String, val dueDay: Long?) : ListDetailAction
    data class SetClaimed(val itemId: String, val claimed: Boolean) : ListDetailAction

    /** [order] comes from `orderAfterMove`. */
    data class SetOrder(val itemId: String, val order: Double) : ListDetailAction
    data class SetGiftForMe(val enabled: Boolean) : ListDetailAction
    data class OpenPhoto(val itemId: String) : ListDetailAction
    data object ClosePhoto : ListDetailAction
    data object ClearChecked : ListDetailAction
    data class UpdateList(val name: String, val emoji: String) : ListDetailAction
    data object DeleteList : ListDetailAction
}

internal sealed interface ListDetailEvent {
    /** The chosen picture could not be read or shrunk. */
    data object PhotoFailed : ListDetailEvent

    /** The recording could not be read or is too long to store. */
    data object VoiceFailed : ListDetailEvent

    /** The voice note has not reached this device yet. */
    data object VoiceNotSynced : ListDetailEvent
}

/** A removal the screen shows at once but that is written only once its Undo snackbar is gone. */
internal sealed interface Removal {
    data class Items(val itemIds: List<String>, val isSection: Boolean) : Removal
    data class Photo(val itemId: String) : Removal
    data class Voice(val itemId: String) : Removal
    data class Place(val itemId: String) : Removal
    data class DueDay(val itemId: String) : Removal
}

private fun List<ListItem>.without(removal: Removal): List<ListItem> = when (removal) {
    is Removal.Items -> filter { it.id !in removal.itemIds }
    is Removal.Photo -> map { if (it.id == removal.itemId) it.copy(thumbnail = null) else it }
    is Removal.Voice -> map { if (it.id == removal.itemId) it.copy(voiceSeconds = null) else it }
    is Removal.Place -> map { if (it.id == removal.itemId) it.copy(location = null) else it }
    is Removal.DueDay -> map { if (it.id == removal.itemId) it.copy(dueDay = null) else it }
}

@HiltViewModel(assistedFactory = ListDetailViewModel.Factory::class)
internal class ListDetailViewModel @AssistedInject constructor(
    @Assisted key: ListDetailKey,
    private val listRepository: ListRepository,
    private val itemRepository: ItemRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val ref = ListRef(key.spaceId, key.listId)
    private val photoBusy = MutableStateFlow(emptySet<String>())
    private val photoViewer = MutableStateFlow<PhotoViewer?>(null)
    private val voice = MutableStateFlow<VoicePlayback?>(null)
    private val _events = Channel<ListDetailEvent>(Channel.BUFFERED)
    val events: Flow<ListDetailEvent> = _events.receiveAsFlow()

    /** Removals shown as done at once and written only once their Undo snackbar is gone. */
    val removals = Undoable<Removal>(viewModelScope) { itemRepository.write(it) }

    private val visibleItems = combine(
        // Losing access ends this stream with an error; the list stream reports that as "gone".
        itemRepository.observeItems(ref).catch { emit(emptyList()) },
        removals.pending,
    ) { items, pending -> if (pending == null) items else items.without(pending.removal) }

    // What is open on top of the list: the photo viewer and the playing voice note.
    private val overlays = combine(photoViewer, voice) { viewer, playing -> viewer to playing }

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observeList(ref),
        visibleItems,
        photoBusy,
        overlays,
        authRepository.currentUser,
    ) { list, items, busy, (viewer, playing), user ->
        if (list == null) {
            ListDetailUiState.Gone
        } else {
            ListDetailUiState.Ready(list, items.sortedForDisplay(), busy, viewer, user?.id.orEmpty(), playing)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState.Loading)

    fun onAction(action: ListDetailAction) {
        when (action) {
            is ListDetailAction.AddItem -> addItem(action.text, action.sectionId)
            is ListDetailAction.AddSection -> addItem(action.name, sectionId = null, isHeader = true)
            is ListDetailAction.SetChecked -> launch { setChecked(ref, action.itemId, action.checked) }
            is ListDetailAction.UpdateText -> launch { updateText(ref, action.itemId, action.text) }
            is ListDetailAction.DeleteItem -> removals.remove(
                Removal.Items(listOf(action.itemId), isSection = items.any { it.id == action.itemId && it.isHeader }),
            )
            is ListDetailAction.SetPhoto -> setPhoto(action.itemId, action.photoUri)
            is ListDetailAction.RemovePhoto -> removals.remove(Removal.Photo(action.itemId))
            is ListDetailAction.SetVoiceNote -> setVoiceNote(action.itemId, action.audioPath, action.seconds)
            is ListDetailAction.RemoveVoiceNote -> {
                voice.update { if (it?.itemId == action.itemId) null else it }
                removals.remove(Removal.Voice(action.itemId))
            }
            is ListDetailAction.PlayVoiceNote -> playVoiceNote(action.itemId)
            ListDetailAction.StopVoiceNote -> voice.value = null
            is ListDetailAction.SetLocation -> if (action.location == null) {
                removals.remove(Removal.Place(action.itemId))
            } else {
                launch { setLocation(ref, action.itemId, action.location) }
            }
            is ListDetailAction.SetLink -> launch { setLink(ref, action.itemId, action.link) }
            is ListDetailAction.SetNote -> launch { setNote(ref, action.itemId, action.note) }
            is ListDetailAction.SetQuantity -> launch { setQuantity(ref, action.itemId, action.quantity) }
            is ListDetailAction.SetPrice -> launch { setPrice(ref, action.itemId, action.priceCents) }
            is ListDetailAction.SetDueDay -> if (action.dueDay == null) {
                removals.remove(Removal.DueDay(action.itemId))
            } else {
                launch { setDueDay(ref, action.itemId, action.dueDay) }
            }
            is ListDetailAction.SetClaimed -> launch { setClaimed(ref, action.itemId, action.claimed) }
            is ListDetailAction.SetOrder -> launch { setOrder(ref, action.itemId, action.order) }
            is ListDetailAction.SetGiftForMe -> viewModelScope.launch {
                listRepository.setGiftForMe(ref, action.enabled)
            }
            is ListDetailAction.OpenPhoto -> openPhoto(action.itemId)
            ListDetailAction.ClosePhoto -> photoViewer.value = null
            ListDetailAction.ClearChecked -> clearChecked()
            is ListDetailAction.UpdateList -> viewModelScope.launch {
                listRepository.updateList(ref, action.name, action.emoji)
            }
            ListDetailAction.DeleteList -> viewModelScope.launch { listRepository.deleteList(ref) }
        }
    }

    private fun launch(block: suspend ItemRepository.() -> Unit) {
        viewModelScope.launch { itemRepository.block() }
    }

    private val items: List<ListItem>
        get() = (uiState.value as? ListDetailUiState.Ready)?.items.orEmpty()

    private fun addItem(text: String, sectionId: String?, isHeader: Boolean = false) {
        val trimmed = text.trim()
        val items = items
        if (trimmed.isNotEmpty()) {
            launch { addItem(ref, trimmed, isHeader = isHeader, order = orderAtEndOfSection(items, sectionId)) }
        }
    }

    private fun clearChecked() {
        val checkedIds = items.filter { it.isChecked }.map { it.id }
        if (checkedIds.isNotEmpty()) removals.remove(Removal.Items(checkedIds, isSection = false))
    }

    private suspend fun ItemRepository.write(removal: Removal) = when (removal) {
        is Removal.Items -> deleteItems(ref, removal.itemIds)
        is Removal.Photo -> removePhoto(ref, removal.itemId)
        is Removal.Voice -> removeVoiceNote(ref, removal.itemId)
        is Removal.Place -> setLocation(ref, removal.itemId, null)
        is Removal.DueDay -> setDueDay(ref, removal.itemId, null)
    }

    override fun onCleared() = removals.flush()

    private fun setPhoto(itemId: String, photoUri: String) {
        viewModelScope.launch {
            photoBusy.update { it + itemId }
            try {
                itemRepository.setPhoto(ref, itemId, photoUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The picture comes from another app, so it can be unreadable in many ways.
                _events.send(ListDetailEvent.PhotoFailed)
            } finally {
                photoBusy.update { it - itemId }
            }
        }
    }

    private fun setVoiceNote(itemId: String, audioPath: String, seconds: Int) {
        viewModelScope.launch {
            try {
                itemRepository.setVoiceNote(ref, itemId, audioPath, seconds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ListDetailEvent.VoiceFailed)
            }
        }
    }

    private fun playVoiceNote(itemId: String) {
        voice.value = VoicePlayback(itemId, audio = null)
        viewModelScope.launch {
            val audio = itemRepository.loadVoiceNote(ref, itemId)
            if (audio == null) {
                _events.send(ListDetailEvent.VoiceNotSynced)
                voice.update { if (it?.itemId == itemId) null else it }
            } else {
                // Ignore the result when playback was stopped or moved to another item meanwhile.
                voice.update { if (it?.itemId == itemId) it.copy(audio = audio) else it }
            }
        }
    }

    private fun openPhoto(itemId: String) {
        photoViewer.value = PhotoViewer(itemId, image = null, isLoading = true)
        viewModelScope.launch {
            val image = itemRepository.loadPhoto(ref, itemId)
            // Ignore the result when the viewer was closed or moved to another item meanwhile.
            photoViewer.update { if (it?.itemId == itemId) it.copy(image = image, isLoading = false) else it }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ListDetailKey): ListDetailViewModel
    }
}
