package com.kokouliss.listanything.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.ui.R as CoreR
import com.kokouliss.listanything.core.ui.component.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The longest name a person can give themselves, here and when signing up with email. */
internal const val MAX_NAME_LENGTH = 60

internal enum class NameDialogState { Closed, Open, Saving, Failed }

@HiltViewModel
internal class NameViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val dialog = MutableStateFlow(NameDialogState.Closed)

    /** The name the person has now, where the field starts. Null until it is known. */
    val name: StateFlow<String?> = authRepository.currentUser
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opened from Settings, or by itself right after a new Google account's first sign-in. */
    val state: StateFlow<NameDialogState> = combine(dialog, authRepository.asksForName) { state, asks ->
        if (asks && state == NameDialogState.Closed) NameDialogState.Open else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NameDialogState.Closed)

    fun open() {
        dialog.value = NameDialogState.Open
    }

    /** Closes without saving: the name stays as it is. */
    fun close() {
        dialog.value = NameDialogState.Closed
        authRepository.keepName()
    }

    fun save(name: String) {
        dialog.value = NameDialogState.Saving
        viewModelScope.launch {
            val saved = authRepository.setName(name)
            // Closed meanwhile: the answer comes too late to show.
            dialog.compareAndSet(NameDialogState.Saving, if (saved) NameDialogState.Closed else NameDialogState.Failed)
        }
    }
}

/**
 * Asks for the name the others in the person's spaces see. [firstTime] is the question after the
 * first sign-in, where closing the dialog keeps the name from Google.
 */
@Composable
internal fun NameDialog(
    state: NameDialogState,
    name: String?,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    firstTime: Boolean = false,
) {
    // Waits for the name too, because the field starts from it.
    if (state == NameDialogState.Closed || name == null) return
    TextInputDialog(
        title = stringResource(if (firstTime) R.string.feature_auth_name_question else R.string.feature_auth_change_name),
        label = stringResource(R.string.feature_auth_your_name),
        confirmLabel = stringResource(CoreR.string.core_ui_save),
        onConfirm = onSave,
        onDismiss = onClose,
        initialValue = name,
        maxLength = MAX_NAME_LENGTH,
        capitalization = KeyboardCapitalization.Words,
        supportingText = stringResource(if (firstTime) R.string.feature_auth_name_hint_first else R.string.feature_auth_name_hint),
        isBusy = state == NameDialogState.Saving,
        errorText = if (state == NameDialogState.Failed) stringResource(R.string.feature_auth_name_failed) else null,
        dismissLabel = stringResource(if (firstTime) R.string.feature_auth_keep_name else CoreR.string.core_ui_cancel),
    )
}

/** Right after a new Google account signs in: the person picks the name others see, or keeps Google's. */
@Composable
fun FirstNamePrompt() {
    val viewModel: NameViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    NameDialog(state, name, onSave = viewModel::save, onClose = viewModel::close, firstTime = true)
}
