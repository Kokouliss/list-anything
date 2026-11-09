package com.kokouliss.listanything.feature.lists

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.ConfirmDialog
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.ListEditorDialog
import com.kokouliss.listanything.core.ui.component.ListEmojis
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import java.io.File
import com.kokouliss.listanything.core.ui.R as CoreR

internal const val MAX_ITEM_LENGTH = 200
internal const val MAX_PLACE_NAME_LENGTH = 80

private enum class ListDialog { None, EditList, ClearChecked, DeleteList }

@Composable
internal fun ListDetailRoute(viewModel: ListDetailViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A toast, because these messages must also show above the open item sheet.
    val toast = { message: Int -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    if (uiState is ListDetailUiState.Gone) {
        LaunchedEffect(Unit) { onBack() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ListDetailEvent.PhotoFailed -> toast(R.string.feature_lists_photo_failed)
            }
        }
    }

    // The item a camera or gallery request belongs to. Saved, because Android may recreate the
    // screen while the camera app is in front.
    var photoItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val onPhotoResult = { uri: Uri? ->
        val itemId = photoItemId
        if (uri != null && itemId != null) viewModel.onAction(ListDetailAction.SetPhoto(itemId, uri.toString()))
        photoItemId = null
    }
    val captureUri = remember { captureUri(context) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        onPhotoResult(captureUri.takeIf { saved })
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onPhotoResult)

    ListDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onAction = viewModel::onAction,
        onTakePhoto = { itemId ->
            photoItemId = itemId
            try {
                camera.launch(captureUri)
            } catch (e: ActivityNotFoundException) {
                toast(R.string.feature_lists_no_app)
            }
        },
        onChoosePhoto = { itemId ->
            photoItemId = itemId
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onOpenInMaps = { item ->
            item.location?.let { location ->
                val pin = Uri.encode("${location.latitude},${location.longitude}(${location.name ?: item.text})")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$pin")))
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.feature_lists_no_app)
                }
            }
        },
    )
}

/** Where the camera app writes a new picture. One file is enough: it is shrunk and stored right away. */
private fun captureUri(context: Context): Uri {
    val file = File(context.cacheDir, "photos/capture.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
internal fun ListDetailScreen(
    uiState: ListDetailUiState,
    onBack: () -> Unit,
    onAction: (ListDetailAction) -> Unit,
    onTakePhoto: (itemId: String) -> Unit,
    onChoosePhoto: (itemId: String) -> Unit,
    onOpenInMaps: (ListItem) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(ListDialog.None) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var editingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var placeItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val closeDialog = { dialog = ListDialog.None }

    val ready = uiState as? ListDetailUiState.Ready
    val items = ready?.items.orEmpty()
    val pinned = remember(items) { items.filter { it.location != null } }
    val mapVisible = showMap && pinned.isNotEmpty()

    val placeItem = items.firstOrNull { it.id == placeItemId }
    if (placeItem != null) {
        LocationPicker(
            initial = placeItem.location,
            nearby = pinned.firstOrNull()?.location,
            onConfirm = { location ->
                onAction(ListDetailAction.SetLocation(placeItem.id, location))
                placeItemId = null
            },
            onCancel = { placeItemId = null },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            ready?.list?.let { "${it.emoji} ${it.name}" }.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (pinned.isNotEmpty()) {
                            IconButton(onClick = { showMap = !mapVisible }) {
                                if (mapVisible) {
                                    AppIcon(AppIcons.Checklist, stringResource(R.string.feature_lists_show_list))
                                } else {
                                    AppIcon(AppIcons.Map, stringResource(R.string.feature_lists_show_map))
                                }
                            }
                        }
                        if (ready != null) {
                            ListMenu(hasCheckedItems = items.any { it.isChecked }, onSelect = { dialog = it })
                        }
                    },
                )
            },
            bottomBar = {
                if (ready != null && !mapVisible) {
                    AddItemBar(onAdd = { onAction(ListDetailAction.AddItem(it)) })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    ready == null -> LoadingBox()
                    mapVisible -> PinsMap(pinned, onPinClick = { editingItemId = it }, Modifier.fillMaxSize())
                    items.isEmpty() -> EmptyState(
                        icon = AppIcons.Checklist,
                        title = stringResource(R.string.feature_lists_empty_title),
                        body = stringResource(R.string.feature_lists_empty_body),
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items, key = { it.id }) { item ->
                            ItemRow(
                                item = item,
                                isPhotoBusy = item.id in ready.photoBusyItemIds,
                                onCheckedChange = { onAction(ListDetailAction.SetChecked(item.id, it)) },
                                onClick = { editingItemId = item.id },
                                onPhotoClick = { onAction(ListDetailAction.OpenPhoto(item.id)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    // Looked up on every change, so the sheet closes by itself when someone else deletes the item.
    val editingItem = items.firstOrNull { it.id == editingItemId }
    if (ready != null && editingItem != null) {
        ItemEditorSheet(
            item = editingItem,
            isPhotoBusy = editingItem.id in ready.photoBusyItemIds,
            onAction = onAction,
            onDismiss = { editingItemId = null },
            onTakePhoto = { onTakePhoto(editingItem.id) },
            onChoosePhoto = { onChoosePhoto(editingItem.id) },
            onPickPlace = {
                editingItemId = null
                placeItemId = editingItem.id
            },
            onOpenInMaps = { onOpenInMaps(editingItem) },
        )
    }

    ready?.photoViewer?.let { viewer ->
        PhotoViewerDialog(
            viewer = viewer,
            itemText = items.firstOrNull { it.id == viewer.itemId }?.text.orEmpty(),
            onClose = { onAction(ListDetailAction.ClosePhoto) },
        )
    }

    when (dialog) {
        ListDialog.None -> Unit
        ListDialog.EditList -> ListEditorDialog(
            title = stringResource(R.string.feature_lists_edit_list),
            confirmLabel = stringResource(CoreR.string.core_ui_save),
            initialName = ready?.list?.name.orEmpty(),
            initialEmoji = ready?.list?.emoji ?: ListEmojis.first(),
            onConfirm = { name, emoji ->
                onAction(ListDetailAction.UpdateList(name, emoji))
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        ListDialog.ClearChecked -> ConfirmDialog(
            title = stringResource(R.string.feature_lists_clear_checked),
            text = stringResource(R.string.feature_lists_clear_checked_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onAction(ListDetailAction.ClearChecked)
            },
            onDismiss = closeDialog,
        )
        ListDialog.DeleteList -> ConfirmDialog(
            title = stringResource(R.string.feature_lists_delete_list),
            text = stringResource(R.string.feature_lists_delete_list_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onAction(ListDetailAction.DeleteList)
            },
            onDismiss = closeDialog,
        )
    }
}

@Composable
private fun ItemRow(
    item: ListItem,
    isPhotoBusy: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.feature_lists_edit_item), onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            // Without this a screen reader announces a checkbox with no name.
            modifier = Modifier.semantics { contentDescription = item.text },
        )
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val checkedBy = item.checkedByName
            if (item.isChecked && checkedBy != null) {
                Detail(stringResource(R.string.feature_lists_checked_by, checkedBy))
            }
            item.location?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppIcons.Location, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Detail(location.name ?: stringResource(R.string.feature_lists_pinned_place), Modifier.padding(start = 4.dp))
                }
            }
        }
        val thumbnail = rememberBitmap(item.thumbnail)
        when {
            isPhotoBusy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            thumbnail != null -> Image(
                bitmap = thumbnail,
                contentDescription = stringResource(R.string.feature_lists_photo_description, item.text),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.feature_lists_view_photo), onClick = onPhotoClick),
            )
        }
    }
}

@Composable
private fun Detail(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun rememberBitmap(image: ImageData?): ImageBitmap? = remember(image) {
    image?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
}

@Composable
private fun AddItemBar(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                // Keeps the field above the keyboard and the gesture bar.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_ITEM_LENGTH) },
                placeholder = { Text(stringResource(R.string.feature_lists_add_item_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                // The keyboard stays open, so a whole shopping list can be typed in one go.
                keyboardActions = KeyboardActions(onSend = { add() }),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = add, enabled = text.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) {
                AppIcon(AppIcons.Add, stringResource(R.string.feature_lists_add_item))
            }
        }
    }
}

@Composable
private fun ListMenu(hasCheckedItems: Boolean, onSelect: (ListDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val select = { target: ListDialog ->
        expanded = false
        onSelect(target)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_lists_edit_list)) },
                leadingIcon = { AppIcon(AppIcons.Edit, null) },
                onClick = { select(ListDialog.EditList) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_lists_clear_checked)) },
                leadingIcon = { AppIcon(AppIcons.DeleteSweep, null) },
                enabled = hasCheckedItems,
                onClick = { select(ListDialog.ClearChecked) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_lists_delete_list)) },
                leadingIcon = { AppIcon(AppIcons.Delete, null) },
                onClick = { select(ListDialog.DeleteList) },
            )
        }
    }
}

@Composable
private fun ItemEditorSheet(
    item: ListItem,
    isPhotoBusy: Boolean,
    onAction: (ListDetailAction) -> Unit,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onPickPlace: () -> Unit,
    onOpenInMaps: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            var text by rememberSaveable(item.id) { mutableStateOf(item.text) }
            val canSave = text.isNotBlank() && text.trim() != item.text
            val save = { if (canSave) onAction(ListDetailAction.UpdateText(item.id, text.trim())) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_ITEM_LENGTH) },
                label = { Text(stringResource(R.string.feature_lists_item_label)) },
                supportingText = { Text(stringResource(R.string.feature_lists_added_by, item.createdByName)) },
                trailingIcon = {
                    if (canSave) {
                        IconButton(onClick = save) {
                            AppIcon(AppIcons.Check, stringResource(CoreR.string.core_ui_save))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )

            SheetAction(AppIcons.Camera, R.string.feature_lists_take_photo, onTakePhoto, enabled = !isPhotoBusy)
            SheetAction(AppIcons.Image, R.string.feature_lists_choose_photo, onChoosePhoto, enabled = !isPhotoBusy)
            if (item.thumbnail != null) {
                SheetAction(AppIcons.Close, R.string.feature_lists_remove_photo, onClick = {
                    onAction(ListDetailAction.RemovePhoto(item.id))
                })
            }
            HorizontalDivider()
            if (item.location == null) {
                SheetAction(AppIcons.Location, R.string.feature_lists_add_place, onPickPlace)
            } else {
                SheetAction(AppIcons.Location, R.string.feature_lists_change_place, onPickPlace)
                SheetAction(AppIcons.OpenInNew, R.string.feature_lists_open_in_maps, onOpenInMaps)
                SheetAction(AppIcons.Close, R.string.feature_lists_remove_place, onClick = {
                    onAction(ListDetailAction.SetLocation(item.id, null))
                })
            }
            HorizontalDivider()
            SheetAction(AppIcons.Delete, R.string.feature_lists_delete_item, onClick = {
                onAction(ListDetailAction.DeleteItem(item.id))
            })
        }
    }
}

@Composable
private fun SheetAction(icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AppIcon(icon, null) },
        headlineContent = { Text(stringResource(label)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun PhotoViewerDialog(viewer: PhotoViewer, itemText: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            // shortcut: the photo (at most 1280 px) is decoded on the main thread when it opens.
            // Decode it in the view model if opening a photo ever stutters.
            val bitmap = rememberBitmap(viewer.image)
            when {
                viewer.isLoading -> CircularProgressIndicator(color = Color.White)
                bitmap == null -> Text(
                    stringResource(R.string.feature_lists_photo_not_synced),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                else -> Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.feature_lists_photo_description, itemText),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                AppIcon(AppIcons.Close, stringResource(R.string.feature_lists_close), tint = Color.White)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ListDetailScreenPreview() {
    ListAnythingTheme(dynamicColor = false) {
        ListDetailScreen(
            uiState = ListDetailUiState.Ready(
                list = SharedList("l1", "s1", "Supermarket", "🛒"),
                items = listOf(
                    ListItem("1", "Shampoo, the green one", false, null, "Panos", null, null, 1),
                    ListItem("2", "Taverna by the harbour", false, null, "Maria", null, ItemLocation(35.5, 24.0, "To Limani"), 2),
                    ListItem("3", "Milk", true, "Maria", "Panos", null, null, 3),
                ),
                photoBusyItemIds = emptySet(),
                photoViewer = null,
            ),
            onBack = {},
            onAction = {},
            onTakePhoto = {},
            onChoosePhoto = {},
            onOpenInMaps = {},
        )
    }
}
