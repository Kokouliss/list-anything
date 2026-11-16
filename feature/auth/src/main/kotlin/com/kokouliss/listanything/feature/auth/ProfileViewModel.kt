package com.kokouliss.listanything.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where deleting the account stands. Success needs no state: the app leaves this screen. */
internal enum class DeleteState { Idle, Deleting, Failed }

@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Null for a moment while loading, and again right after signing out, when the app leaves this screen. */
    val user: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private val _deleteState = MutableStateFlow(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    /** [idToken] is null when Google did not hand over a fresh sign-in token. */
    fun deleteAccount(idToken: String?) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Deleting
            val done = idToken != null && authRepository.deleteAccount(idToken)
            _deleteState.value = if (done) DeleteState.Idle else DeleteState.Failed
        }
    }
}
