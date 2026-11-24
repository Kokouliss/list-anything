package com.kokouliss.listanything.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.Activity
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.ListProgress
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.ListKind
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.normalizeEmail
import com.kokouliss.listanything.core.domain.model.forMember
import com.kokouliss.listanything.core.domain.model.progress
import com.kokouliss.listanything.core.domain.repository.ActivityRepository
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.DrawingRepository
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import com.kokouliss.listanything.core.ui.component.Undoable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface SpaceDetailUiState {
    data object Loading : SpaceDetailUiState

    /** The space was deleted, or this user left or was removed. The screen closes itself. */
    data object Gone : SpaceDetailUiState

    data class Ready(
        val space: Space,
        val lists: List<SharedList>,
        val drawings: List<Drawing>,
        val currentUserId: String,
        /** How far each list is, by list id. A list whose items have not arrived yet is missing. */
        val progress: Map<String, ListProgress> = emptyMap(),
    ) : SpaceDetailUiState {
        val isOwner: Boolean get() = space.ownerId == currentUserId
    }
}

@HiltViewModel(assistedFactory = SpaceDetailViewModel.Factory::class)
internal class SpaceDetailViewModel @AssistedInject constructor(
    @Assisted private val key: SpaceDetailKey,
    private val spaceRepository: SpaceRepository,
    private val listRepository: ListRepository,
    private val drawingRepository: DrawingRepository,
    authRepository: AuthRepository,
    itemRepository: ItemRepository,
    activityRepository: ActivityRepository,
) : ViewModel() {

    // Losing access ends this stream with an error; the space stream reports that as "gone".
    private val lists = listRepository.observeLists(key.spaceId).catch { emit(emptyList()) }

    // shortcut: listens to the items of every list of the space for its "3 of 12 left", which reads
    // each list's items once per visit and then only changes. Store a count on the list document
    // (updated by firebase/functions) if a space ever has hundreds of items.
    private val progress: Flow<Map<String, ListProgress>> = lists.flatMapLatest { lists ->
        if (lists.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                lists.map { list ->
                    itemRepository.observeItems(ListRef(key.spaceId, list.id))
                        .map { items -> list.id to items.progress() }
                        .catch { }
                },
            ) { it.toMap() }
        }
    }

    /** Email invites taken off the list at once and cancelled only once their Undo snackbar is gone. */
    val cancelledInvites = Undoable<String>(viewModelScope) { spaceRepository.cancelEmailInvite(key.spaceId, it) }

    val uiState: StateFlow<SpaceDetailUiState> = combine(
        spaceRepository.observeSpace(key.spaceId),
        lists,
        drawingRepository.observeDrawings(key.spaceId).catch { emit(emptyList()) },
        authRepository.currentUser,
        combine(cancelledInvites.pending, progress) { cancelled, progress -> cancelled to progress },
    ) { space, lists, drawings, user, (cancelled, progress) ->
        if (space == null || user == null) {
            SpaceDetailUiState.Gone
        } else {
            val invited = space.invitedEmails.filter { it != cancelled?.removal }
            SpaceDetailUiState.Ready(space.copy(invitedEmails = invited), lists, drawings, user.id, progress)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpaceDetailUiState.Loading)

    /**
     * The space's history as this member sees it, null until it arrives. Only the History tab
     * collects it, so a visit to the space that never opens the tab reads none of it.
     */
    val history: StateFlow<List<Activity>?> = combine(
        activityRepository.observeActivity(key.spaceId).catch { emit(emptyList()) },
        uiState.filterIsInstance<SpaceDetailUiState.Ready>(),
    ) { entries, state ->
        entries.forMember(
            userId = state.currentUserId,
            names = state.space.members.associate { it.id to it.name },
            listNames = state.lists.associate { it.id to "${it.emoji} ${it.name}".trim() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val space: Space? get() = (uiState.value as? SpaceDetailUiState.Ready)?.space

    fun createList(name: String, emoji: String, kind: ListKind) {
        viewModelScope.launch { listRepository.createList(key.spaceId, name, emoji, kind.features) }
    }

    /** Returns the id of the new drawing, so the screen can open it at once. */
    suspend fun createDrawing(title: String): String = drawingRepository.createDrawing(key.spaceId, title)

    fun renameSpace(name: String) {
        viewModelScope.launch { spaceRepository.renameSpace(key.spaceId, name) }
    }

    /** The screen only enables sending for a valid address; the new invite then shows up in the pending list. */
    fun inviteEmail(input: String) {
        val email = normalizeEmail(input) ?: return
        viewModelScope.launch { spaceRepository.inviteEmail(key.spaceId, email) }
    }

    fun cancelEmailInvite(email: String) = cancelledInvites.remove(email)

    fun removeMember(memberId: String) {
        val space = space ?: return
        viewModelScope.launch { spaceRepository.removeMember(space, memberId) }
    }

    fun resetInviteCode() {
        val space = space ?: return
        viewModelScope.launch { spaceRepository.resetInviteCode(space) }
    }

    fun leaveSpace() {
        viewModelScope.launch { spaceRepository.leaveSpace(key.spaceId) }
    }

    fun deleteSpace() {
        val space = space ?: return
        viewModelScope.launch { spaceRepository.deleteSpace(space) }
    }

    override fun onCleared() = cancelledInvites.flush()

    @AssistedFactory
    interface Factory {
        fun create(key: SpaceDetailKey): SpaceDetailViewModel
    }
}
