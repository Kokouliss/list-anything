package com.kokouliss.listanything.feature.spaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.Drawing
import com.kokouliss.listanything.core.domain.model.MAX_STROKE_POINTS
import com.kokouliss.listanything.core.domain.model.PAGE_HEIGHT
import com.kokouliss.listanything.core.domain.model.Stroke
import com.kokouliss.listanything.core.domain.model.thinnedTo
import com.kokouliss.listanything.core.ui.R as CoreR
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.ConfirmDialog
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.destructiveMenuItemColors
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle

/** The pens, as ARGB and the name a screen reader gets. */
private val Pens = listOf(
    0xFF1F1F1F.toInt() to R.string.feature_spaces_pen_ink,
    0xFFD32F2F.toInt() to R.string.feature_spaces_pen_red,
    0xFF1976D2.toInt() to R.string.feature_spaces_pen_blue,
    0xFF388E3C.toInt() to R.string.feature_spaces_pen_green,
)

/** Pen width as a fraction of the page width, like the stored points. */
private const val PEN_WIDTH = 0.008f

/** How far a finger must move, as a fraction of the page width, before a stroke gets another point. */
private const val MIN_STEP = 0.004f

private enum class DrawingDialog { None, Clear, Delete }

@Composable
internal fun DrawingRoute(viewModel: DrawingViewModel, onBack: () -> Unit, onGone: (message: String) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    if (uiState is DrawingUiState.Gone) {
        LaunchedEffect(Unit) { onGone(resources.getString(R.string.feature_spaces_drawing_gone)) }
    }
    DrawingScreen(
        uiState = uiState,
        onBack = onBack,
        onStroke = viewModel::addStroke,
        onUndo = viewModel::undo,
        onClear = viewModel::clear,
        onDelete = viewModel::delete,
    )
}

@Composable
internal fun DrawingScreen(
    uiState: DrawingUiState,
    onBack: () -> Unit,
    onStroke: (Stroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    val ready = uiState as? DrawingUiState.Ready
    var dialog by rememberSaveable { mutableStateOf(DrawingDialog.None) }
    var pen by rememberSaveable { mutableIntStateOf(0) }
    val closeDialog = { dialog = DrawingDialog.None }

    // Strokes drawn here that the store has not echoed back yet, so a line never blinks when the finger lifts.
    val stored = ready?.drawing?.strokes.orEmpty()
    var pending by remember { mutableStateOf(emptyList<Stroke>()) }
    LaunchedEffect(stored) { pending = pending.filter { it !in stored } }

    val title = ready?.drawing?.title.orEmpty()
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = onUndo, enabled = stored.isNotEmpty()) {
                            AppIcon(AppIcons.Undo, stringResource(R.string.feature_spaces_undo_stroke))
                        }
                        DrawingMenu(onSelect = { dialog = it })
                    }
                },
            )
        },
        bottomBar = { if (ready != null) PenRow(selected = pen, onSelect = { pen = it }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ready == null) {
                LoadingBox()
            } else {
                DrawingPage(
                    strokes = stored + pending.filter { it !in stored },
                    pen = Pens[pen].first,
                    enabled = !ready.isFull,
                    onStroke = { stroke ->
                        pending = pending + stroke
                        onStroke(stroke)
                    },
                    modifier = Modifier.weight(1f, fill = false).aspectRatio(1f / PAGE_HEIGHT),
                )
                if (ready.isFull) {
                    Text(
                        stringResource(R.string.feature_spaces_page_full),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (dialog) {
        DrawingDialog.None -> Unit
        DrawingDialog.Clear -> ConfirmDialog(
            title = stringResource(R.string.feature_spaces_clear_page),
            text = stringResource(R.string.feature_spaces_clear_page_confirm_text),
            confirmLabel = stringResource(R.string.feature_spaces_clear),
            onConfirm = {
                closeDialog()
                onClear()
            },
            onDismiss = closeDialog,
        )
        DrawingDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.feature_spaces_delete_drawing),
            text = stringResource(R.string.feature_spaces_delete_drawing_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onDelete()
            },
            onDismiss = closeDialog,
        )
    }
}

@Composable
private fun DrawingMenu(onSelect: (DrawingDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val select = { target: DrawingDialog ->
        expanded = false
        onSelect(target)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_spaces_clear_page)) },
                leadingIcon = { AppIcon(AppIcons.DeleteSweep, null) },
                onClick = { select(DrawingDialog.Clear) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_spaces_delete_drawing)) },
                leadingIcon = { AppIcon(AppIcons.Delete, null) },
                colors = destructiveMenuItemColors(),
                onClick = { select(DrawingDialog.Delete) },
            )
        }
    }
}

/** The pens as coloured dots; the chosen one wears a ring. */
@Composable
private fun PenRow(selected: Int, onSelect: (Int) -> Unit) {
    BottomAppBar {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pens.forEachIndexed { index, (color, name) ->
                val isSelected = index == selected
                val label = stringResource(name)
                IconButton(
                    onClick = { onSelect(index) },
                    modifier = Modifier.semantics {
                        contentDescription = label
                        this.selected = isSelected
                    },
                ) {
                    val ring = if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                    Box(Modifier.size(32.dp).then(ring), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)))
                    }
                }
            }
        }
    }
}

/** A white sheet to draw on with a finger. Strokes are handed over as the finger lifts. */
@Composable
private fun DrawingPage(
    strokes: List<Stroke>,
    pen: Int,
    enabled: Boolean,
    onStroke: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = remember(strokes) { strokes.toInk() }
    // The stroke under the finger, in pixels, until it is handed over.
    var current by remember { mutableStateOf(emptyList<Offset>()) }
    val description = stringResource(R.string.feature_spaces_drawing_page)
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(enabled, pen) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val points = mutableListOf(down.position)
                        current = points.toList()
                        drag(down.id) { change ->
                            change.consume()
                            // A resting finger reports many positions a hair apart; keep the ones that moved.
                            if ((change.position - points.last()).getDistance() >= size.width * MIN_STEP) {
                                points += change.position
                                current = points.toList()
                            }
                        }
                        current = emptyList()
                        onStroke(points.toStroke(pen, size.width.toFloat()))
                    }
                },
        ) {
            drawInk(ink)
            if (current.isNotEmpty()) drawInk(listOf(current.toStroke(pen, size.width)).toInk())
        }
    }
}

/** A small picture of a drawing, for its card. */
@Composable
internal fun DrawingPreview(strokes: List<Stroke>, modifier: Modifier = Modifier) {
    val ink = remember(strokes) { strokes.toInk() }
    Canvas(modifier.background(Color.White)) { drawInk(ink) }
}

/** Page coordinates for finger positions on a page [width] pixels wide. */
private fun List<Offset>.toStroke(pen: Int, width: Float): Stroke =
    Stroke(pen, thinnedTo(MAX_STROKE_POINTS).flatMap { listOf((it.x / width).rounded(), (it.y / width).rounded()) })

/** Four decimals: a thousandth of the page is finer than a finger, and the same on every device. */
private fun Float.rounded(): Float = (this * 10_000).roundToInt() / 10_000f

private fun List<Stroke>.toInk(): List<Pair<Color, Path>> = map { stroke ->
    val path = Path()
    for (i in 0 until stroke.points.size - 1 step 2) {
        if (i == 0) path.moveTo(stroke.points[0], stroke.points[1]) else path.lineTo(stroke.points[i], stroke.points[i + 1])
    }
    // A tap is a dot: a round-capped line of no length draws as a circle.
    if (stroke.points.size == 2) path.lineTo(stroke.points[0], stroke.points[1])
    Color(stroke.color) to path
}

/** Draws page-unit paths on this canvas, scaled to its width. */
private fun DrawScope.drawInk(ink: List<Pair<Color, Path>>) {
    scale(size.width, size.width, pivot = Offset.Zero) {
        ink.forEach { (color, path) ->
            drawPath(path, color, style = StrokeStyle(PEN_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@PreviewLightDark
@Composable
private fun DrawingScreenPreview() {
    ListAnythingTheme {
        DrawingScreen(
            uiState = DrawingUiState.Ready(
                Drawing(
                    "d1", "s1", "Garden plan",
                    listOf(
                        Stroke(Pens[0].first, listOf(0.1f, 0.2f, 0.5f, 0.25f, 0.9f, 0.2f)),
                        Stroke(Pens[2].first, listOf(0.2f, 0.6f, 0.3f, 0.9f, 0.6f, 1.0f)),
                        Stroke(Pens[1].first, listOf(0.7f, 0.5f)),
                    ),
                    "Panos",
                ),
            ),
            onBack = {},
            onStroke = {},
            onUndo = {},
            onClear = {},
            onDelete = {},
        )
    }
}
