package com.kokouliss.listanything.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.sortedForDisplay
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
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
    ) : ListDetailUiState
}

internal sealed interface ListDetailAction {
    data class AddItem(val text: String) : ListDetailAction
    data class SetChecked(val itemId: String, val checked: Boolean) : ListDetailAction
    data class UpdateText(val itemId: String, val text: String) : ListDetailAction
    data class DeleteItem(val itemId: String) : ListDetailAction
    data class SetPhoto(val itemId: String, val photoUri: String) : ListDetailAction
    data class RemovePhoto(val itemId: String) : ListDetailAction
    data class SetLocation(val itemId: String, val location: ItemLocation?) : ListDetailAction
    data class OpenPhoto(val itemId: String) : ListDetailAction
    data object ClosePhoto : ListDetailAction
    data object ClearChecked : ListDetailAction
    data class UpdateList(val name: String, val emoji: String) : ListDetailAction
    data object DeleteList : ListDetailAction
}

internal sealed interface ListDetailEvent {
    /** The chosen picture could not be read or shrunk. */
    data object PhotoFailed : ListDetailEvent
}

@HiltViewModel(assistedFactory = ListDetailViewModel.Factory::class)
internal class ListDetailViewModel @AssistedInject constructor(
    @Assisted key: ListDetailKey,
    private val listRepository: ListRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    private val ref = ListRef(key.spaceId, key.listId)
    private val photoBusy = MutableStateFlow(emptySet<String>())
    private val photoViewer = MutableStateFlow<PhotoViewer?>(null)

    private val _events = Channel<ListDetailEvent>(Channel.BUFFERED)
    val events: Flow<ListDetailEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ListDetailUiState> = combine(
        listRepository.observeList(ref),
        // Losing access ends this stream with an error; the list stream reports that as "gone".
        itemRepository.observeItems(ref).catch { emit(emptyList()) },
        photoBusy,
        photoViewer,
    ) { list, items, busy, viewer ->
        if (list == null) {
            ListDetailUiState.Gone
        } else {
            ListDetailUiState.Ready(list, items.sortedForDisplay(), busy, viewer)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState.Loading)

    fun onAction(action: ListDetailAction) {
        when (action) {
            is ListDetailAction.AddItem -> addItem(action.text)
            is ListDetailAction.SetChecked -> launch { setChecked(ref, action.itemId, action.checked) }
            is ListDetailAction.UpdateText -> launch { updateText(ref, action.itemId, action.text) }
            is ListDetailAction.DeleteItem -> launch { deleteItems(ref, listOf(action.itemId)) }
            is ListDetailAction.SetPhoto -> setPhoto(action.itemId, action.photoUri)
            is ListDetailAction.RemovePhoto -> launch { removePhoto(ref, action.itemId) }
            is ListDetailAction.SetLocation -> launch { setLocation(ref, action.itemId, action.location) }
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

    private fun addItem(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) launch { addItem(ref, trimmed) }
    }

    private fun clearChecked() {
        val checkedIds = (uiState.value as? ListDetailUiState.Ready)?.items.orEmpty()
            .filter { it.isChecked }
            .map { it.id }
        if (checkedIds.isNotEmpty()) launch { deleteItems(ref, checkedIds) }
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
