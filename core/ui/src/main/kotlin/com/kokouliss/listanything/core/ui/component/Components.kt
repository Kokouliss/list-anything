package com.kokouliss.listanything.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.ui.R
import com.kokouliss.listanything.core.ui.theme.NOTES_ALPHA
import com.kokouliss.listanything.core.ui.theme.NotePapers

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Centered icon, title and explanation for a screen that has nothing to show yet. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, null, Modifier.size(40.dp), MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        actions()
    }
}

/** A circle with the first letter of [name]. Decorative: the name is always shown next to it. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** The logo of the app on the dark tile of its launcher icon. Decorative: the name is shown next to it. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.core_ui_logo_list),
        contentDescription = null,
        // The ink of the AnyKind logos and the corners of their icon tile, 24 of 100.
        modifier = modifier.background(Color(0xFF171A21), RoundedCornerShape(percent = 24)),
    )
}

/** The logo of AnyKind, the collection this app belongs to. Its letters take the colour of the text around it. */
@Composable
fun AnyKindWordmark(modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(painterResource(R.drawable.core_ui_logo_anykind_mark), contentDescription = "AnyKind")
        Image(
            painter = painterResource(R.drawable.core_ui_logo_anykind_letters),
            contentDescription = null,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
        )
    }
}

/** A sticky note of the backdrop: its centre as a share of the screen, its side and its tilt in degrees. */
private class BackdropNote(val x: Float, val y: Float, val side: Dp, val tilt: Float)

// Mostly along the edges, where they peek in from the side and stay clear of centred text.
private val BackdropNotes = listOf(
    BackdropNote(0.08f, 0.08f, 96.dp, -12f),
    BackdropNote(0.92f, 0.20f, 120.dp, 9f),
    BackdropNote(0.03f, 0.46f, 132.dp, 7f),
    BackdropNote(0.97f, 0.60f, 104.dp, -8f),
    BackdropNote(0.24f, 0.86f, 116.dp, -5f),
    BackdropNote(0.94f, 0.93f, 88.dp, 14f),
)

/**
 * Faint sticky notes scattered behind a screen, each with three lines of writing in [ink]. Decoration
 * only: they say nothing and cannot be pressed.
 */
fun Modifier.notesBackdrop(ink: Color): Modifier = drawBehind {
    BackdropNotes.forEachIndexed { index, note ->
        val side = note.side.toPx()
        val corner = Offset(size.width * note.x - side / 2, size.height * note.y - side / 2)
        rotate(note.tilt, pivot = corner + Offset(side / 2, side / 2)) {
            drawRoundRect(
                color = NotePapers[index % NotePapers.size],
                topLeft = corner,
                size = Size(side, side),
                cornerRadius = CornerRadius(side / 12),
                alpha = NOTES_ALPHA,
            )
            repeat(3) { line ->
                val y = corner.y + side * (0.32f + 0.18f * line)
                // The last line stops early, as writing does.
                val end = if (line == 2) 0.52f else 0.82f
                drawLine(
                    color = ink,
                    start = Offset(corner.x + side * 0.18f, y),
                    end = Offset(corner.x + side * end, y),
                    strokeWidth = side / 32,
                    cap = StrokeCap.Round,
                    alpha = NOTES_ALPHA / 3,
                )
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(AppIcons.ArrowBack, stringResource(R.string.core_ui_back))
    }
}
