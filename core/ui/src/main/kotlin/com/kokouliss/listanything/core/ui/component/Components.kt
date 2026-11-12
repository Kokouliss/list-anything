package com.kokouliss.listanything.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.ui.R

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.core_ui_loading)
    Box(
        modifier.fillMaxSize().semantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center,
    ) {
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
    // Centred while it fits; scrolls once large text makes it taller than the screen.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(32.dp),
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

/** How far apart the lines of the paper are: a line of writing with room around it. */
private val RuledLineGap = 32.dp

/**
 * Faint ruled lines behind a screen, like the paper of a notepad. Decoration only. [line] is drawn
 * at a fifth of its strength, so it stays behind whatever is written on top.
 */
fun Modifier.ruledPaper(line: Color): Modifier = drawBehind {
    val gap = RuledLineGap.toPx()
    var y = gap
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), alpha = 0.2f)
        y += gap
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(AppIcons.ArrowBack, stringResource(R.string.core_ui_back))
    }
}
