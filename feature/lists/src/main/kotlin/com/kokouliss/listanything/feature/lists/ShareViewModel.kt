package com.kokouliss.listanything.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The lists of one space, as places a shared link can go. */
internal data class ShareTarget(val spaceName: String, val lists: List<SharedList>)

@HiltViewModel
internal class ShareViewModel @Inject constructor(
    spaceRepository: SpaceRepository,
    listRepository: ListRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    /** Every list the user can add to, grouped by space. Null while loading. */
    val targets: StateFlow<List<ShareTarget>?> = spaceRepository.observeSpaces()
        .flatMapLatest { spaces ->
            if (spaces.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    spaces.map { space ->
                        listRepository.observeLists(space.id).map { lists -> ShareTarget(space.name, lists) }
                    },
                ) { targets -> targets.filter { it.lists.isNotEmpty() } }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addTo(list: SharedList, key: ShareKey) {
        viewModelScope.launch { itemRepository.addItem(ListRef(list.spaceId, list.id), key.text, key.link) }
    }
}
