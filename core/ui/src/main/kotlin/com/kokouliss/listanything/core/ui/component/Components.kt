package com.kokouliss.listanything.core.ui.component

import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.ui.R
import com.kokouliss.listanything.core.ui.theme.Motion
import com.kokouliss.listanything.core.ui.theme.bubbleColors
import com.kokouliss.listanything.core.ui.theme.rememberReducedMotion

/**
 * What a screen shows while it waits: the logo with its bullets arriving. With animations off on the
 * phone the logo is still, so the word is written under it.
 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.core_ui_loading)
    Column(
        modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLoader(Modifier.size(120.dp))
        if (rememberReducedMotion()) {
            Text(loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Centered icon, title and explanation for a screen that has nothing to show yet. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    /** A small drawing that takes the place of the icon. */
    doodle: Doodle? = null,
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
            if (doodle != null) {
                DoodleImage(doodle)
            } else {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(icon, null, Modifier.size(40.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
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

/** Menu entry colours for an action that deletes something: red, as Material asks. */
@Composable
fun destructiveMenuItemColors(): MenuItemColors = MenuDefaults.itemColors(
    textColor = MaterialTheme.colorScheme.error,
    leadingIconColor = MaterialTheme.colorScheme.error,
)

/**
 * A circle with [photo], the picture the person chose for themselves, or without one the first
 * letter of [name]. Decorative: the name is always shown next to it.
 */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, photo: ImageData? = null) {
    val picture = rememberPicture(photo)
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * [image] ready to draw, or null when there is none or it cannot be read. Decoded where it is
 * called: fine for the small pictures stored on a document, not for a full-size photo.
 */
@Composable
fun rememberPicture(image: ImageData?): ImageBitmap? = remember(image) {
    image?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
}

/**
 * A small ink stamp with the first letter of [name], or their [photo]: who added or checked an
 * item. The same [seed] always gives the same colour, so a person is known by it down the page. A
 * screen reader gets [description] in place of the letter.
 */
@Composable
fun PersonStamp(name: String, seed: Int, description: String, modifier: Modifier = Modifier, photo: ImageData? = null) {
    val (fill, ink) = bubbleColors(seed)
    val picture = rememberPicture(photo)
    Box(
        modifier.size(24.dp).clip(CircleShape).background(fill).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}

/** True while the phone has no way to the internet. Changes made then wait on the phone. */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    return produceState(false) {
        val network = context.getSystemService(ConnectivityManager::class.java)
        value = network.activeNetwork == null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                value = false
            }

            override fun onLost(network: Network) {
                value = true
            }
        }
        network.registerDefaultNetworkCallback(callback)
        awaitDispose { network.unregisterNetworkCallback(callback) }
    }.value
}

/** The logo of the app on the dark tile of its launcher icon. Decorative: the name is shown next to it. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.core_ui_logo_list),
        contentDescription = null,
        modifier = modifier.background(LogoTile, LogoTileShape),
    )
}

// The ink of the AnyKind logos and the corners of their icon tile, 24 of 100.
internal val LogoTile = Color(0xFF171A21)
internal val LogoTileShape = RoundedCornerShape(percent = 24)

/** Gives a little under the finger. [source] is the one the click listens to. */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.PRESS_SCALE else 1f, Motion.momentum(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A floating button that pops in and out, not on and off. */
@Composable
fun PopIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = scaleIn(Motion.momentum()) + fadeIn(), exit = scaleOut() + fadeOut()) { content() }
}

/** The loader while [loading], then [content] fades in over it. A screen that opens loaded just shows. */
@Composable
fun LoadingCrossfade(loading: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Crossfade(loading, modifier, tween(Motion.LONG), label = "loaded") { if (it) LoadingBox() else content() }
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
/** The widest a page of content gets, so rows do not stretch across a tablet. Centred past that. */
fun Modifier.pageWidth(): Modifier = fillMaxWidth().wrapContentWidth().widthIn(max = 840.dp).fillMaxWidth()

private val RuledLineGap = 32.dp

/**
 * Faint ruled lines behind a screen, like the paper of a notepad. Decoration only. [line] is drawn
 * at a small fraction of its strength, so it stays well behind whatever is written on top.
 */
fun Modifier.ruledPaper(line: Color, scrolled: () -> Float = { 0f }): Modifier = drawBehind {
    val gap = RuledLineGap.toPx()
    // The lines travel with the content: the first one starts where the scroll left it.
    var y = (scrolled() % gap + gap) % gap
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), alpha = 0.08f)
        y += gap
    }
}

/** Ruled lines that scroll with the list or column inside, so the writing does not slide over the paper. */
fun Modifier.ruledPaper(line: Color, scroll: PaperScroll): Modifier =
    nestedScroll(scroll).ruledPaper(line) { scroll.offset }

/** The paper behind a list. Each kind of list has its own, so a page says what it is before a word is read. */
enum class Paper { Ruled, Squared, Grid, Dotted, Plain }

/**
 * The pattern of [paper] behind a list, faint like [ruledPaper] and moving with [scroll]. Ruled
 * paper draws nothing here: its lines come from the rows, which are not all one height.
 */
// shortcut: the pattern follows scrolling by finger, not a jump to a row or rows that come and go, so
// it can sit a few pixels off the rows. It lines up with nothing, so nobody sees. Read the list's
// own scroll position if a pattern ever has to meet the rows.
fun Modifier.paper(paper: Paper, line: Color, scroll: PaperScroll): Modifier = when (paper) {
    Paper.Ruled, Paper.Plain -> this
    else -> nestedScroll(scroll).drawBehind {
        // Small squares and dots for writing, wide squares for a map.
        val gap = (if (paper == Paper.Grid) RuledLineGap * 3 else RuledLineGap / 2).toPx()
        val top = (scroll.offset % gap + gap) % gap
        if (paper == Paper.Dotted) {
            val (width, height) = size
            val dots = buildList {
                var y = top
                while (y < height) {
                    var x = gap
                    while (x < width) {
                        add(Offset(x, y))
                        x += gap
                    }
                    y += gap
                }
            }
            // A dot is far less ink than a line, so it is drawn stronger to look as faint.
            drawPoints(dots, PointMode.Points, line, 2.dp.toPx(), StrokeCap.Round, alpha = 0.3f)
        } else {
            var y = top
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), alpha = 0.08f)
                y += gap
            }
            var x = gap
            while (x < size.width) {
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), alpha = 0.08f)
                x += gap
            }
        }
    }
}

/** How far the scrolling content inside has moved, summed from what each scroll consumed. */
@Stable
class PaperScroll : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        offset += consumed.y
        return Offset.Zero
    }
}

@Composable
fun rememberPaperScroll(): PaperScroll = remember { PaperScroll() }

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(AppIcons.ArrowBack, stringResource(R.string.core_ui_back))
    }
}
