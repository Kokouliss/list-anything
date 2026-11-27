package com.kokouliss.listanything.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.ReminderScheduler
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
    private val reminders: ReminderScheduler,
) : ViewModel() {

    /** The hour of the day the "items due today" reminder comes at. */
    val reminderHour: StateFlow<Int> get() = _reminderHour
    private val _reminderHour = MutableStateFlow(reminders.dailyHour)

    fun setReminderHour(hour: Int) {
        reminders.setDailyHour(hour)
        _reminderHour.value = reminders.dailyHour
    }

    /** Null for a moment while loading, and again right after signing out, when the app leaves this screen. */
    val user: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private val _deleteState = MutableStateFlow(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    /** [idToken] is null when Google did not hand over a fresh sign-in token. */
    fun deleteAccount(idToken: String?) = delete { idToken != null && authRepository.deleteAccount(idToken) }

    fun deleteAccountWithPassword(password: String) = delete { authRepository.deleteAccountWithPassword(password) }

    private fun delete(attempt: suspend () -> Boolean) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Deleting
            _deleteState.value = if (attempt()) DeleteState.Idle else DeleteState.Failed
        }
    }

    private val _verificationSent = MutableStateFlow(false)
    val verificationSent: StateFlow<Boolean> = _verificationSent.asStateFlow()

    fun sendVerificationEmail() {
        viewModelScope.launch { _verificationSent.value = authRepository.sendVerificationEmail() }
    }

    /** True after a picture that could not be read, until the next try. */
    val photoFailed: StateFlow<Boolean> get() = _photoFailed
    private val _photoFailed = MutableStateFlow(false)

    /** [photoUri] is the picture the person picked; null takes their profile photo away. */
    fun setPhoto(photoUri: String?) {
        viewModelScope.launch { _photoFailed.value = !authRepository.setPhoto(photoUri) }
    }

    init {
        // Opening Settings after tapping the link shows the address as confirmed right away.
        viewModelScope.launch { authRepository.refreshEmailVerification() }
    }
}
