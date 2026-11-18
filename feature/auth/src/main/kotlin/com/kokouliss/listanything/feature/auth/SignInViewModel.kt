package com.kokouliss.listanything.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.EmailAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class SignInError { NoAccount, Rejected, NotConfigured, Failed }

internal data class SignInUiState(
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
    /** Why the email form did not work; never [EmailAuthResult.Success]. */
    val emailError: EmailAuthResult? = null,
    /** The address a new-password link was just sent to. */
    val resetSentTo: String? = null,
)

@HiltViewModel
internal class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /** Runs here rather than in the screen, so a rotation while Google's account sheet is open keeps the result. */
    fun signIn(activity: Activity) {
        if (_uiState.value.isSigningIn) return
        _uiState.value = SignInUiState(isSigningIn = true)
        viewModelScope.launch { onGoogleResult(requestGoogleIdToken(activity)) }
    }

    fun signInWithEmail(email: String, password: String) = runEmail { authRepository.signInWithEmail(email, password) }

    fun createAccount(name: String, email: String, password: String) =
        runEmail { authRepository.createAccount(name, email, password) }

    fun sendPasswordReset(email: String) {
        if (_uiState.value.isSigningIn) return
        _uiState.value = SignInUiState(isSigningIn = true)
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            _uiState.value = if (result == EmailAuthResult.Success) {
                SignInUiState(resetSentTo = email.trim())
            } else {
                SignInUiState(emailError = result)
            }
        }
    }

    /** On success the app swaps this screen for the signed-in one, so only failure is shown here. */
    private fun runEmail(attempt: suspend () -> EmailAuthResult) {
        if (_uiState.value.isSigningIn) return
        _uiState.value = SignInUiState(isSigningIn = true)
        viewModelScope.launch {
            val result = attempt()
            _uiState.value = SignInUiState(emailError = result.takeIf { it != EmailAuthResult.Success })
        }
    }

    private fun onGoogleResult(result: GoogleSignInResult) {
        when (result) {
            is GoogleSignInResult.Success -> viewModelScope.launch {
                // On success the app swaps this screen for the signed-in one, so only failure is shown here.
                if (!authRepository.signInWithGoogle(result.idToken)) {
                    _uiState.value = SignInUiState(error = SignInError.Failed)
                }
            }
            GoogleSignInResult.Cancelled -> _uiState.value = SignInUiState()
            GoogleSignInResult.NoAccount -> _uiState.value = SignInUiState(error = SignInError.NoAccount)
            GoogleSignInResult.Rejected -> _uiState.value = SignInUiState(error = SignInError.Rejected)
            GoogleSignInResult.NotConfigured -> _uiState.value = SignInUiState(error = SignInError.NotConfigured)
            GoogleSignInResult.Failed -> _uiState.value = SignInUiState(error = SignInError.Failed)
        }
    }
}
