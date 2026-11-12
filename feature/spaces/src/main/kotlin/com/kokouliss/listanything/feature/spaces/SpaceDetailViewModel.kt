package com.kokouliss.listanything.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.normalizeEmail
import com.kokouliss.listanything.core.domain.repository.AuthRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface SpaceDetailUiState {
    data object Loading : SpaceDetailUiState

    /** The space was deleted, or this user left or was removed. The screen closes itself. */
    data object Gone : SpaceDetailUiState

    data class Ready(
        val space: Space,
        val lists: List<SharedList>,
        val currentUserId: String,
    ) : SpaceDetailUiState {
        val isOwner: Boolean get() = space.ownerId == currentUserId
    }
}

@HiltViewModel(assistedFactory = SpaceDetailViewModel.Factory::class)
internal class SpaceDetailViewModel @AssistedInject constructor(
    @Assisted private val key: SpaceDetailKey,
    private val spaceRepository: SpaceRepository,
    private val listRepository: ListRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    /** Email invites taken off the list at once and cancelled only once their Undo snackbar is gone. */
    val cancelledInvites = Undoable<String>(viewModelScope) { spaceRepository.cancelEmailInvite(key.spaceId, it) }

    val uiState: StateFlow<SpaceDetailUiState> = combine(
        spaceRepository.observeSpace(key.spaceId),
        // Losing access ends this stream with an error; the space stream reports that as "gone".
        listRepository.observeLists(key.spaceId).catch { emit(emptyList()) },
        authRepository.currentUser,
        cancelledInvites.pending,
    ) { space, lists, user, cancelled ->
        if (space == null || user == null) {
            SpaceDetailUiState.Gone
        } else {
            val invited = space.invitedEmails.filter { it != cancelled?.removal }
            SpaceDetailUiState.Ready(space.copy(invitedEmails = invited), lists, user.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpaceDetailUiState.Loading)

    private val space: Space? get() = (uiState.value as? SpaceDetailUiState.Ready)?.space

    fun createList(name: String, emoji: String) {
        viewModelScope.launch { listRepository.createList(key.spaceId, name, emoji) }
    }

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
