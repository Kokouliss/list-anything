package com.kokouliss.listanything.core.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.ui.R
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded off the main thread, so a list of photos scrolls without a hitch. Null until then. */
@Composable
fun rememberBitmap(image: ImageData?): ImageBitmap? = produceState<ImageBitmap?>(null, image) {
    value = image?.let {
        withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
    }
}.value

/**
 * A full-size photo over the whole screen, of a list item or a chat message. [image] stays null
 * when it could not be loaded; a screen reader gets [description].
 */
@Composable
fun PhotoDialog(image: ImageData?, isLoading: Boolean, description: String, onClose: () -> Unit) {
    // Always the night notepad: a photo sits on the night paper, never on pure black.
    ListAnythingTheme(darkTheme = true) {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                val bitmap = rememberBitmap(image)
                when {
                    isLoading -> CircularProgressIndicator()
                    bitmap == null -> Text(
                        stringResource(R.string.core_ui_photo_not_synced),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                    else -> {
                        // Pinch to zoom and drag to look around; letting go at the smallest size centres it again.
                        // shortcut: the drag is not kept within the edges, so a zoomed photo can be pushed off
                        // screen. Clamp the offset if that gets in the way.
                        var scale by remember { mutableFloatStateOf(1f) }
                        var pan by remember { mutableStateOf(Offset.Zero) }
                        val zoom = rememberTransformableState { zoomChange, panChange, _ ->
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            pan = if (scale == 1f) Offset.Zero else pan + panChange
                        }
                        Image(
                            bitmap = bitmap,
                            contentDescription = description,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = pan.x
                                    translationY = pan.y
                                }
                                .transformable(zoom),
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    AppIcon(AppIcons.Close, stringResource(R.string.core_ui_close), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
