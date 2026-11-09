package com.kokouliss.listanything.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface SpacesUiState {
    data object Loading : SpacesUiState

    /** The spaces could not be read, for example because the security rules are not deployed yet. */
    data object Error : SpacesUiState

    data class Ready(
        val userName: String,
        val userEmail: String?,
        val spaces: List<Space>,
        val invitations: List<Space>,
        val isJoining: Boolean,
    ) : SpacesUiState
}

internal sealed interface SpacesEvent {
    data class JoinFinished(val result: JoinResult) : SpacesEvent
}

@HiltViewModel
internal class SpacesViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val isJoining = MutableStateFlow(false)

    private val _events = Channel<SpacesEvent>(Channel.BUFFERED)
    val events: Flow<SpacesEvent> = _events.receiveAsFlow()

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<SpacesUiState> = retries
        .flatMapLatest {
            combine(
                authRepository.currentUser,
                spaceRepository.observeSpaces(),
                spaceRepository.observeInvitations(),
                isJoining,
            ) { user, spaces, invitations, joining ->
                SpacesUiState.Ready(
                    userName = user?.name.orEmpty(),
                    userEmail = user?.email,
                    spaces = spaces,
                    invitations = invitations,
                    isJoining = joining,
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
        if (isJoining.value) return
        isJoining.value = true
        viewModelScope.launch {
            val result = spaceRepository.joinWithCode(code)
            isJoining.value = false
            _events.send(SpacesEvent.JoinFinished(result))
        }
    }

    fun acceptInvitation(spaceId: String) {
        viewModelScope.launch { spaceRepository.acceptInvitation(spaceId) }
    }

    fun declineInvitation(spaceId: String) {
        viewModelScope.launch { spaceRepository.declineInvitation(spaceId) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
