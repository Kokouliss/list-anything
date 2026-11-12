package com.kokouliss.listanything.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import com.kokouliss.listanything.core.ui.component.Undoable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface SpacesUiState {
    data object Loading : SpacesUiState

    /** The spaces could not be read, for example because the security rules are not deployed yet. */
    data object Error : SpacesUiState

    data class Ready(
        val userName: String,
        val spaces: List<Space>,
        val invitations: List<Space>,
        val join: JoinState,
    ) : SpacesUiState
}

/** Where joining a space with a code stands. */
internal sealed interface JoinState {
    data object Idle : JoinState
    data object Joining : JoinState

    /** The screen shows [result], then calls [SpacesViewModel.joinShown]. */
    data class Done(val result: JoinResult) : JoinState
}

@HiltViewModel
internal class SpacesViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val join = MutableStateFlow<JoinState>(JoinState.Idle)

    /** Invitations taken off the screen at once and declined only once their Undo snackbar is gone. */
    val declines = Undoable<Space>(viewModelScope) { spaceRepository.declineInvitation(it.id) }

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<SpacesUiState> = retries
        .flatMapLatest {
            combine(
                authRepository.currentUser,
                spaceRepository.observeSpaces(),
                spaceRepository.observeInvitations(),
                join,
                declines.pending,
            ) { user, spaces, invitations, joining, declined ->
                SpacesUiState.Ready(
                    userName = user?.name.orEmpty(),
                    spaces = spaces,
                    invitations = invitations.filter { it.id != declined?.removal?.id },
                    join = joining,
                ) as SpacesUiState
            }.catch { emit(SpacesUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpacesUiState.Loading)

    /** A failed stream does not restart by itself, so the error screen offers this. */
    fun retry() {
        retries.update { it + 1 }
    }

    fun createSpace(name: String) {
        viewModelScope.launch { spaceRepository.createSpace(name) }
    }

    fun joinWithCode(code: String) {
        if (join.value == JoinState.Joining) return
        join.value = JoinState.Joining
        viewModelScope.launch { join.value = JoinState.Done(spaceRepository.joinWithCode(code)) }
    }

    fun joinShown() {
        join.update { if (it is JoinState.Done) JoinState.Idle else it }
    }

    fun acceptInvitation(spaceId: String) {
        viewModelScope.launch { spaceRepository.acceptInvitation(spaceId) }
    }

    fun declineInvitation(spaceId: String) {
        val ready = uiState.value as? SpacesUiState.Ready ?: return
        ready.invitations.find { it.id == spaceId }?.let(declines::remove)
    }

    override fun onCleared() = declines.flush()
}
