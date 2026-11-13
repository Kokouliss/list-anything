package com.kokouliss.listanything.feature.spaces

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.DrawingRef
import com.kokouliss.listanything.core.domain.model.MAX_STROKES
import com.kokouliss.listanything.core.domain.model.Stroke
import com.kokouliss.listanything.core.domain.repository.DrawingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class DrawingViewModelTest {

    private val repository = FakeDrawingRepository()
    private val viewModel by lazy { DrawingViewModel(DrawingKey("s1", "d1"), repository) }
    private val line = Stroke(0xFF1F1F1F.toInt(), listOf(0.1f, 0.1f, 0.5f, 0.5f))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `undo takes the last stroke off the page and a full page takes no more`() = runTest {
        val dot = Stroke(0xFFD32F2F.toInt(), listOf(0.2f, 0.2f))
        repository.drawing.value = drawing(listOf(line, dot))

        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(DrawingUiState.Ready(drawing(listOf(line, dot))))
            viewModel.undo()
            assertThat(repository.removed).containsExactly(dot)

            repository.drawing.value = drawing(List(MAX_STROKES) { line })
            assertThat((expectMostRecentItem() as DrawingUiState.Ready).isFull).isTrue()
            viewModel.addStroke(dot)
            assertThat(repository.added).isEmpty()
        }
    }

    @Test
    fun `a deleted drawing is reported as gone`() = runTest {
        repository.drawing.value = drawing(emptyList())

        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isInstanceOf(DrawingUiState.Ready::class.java)
            repository.drawing.value = null
            assertThat(awaitItem()).isEqualTo(DrawingUiState.Gone)
        }
    }

    private fun drawing(strokes: List<Stroke>) = Drawing("d1", "s1", "Garden", strokes, "Panos")
}

private class FakeDrawingRepository : DrawingRepository {
    val drawing = MutableStateFlow<Drawing?>(null)
    val added = mutableListOf<Stroke>()
    val removed = mutableListOf<Stroke>()

    override fun observeDrawings(spaceId: String): Flow<List<Drawing>> = flowOf(emptyList())
    override fun observeDrawing(ref: DrawingRef): Flow<Drawing?> = drawing
    override suspend fun createDrawing(spaceId: String, title: String) = "new"
    override suspend fun addStroke(ref: DrawingRef, stroke: Stroke) {
        added += stroke
    }
    override suspend fun removeStroke(ref: DrawingRef, stroke: Stroke) {
        removed += stroke
    }
    override suspend fun clearStrokes(ref: DrawingRef) = Unit
    override suspend fun deleteDrawing(ref: DrawingRef) = Unit
}
