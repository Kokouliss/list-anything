package com.kokouliss.listanything.core.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.kokouliss.listanything.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Removals that wait for an Undo. [remove] hides one at once; [write] runs when its snackbar is
 * gone ([keep]) or the screen is left ([flush]), unless [undo] brought it back first.
 */
class Undoable<T : Any>(private val scope: CoroutineScope, private val write: suspend (T) -> Unit) {

    /** [token] tells a late Undo or Keep from the snackbar apart from one for a newer removal. */
    data class Pending<R>(val token: Int, val removal: R)

    private val _pending = MutableStateFlow<Pending<T>?>(null)
    private var nextToken = 0

    /** The removal whose Undo snackbar the screen should show. A state, so a rebuilt screen shows it again. */
    val pending: StateFlow<Pending<T>?> = _pending.asStateFlow()

    /** Hides [removal] and offers Undo for it. A removal still offered is written first. */
    fun remove(removal: T) {
        keep(_pending.value)
        _pending.value = Pending(nextToken++, removal)
    }

    fun undo(token: Int) {
        _pending.update { if (it?.token == token) null else it }
    }

    fun keep(token: Int) = keep(_pending.value?.takeIf { it.token == token })

    private fun keep(pending: Pending<T>?) {
        if (pending == null || _pending.value != pending) return
        _pending.value = null
        scope.launch { write(pending.removal) }
    }

    /**
     * For `ViewModel.onCleared`: leaving the screen ends the Undo offer. The scope is already
     * cancelled there, and the write only queues on the device, so it runs outside the scope.
     */
    fun flush() {
        _pending.value?.let { scope.launch(NonCancellable) { write(it.removal) } }
    }
}

/** Shows the Undo snackbar of [undoable] in [host], with [describe] as its text. */
@Composable
fun <T : Any> UndoSnackbar(undoable: Undoable<T>, host: SnackbarHostState, describe: (T) -> String) {
    val undo = stringResource(R.string.core_ui_undo)
    val currentDescribe by rememberUpdatedState(describe)
    LaunchedEffect(undoable, host) {
        // A newer removal replaces the snackbar; its Keep or Undo then carries the newer token.
        undoable.pending.collectLatest { pending ->
            if (pending == null) return@collectLatest
            val result = host.showSnackbar(currentDescribe(pending.removal), actionLabel = undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undoable.undo(pending.token) else undoable.keep(pending.token)
        }
    }
}
