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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The full-size photo of one item. [image] stays null when it could not be loaded. */
internal data class PhotoViewer(val itemId: String, val image: ImageData?, val isLoading: Boolean)

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

    /** The Undo snackbar of the pending removal was used, or went away. */
    data class UndoRemoval(val token: Int) : ListDetailAction
    data class KeepRemoval(val token: Int) : ListDetailAction
    data class UpdateList(val name: String, val emoji: String) : ListDetailAction
    data object DeleteList : ListDetailAction
}

internal sealed interface ListDetailEvent {
    /** The chosen picture could not be read or shrunk. */
    data object PhotoFailed : ListDetailEvent
}

/** A removal the screen shows at once but that is written only once its Undo snackbar is gone. */
internal sealed interface Removal {
    data class Items(val itemIds: List<String>, val isSection: Boolean) : Removal
    data class Photo(val itemId: String) : Removal
    data class Place(val itemId: String) : Removal
    data class DueDay(val itemId: String) : Removal
}

/** [token] tells a late Undo or Keep from the snackbar apart from one for a newer removal. */
internal data class PendingRemoval(val token: Int, val removal: Removal)

private fun List<ListItem>.without(removal: Removal): List<ListItem> = when (removal) {
    is Removal.Items -> filter { it.id !in removal.itemIds }
    is Removal.Photo -> map { if (it.id == removal.itemId) it.copy(thumbnail = null) else it }
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
    private val _pendingRemoval = MutableStateFlow<PendingRemoval?>(null)
    private var nextToken = 0

    private val _events = Channel<ListDetailEvent>(Channel.BUFFERED)
    val events: Flow<ListDetailEvent> = _events.receiveAsFlow()

    /** The removal whose Undo snackbar the screen should show. A state, so a rebuilt screen shows it again. */
    val pendingRemoval: StateFlow<PendingRemoval?> = _pendingRemoval.asStateFlow()

    private val visibleItems = combine(
        // Losing access ends this stream with an error; the list stream reports that as "gone".
        itemRepository.observeItems(ref).catch { emit(emptyList()) },
        _pendingRemoval,
    ) { items, pending -> if (pending == null) items else items.without(pending.removal) }

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observeList(ref),
        visibleItems,
        photoBusy,
        photoViewer,
        authRepository.currentUser,
    ) { list, items, busy, viewer, user ->
        if (list == null) {
            ListDetailUiState.Gone
        } else {
            ListDetailUiState.Ready(list, items.sortedForDisplay(), busy, viewer, user?.id.orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState.Loading)

    fun onAction(action: ListDetailAction) {
        when (action) {
            is ListDetailAction.AddItem -> addItem(action.text, action.sectionId)
            is ListDetailAction.AddSection -> addItem(action.name, sectionId = null, isHeader = true)
            is ListDetailAction.SetChecked -> launch { setChecked(ref, action.itemId, action.checked) }
            is ListDetailAction.UpdateText -> launch { updateText(ref, action.itemId, action.text) }
            is ListDetailAction.DeleteItem -> remove(
                Removal.Items(listOf(action.itemId), isSection = items.any { it.id == action.itemId && it.isHeader }),
            )
            is ListDetailAction.SetPhoto -> setPhoto(action.itemId, action.photoUri)
            is ListDetailAction.RemovePhoto -> remove(Removal.Photo(action.itemId))
            is ListDetailAction.SetLocation -> if (action.location == null) {
                remove(Removal.Place(action.itemId))
            } else {
                launch { setLocation(ref, action.itemId, action.location) }
            }
            is ListDetailAction.SetLink -> launch { setLink(ref, action.itemId, action.link) }
            is ListDetailAction.SetNote -> launch { setNote(ref, action.itemId, action.note) }
            is ListDetailAction.SetQuantity -> launch { setQuantity(ref, action.itemId, action.quantity) }
            is ListDetailAction.SetPrice -> launch { setPrice(ref, action.itemId, action.priceCents) }
            is ListDetailAction.SetDueDay -> if (action.dueDay == null) {
                remove(Removal.DueDay(action.itemId))
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
            is ListDetailAction.UndoRemoval -> _pendingRemoval.update { if (it?.token == action.token) null else it }
            is ListDetailAction.KeepRemoval -> keep(_pendingRemoval.value?.takeIf { it.token == action.token })
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
        if (checkedIds.isNotEmpty()) remove(Removal.Items(checkedIds, isSection = false))
    }

    /** Shows [removal] as done and offers Undo for it. A removal still waiting is written first. */
    private fun remove(removal: Removal) {
        keep(_pendingRemoval.value)
        _pendingRemoval.value = PendingRemoval(nextToken++, removal)
    }

    private fun keep(pending: PendingRemoval?) {
        if (pending == null || _pendingRemoval.value != pending) return
        _pendingRemoval.value = null
        launch { write(pending.removal) }
    }

    private suspend fun ItemRepository.write(removal: Removal) = when (removal) {
        is Removal.Items -> deleteItems(ref, removal.itemIds)
        is Removal.Photo -> removePhoto(ref, removal.itemId)
        is Removal.Place -> setLocation(ref, removal.itemId, null)
        is Removal.DueDay -> setDueDay(ref, removal.itemId, null)
    }

    override fun onCleared() {
        // Leaving the screen ends the Undo offer. The scope is already cancelled here, and the write
        // only queues on the device, so it is launched outside the scope and not waited for.
        _pendingRemoval.value?.let { pending ->
            viewModelScope.launch(NonCancellable) { itemRepository.write(pending.removal) }
        }
    }

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
