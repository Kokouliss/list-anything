package com.kokouliss.listanything.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.DrawingRef
import com.kokouliss.listanything.core.domain.model.MAX_STROKES
import com.kokouliss.listanything.core.domain.model.Stroke
import com.kokouliss.listanything.core.domain.repository.DrawingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface DrawingUiState {
    data object Loading : DrawingUiState

    /** The drawing was deleted, or this user lost access to its space. The screen closes itself. */
    data object Gone : DrawingUiState

    data class Ready(val drawing: Drawing) : DrawingUiState {
        val isFull: Boolean get() = drawing.strokes.size >= MAX_STROKES
    }
}

@HiltViewModel(assistedFactory = DrawingViewModel.Factory::class)
internal class DrawingViewModel @AssistedInject constructor(
    @Assisted key: DrawingKey,
    private val repository: DrawingRepository,
) : ViewModel() {

    private val ref = DrawingRef(key.spaceId, key.drawingId)

    val uiState: StateFlow<DrawingUiState> = repository.observeDrawing(ref)
        .map { if (it == null) DrawingUiState.Gone else DrawingUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawingUiState.Loading)

    private val ready: DrawingUiState.Ready? get() = uiState.value as? DrawingUiState.Ready

    fun addStroke(stroke: Stroke) {
        // A full page takes no more. The security rules refuse the write as well.
        val ready = ready ?: return
        if (!ready.isFull) viewModelScope.launch { repository.addStroke(ref, stroke) }
    }

    /** Takes the last stroke off the page, whoever drew it. */
    fun undo() {
        val last = ready?.drawing?.strokes?.lastOrNull() ?: return
        viewModelScope.launch { repository.removeStroke(ref, last) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearStrokes(ref) }
    }

    fun delete() {
        viewModelScope.launch { repository.deleteDrawing(ref) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: DrawingKey): DrawingViewModel
    }
}
