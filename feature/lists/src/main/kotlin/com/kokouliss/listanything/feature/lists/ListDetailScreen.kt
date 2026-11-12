package com.kokouliss.listanything.feature.lists

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.MAX_ITEM_LENGTH
import com.kokouliss.listanything.core.domain.model.MAX_LINK_LENGTH
import com.kokouliss.listanything.core.domain.model.MAX_NOTE_LENGTH
import com.kokouliss.listanything.core.domain.model.MAX_QUANTITY
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.normalizeLink
import com.kokouliss.listanything.core.domain.model.orderAfterMove
import com.kokouliss.listanything.core.domain.model.parsePriceCents
import com.kokouliss.listanything.core.domain.model.totalCents
import com.kokouliss.listanything.core.ui.R as CoreR
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.ConfirmDialog
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.ListEditorDialog
import com.kokouliss.listanything.core.ui.component.ListEmojis
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.TextInputDialog
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.theme.marginLineColor
import java.io.File
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal const val MAX_PLACE_NAME_LENGTH = 80
private const val MILLIS_PER_DAY = 86_400_000L

/** Where the red margin line of the page runs: right after the checkboxes. */
private val PageMargin = 52.dp

/** The room between the margin line and the writing, about what the checkbox leaves on its side. */
private val MarginGap = 12.dp

/** The line under a row of ruled paper. */
private fun Modifier.ruledLine(color: Color) = drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

private enum class ListDialog { None, EditList, AddSection, ClearChecked, DeleteList }

// shortcut: prices are always euros. Store a currency on the list when another one is needed.
private fun formatPrice(cents: Long, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") }.format(cents / 100.0)

/** A price the way it is typed into the price field, with the comma or point of the language. */
private fun priceInput(cents: Long, locale: Locale): String =
    "${cents / 100}${DecimalFormatSymbols.getInstance(locale).decimalSeparator}${(cents % 100).toString().padStart(2, '0')}"

private fun formatDay(epochDay: Long, locale: Locale): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

@Composable
internal fun ListDetailRoute(viewModel: ListDetailViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(message: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
    }

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
    LaunchedEffect(viewModel) {
        // A newer removal replaces the snackbar; its Keep or Undo then carries the newer token.
        viewModel.pendingRemoval.collectLatest { pending ->
            if (pending == null) return@collectLatest
            val result = snackbarHostState.showSnackbar(
                message = context.resources.describe(pending.removal),
                actionLabel = context.getString(R.string.feature_lists_undo),
                duration = SnackbarDuration.Short,
            )
            viewModel.onAction(
                if (result == SnackbarResult.ActionPerformed) {
                    ListDetailAction.UndoRemoval(pending.token)
                } else {
                    ListDetailAction.KeepRemoval(pending.token)
                },
            )
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
    val openInOtherApp = { uri: String ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.feature_lists_no_app)
        }
    }

    ListDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
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
                openInOtherApp("geo:0,0?q=$pin")
            }
        },
        onOpenLink = openInOtherApp,
    )
}

private fun Resources.describe(removal: Removal): String = when (removal) {
    is Removal.Items -> when {
        removal.itemIds.size > 1 -> getQuantityString(
            R.plurals.feature_lists_checked_removed,
            removal.itemIds.size,
            removal.itemIds.size,
        )
        removal.isSection -> getString(R.string.feature_lists_section_deleted)
        else -> getString(R.string.feature_lists_item_deleted)
    }
    is Removal.Photo -> getString(R.string.feature_lists_photo_removed)
    is Removal.Place -> getString(R.string.feature_lists_place_removed)
    is Removal.DueDay -> getString(R.string.feature_lists_due_removed)
}

/** Where the camera app writes a new picture. One file is enough: it is shrunk and stored right away. */
private fun captureUri(context: Context): Uri {
    val file = File(context.cacheDir, "photos/capture.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
internal fun ListDetailScreen(
    uiState: ListDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (ListDetailAction) -> Unit,
    onTakePhoto: (itemId: String) -> Unit,
    onChoosePhoto: (itemId: String) -> Unit,
    onOpenInMaps: (ListItem) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(ListDialog.None) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var editingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var placeItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var addSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val closeDialog = { dialog = ListDialog.None }

    val ready = uiState as? ListDetailUiState.Ready
    val items = ready?.items.orEmpty()
    val pinned = remember(items) { items.filter { it.location != null } }
    val mapVisible = showMap && pinned.isNotEmpty()
    // Looked up on every change, so new items stop going to a section that someone deleted.
    val addSection = items.firstOrNull { it.id == addSectionId && it.isHeader }

    // Looked up on every change, so the sheet closes by itself when someone else deletes the item.
    val editingItem = items.firstOrNull { it.id == editingItemId }

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
                            ListMenu(
                                hasCheckedItems = items.any { it.isChecked },
                                // Only one person can be the one the gifts are for.
                                isMyWishList = ready.hidesClaims.takeIf { it || ready.list.giftForId == null },
                                onToggleWishList = { onAction(ListDetailAction.SetGiftForMe(!ready.hidesClaims)) },
                                onSelect = { dialog = it },
                            )
                        }
                    },
                )
            },
            // The open sheet covers this host, so it shows its own copy of the snackbar instead.
            snackbarHost = { if (editingItem == null) SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (ready != null && !mapVisible) {
                    AddItemBar(
                        total = totalText(items),
                        sectionName = addSection?.text,
                        onClearSection = { addSectionId = null },
                        onAdd = { onAction(ListDetailAction.AddItem(it, addSection?.id)) },
                    )
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
                    else -> ItemList(
                        state = ready,
                        onAction = onAction,
                        onEdit = { editingItemId = it },
                        onAddToSection = { addSectionId = it },
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }

    if (ready != null && editingItem != null) {
        ItemEditorSheet(
            item = editingItem,
            state = ready,
            snackbarHostState = snackbarHostState,
            onAction = onAction,
            onDismiss = { editingItemId = null },
            onTakePhoto = { onTakePhoto(editingItem.id) },
            onChoosePhoto = { onChoosePhoto(editingItem.id) },
            onPickPlace = {
                editingItemId = null
                placeItemId = editingItem.id
            },
            onOpenInMaps = { onOpenInMaps(editingItem) },
            onOpenLink = { editingItem.link?.let(onOpenLink) },
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
        ListDialog.AddSection -> TextInputDialog(
            title = stringResource(R.string.feature_lists_add_section),
            label = stringResource(R.string.feature_lists_section_label),
            confirmLabel = stringResource(R.string.feature_lists_add_item),
            supportingText = stringResource(R.string.feature_lists_section_hint),
            onConfirm = {
                onAction(ListDetailAction.AddSection(it))
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
private fun totalText(items: List<ListItem>): String? {
    val total = items.totalCents() ?: return null
    val left = items.filter { !it.isChecked }.totalCents() ?: 0
    val locale = LocalConfiguration.current.locales[0]
    return if (left == total) {
        stringResource(R.string.feature_lists_total, formatPrice(total, locale))
    } else {
        stringResource(R.string.feature_lists_total_left, formatPrice(total, locale), formatPrice(left, locale))
    }
}

@Composable
private fun ItemList(
    state: ListDetailUiState.Ready,
    onAction: (ListDetailAction) -> Unit,
    onEdit: (itemId: String) -> Unit,
    onAddToSection: (headerId: String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    // The rows as they look during a drag. The stored order takes over once the drop is saved.
    var shown by remember(state.items) { mutableStateOf(state.items) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        shown = shown.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val saveOrder = { rows: List<ListItem>, itemId: String ->
        orderAfterMove(rows, itemId)?.let { onAction(ListDetailAction.SetOrder(itemId, it)) }
    }
    val reorderLabel = stringResource(R.string.feature_lists_reorder)
    val moveUpLabel = stringResource(R.string.feature_lists_move_up)
    val moveDownLabel = stringResource(R.string.feature_lists_move_down)

    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    val marginColor = marginLineColor()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
        // A page of ruled paper: a line on top, and the red margin from top to bottom.
        modifier = Modifier.fillMaxSize().drawBehind {
            val x = if (layoutDirection == LayoutDirection.Ltr) PageMargin.toPx() else size.width - PageMargin.toPx()
            drawLine(marginColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawLine(ruleColor, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        },
    ) {
        items(shown, key = { it.id }) { item ->
            ReorderableItem(reorderState, key = item.id) {
                // Dragging needs a steady finger, so a screen reader gets the same move as two actions.
                val moveBy = { delta: Int ->
                    val from = shown.indexOf(item)
                    val to = from + delta
                    if (to in shown.indices) saveOrder(shown.toMutableList().apply { add(to, removeAt(from)) }, item.id)
                    true
                }
                val dragHandle = @Composable {
                    Box(
                        Modifier
                            .size(48.dp)
                            .draggableHandle(onDragStopped = { saveOrder(shown, item.id) })
                            .semantics {
                                contentDescription = reorderLabel
                                customActions = listOf(
                                    CustomAccessibilityAction(moveUpLabel) { moveBy(-1) },
                                    CustomAccessibilityAction(moveDownLabel) { moveBy(1) },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Quieter than the writing: every row has one.
                        AppIcon(AppIcons.DragHandle, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                if (item.isHeader) {
                    SectionRow(
                        item = item,
                        onClick = { onEdit(item.id) },
                        onAddHere = { onAddToSection(item.id) },
                        dragHandle = dragHandle,
                        modifier = Modifier.ruledLine(ruleColor),
                    )
                } else {
                    ItemRow(
                        item = item,
                        showsClaim = !state.hidesClaims,
                        isPhotoBusy = item.id in state.photoBusyItemIds,
                        onCheckedChange = { onAction(ListDetailAction.SetChecked(item.id, it)) },
                        onClick = { onEdit(item.id) },
                        onLinkClick = { item.link?.let(onOpenLink) },
                        onPhotoClick = { onAction(ListDetailAction.OpenPhoto(item.id)) },
                        dragHandle = dragHandle,
                        modifier = Modifier.ruledLine(ruleColor),
                    )
                }
            }
        }
    }
}

/** A section heading. The plus sends new items to this section. */
@Composable
private fun SectionRow(
    item: ListItem,
    onClick: () -> Unit,
    onAddHere: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.feature_lists_edit_section), onClick = onClick)
            // Starts where the item texts start, right of the margin line.
            .padding(start = PageMargin + MarginGap, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.text,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onAddHere) {
            AppIcon(
                AppIcons.Add,
                stringResource(R.string.feature_lists_add_to_section, item.text),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        dragHandle()
    }
}

@Composable
private fun ItemRow(
    item: ListItem,
    showsClaim: Boolean,
    isPhotoBusy: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLinkClick: () -> Unit,
    onPhotoClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.feature_lists_edit_item), onClick = onClick)
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            // Without this a screen reader announces a checkbox with no name.
            modifier = Modifier.semantics { contentDescription = item.text },
        )
        Column(Modifier.weight(1f).padding(start = MarginGap, end = 4.dp)) {
            Text(
                if (item.quantity > 1) "${item.quantity} × ${item.text}" else item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val checkedBy = item.checkedByName
            if (item.isChecked && checkedBy != null) {
                Detail(stringResource(R.string.feature_lists_checked_by, checkedBy))
            }
            item.note?.let { Detail(it) }
            val locale = LocalConfiguration.current.locales[0]
            val dueDay = item.dueDay
            val facts = listOfNotNull(
                item.priceCents?.let { formatPrice(it * item.quantity, locale) },
                dueDay?.let { stringResource(R.string.feature_lists_due, formatDay(it, locale)) },
                item.claimedByName?.takeIf { showsClaim }?.let { stringResource(R.string.feature_lists_taken_by, it) },
            )
            if (facts.isNotEmpty()) {
                val isOverdue = !item.isChecked && dueDay != null && dueDay < LocalDate.now().toEpochDay()
                Detail(
                    facts.joinToString(" · "),
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.location?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppIcons.Location, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Detail(location.name ?: stringResource(R.string.feature_lists_pinned_place), Modifier.padding(start = 4.dp))
                }
            }
        }
        if (item.link != null) {
            IconButton(onClick = onLinkClick) {
                AppIcon(
                    AppIcons.Link,
                    stringResource(R.string.feature_lists_open_link),
                    tint = MaterialTheme.colorScheme.primary,
                )
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
        dragHandle()
    }
}

@Composable
private fun Detail(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun rememberBitmap(image: ImageData?): ImageBitmap? = remember(image) {
    image?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
}

@Composable
private fun AddItemBar(total: String?, sectionName: String?, onClearSection: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    val focusRequester = remember { FocusRequester() }
    // Choosing a section moves the focus here, which reads out the new label and opens the keyboard.
    // Not after a rotation: the section was chosen earlier.
    var focusedFor by rememberSaveable { mutableStateOf(sectionName) }
    LaunchedEffect(sectionName) {
        if (sectionName != null && sectionName != focusedFor) focusRequester.requestFocus()
        focusedFor = sectionName
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                // Keeps the field above the keyboard and the gesture bar.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (total != null) {
                Text(
                    total,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_ITEM_LENGTH) },
                    label = {
                        Text(
                            if (sectionName == null) {
                                stringResource(R.string.feature_lists_add_item_hint)
                            } else {
                                stringResource(R.string.feature_lists_add_to_section, sectionName)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if (sectionName == null) {
                        null
                    } else {
                        {
                            IconButton(onClick = onClearSection) {
                                AppIcon(AppIcons.Close, stringResource(R.string.feature_lists_add_to_end))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    // The keyboard stays open, so a whole shopping list can be typed in one go.
                    keyboardActions = KeyboardActions(onSend = { add() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                FilledIconButton(onClick = add, enabled = text.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) {
                    AppIcon(AppIcons.Add, stringResource(R.string.feature_lists_add_item))
                }
            }
        }
    }
}

@Composable
private fun ListMenu(
    hasCheckedItems: Boolean,
    /** Null hides the switch: the list is somebody else's wish list. */
    isMyWishList: Boolean?,
    onToggleWishList: () -> Unit,
    onSelect: (ListDialog) -> Unit,
) {
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
                text = { Text(stringResource(R.string.feature_lists_add_section)) },
                leadingIcon = { AppIcon(AppIcons.Add, null) },
                onClick = { select(ListDialog.AddSection) },
            )
            if (isMyWishList != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isMyWishList) R.string.feature_lists_wish_list_off else R.string.feature_lists_wish_list_on,
                            ),
                        )
                    },
                    leadingIcon = { AppIcon(AppIcons.Gift, null) },
                    onClick = {
                        expanded = false
                        onToggleWishList()
                    },
                )
            }
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
    state: ListDetailUiState.Ready,
    snackbarHostState: SnackbarHostState,
    onAction: (ListDetailAction) -> Unit,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onPickPlace: () -> Unit,
    onOpenInMaps: () -> Unit,
    onOpenLink: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box {
            SheetContent(item, state, onAction, onTakePhoto, onChoosePhoto, onPickPlace, onOpenInMaps, onOpenLink)
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SheetContent(
    item: ListItem,
    state: ListDetailUiState.Ready,
    onAction: (ListDetailAction) -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onPickPlace: () -> Unit,
    onOpenInMaps: () -> Unit,
    onOpenLink: () -> Unit,
) {
    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
        var text by rememberSaveable(item.id) { mutableStateOf(item.text) }
        SheetField(
            value = text,
            onValueChange = { text = it.take(MAX_ITEM_LENGTH) },
            label = if (item.isHeader) R.string.feature_lists_section_label else R.string.feature_lists_item_label,
            canSave = text.isNotBlank() && text.trim() != item.text,
            onSave = { onAction(ListDetailAction.UpdateText(item.id, text.trim())) },
            supportingText = stringResource(R.string.feature_lists_added_by, item.createdByName),
            keyboardOptions = sentences,
            modifier = SheetFieldModifier,
        )
        if (!item.isHeader) {
            ItemDetails(item, state, onAction, sentences, onTakePhoto, onChoosePhoto, onPickPlace, onOpenInMaps, onOpenLink)
            HorizontalDivider()
        }
        SheetAction(
            AppIcons.Delete,
            if (item.isHeader) R.string.feature_lists_delete_section else R.string.feature_lists_delete_item,
            onClick = { onAction(ListDetailAction.DeleteItem(item.id)) },
        )
    }
}

/** Everything in the item sheet that a section heading does not have. */
@Composable
private fun ItemDetails(
    item: ListItem,
    state: ListDetailUiState.Ready,
    onAction: (ListDetailAction) -> Unit,
    sentences: KeyboardOptions,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onPickPlace: () -> Unit,
    onOpenInMaps: () -> Unit,
    onOpenLink: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val isPhotoBusy = item.id in state.photoBusyItemIds

    var note by rememberSaveable(item.id) { mutableStateOf(item.note.orEmpty()) }
    val newNote = note.trim().ifEmpty { null }
    SheetField(
        value = note,
        onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
        label = R.string.feature_lists_note_label,
        canSave = newNote != item.note,
        onSave = { onAction(ListDetailAction.SetNote(item.id, newNote)) },
        singleLine = false,
        keyboardOptions = sentences,
        modifier = SheetFieldModifier,
    )

    Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        var price by rememberSaveable(item.id) { mutableStateOf(item.priceCents?.let { priceInput(it, locale) }.orEmpty()) }
        val newPrice = parsePriceCents(price)
        val isPriceValid = price.isBlank() || newPrice != null
        SheetField(
            value = price,
            onValueChange = { price = it.take(10) },
            label = R.string.feature_lists_price_label,
            canSave = isPriceValid && newPrice != item.priceCents,
            onSave = { onAction(ListDetailAction.SetPrice(item.id, newPrice)) },
            isError = !isPriceValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onAction(ListDetailAction.SetQuantity(item.id, item.quantity - 1)) },
            enabled = item.quantity > 1,
        ) {
            AppIcon(AppIcons.Remove, stringResource(R.string.feature_lists_quantity_less))
        }
        val quantityLabel = stringResource(R.string.feature_lists_quantity, item.quantity)
        Text(
            "× ${item.quantity}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                contentDescription = quantityLabel
                liveRegion = LiveRegionMode.Polite
            },
        )
        IconButton(
            onClick = { onAction(ListDetailAction.SetQuantity(item.id, item.quantity + 1)) },
            enabled = item.quantity < MAX_QUANTITY,
        ) {
            AppIcon(AppIcons.Add, stringResource(R.string.feature_lists_quantity_more))
        }
    }

    var link by rememberSaveable(item.id) { mutableStateOf(item.link.orEmpty()) }
    val newLink = normalizeLink(link)
    val isLinkValid = link.isBlank() || newLink != null
    SheetField(
        value = link,
        onValueChange = { link = it.take(MAX_LINK_LENGTH) },
        label = R.string.feature_lists_link_label,
        // An emptied field can be saved too: that removes the link.
        canSave = isLinkValid && newLink != item.link,
        onSave = { onAction(ListDetailAction.SetLink(item.id, newLink)) },
        isError = !isLinkValid,
        supportingText = if (isLinkValid) null else stringResource(R.string.feature_lists_link_invalid),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = SheetFieldModifier,
    )
    if (item.link != null) {
        SheetAction(AppIcons.OpenInNew, R.string.feature_lists_open_link, onOpenLink)
    }

    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val dueDay = item.dueDay
    if (dueDay == null) {
        SheetAction(AppIcons.Event, R.string.feature_lists_add_due, onClick = { pickingDate = true })
    } else {
        SheetAction(
            AppIcons.Event,
            stringResource(R.string.feature_lists_due, formatDay(dueDay, locale)),
            onClick = { pickingDate = true },
        )
        SheetAction(AppIcons.Close, R.string.feature_lists_remove_due, onClick = {
            onAction(ListDetailAction.SetDueDay(item.id, null))
        })
    }
    if (pickingDate) {
        // The picker counts in UTC days, which is also how the due day is stored.
        val picked = rememberDatePickerState((dueDay ?: LocalDate.now().toEpochDay()) * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picked.selectedDateMillis?.let { onAction(ListDetailAction.SetDueDay(item.id, it / MILLIS_PER_DAY)) }
                    pickingDate = false
                }) { Text(stringResource(CoreR.string.core_ui_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(CoreR.string.core_ui_cancel)) }
            },
        ) {
            DatePicker(picked)
        }
    }

    if (!state.hidesClaims) {
        when (item.claimedById) {
            null -> SheetAction(AppIcons.Gift, R.string.feature_lists_take, onClick = {
                onAction(ListDetailAction.SetClaimed(item.id, true))
            })
            state.currentUserId -> SheetAction(AppIcons.Close, R.string.feature_lists_untake, onClick = {
                onAction(ListDetailAction.SetClaimed(item.id, false))
            })
            else -> SheetAction(
                AppIcons.Gift,
                stringResource(R.string.feature_lists_taken_by, item.claimedByName.orEmpty()),
                onClick = {},
                enabled = false,
            )
        }
        state.list.giftForName?.let { name ->
            Detail(stringResource(R.string.feature_lists_hidden_from, name), Modifier.padding(horizontal = 24.dp))
        }
    }
    HorizontalDivider()

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
}

private val SheetFieldModifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp)

/** A text field of the item sheet. A check mark appears in it while there is a change to save. */
@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    canSave: Boolean,
    onSave: () -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    val save = { if (canSave) onSave() }
    // Closing the sheet, or a rotation, with a valid change still in the field saves it rather than dropping it.
    val saveOnClose by rememberUpdatedState(save)
    DisposableEffect(Unit) { onDispose { saveOnClose() } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            if (canSave) {
                IconButton(onClick = save) {
                    AppIcon(AppIcons.Check, stringResource(CoreR.string.core_ui_save))
                }
            }
        },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = modifier,
    )
}

@Composable
private fun SheetAction(icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    SheetAction(icon, stringResource(label), onClick, enabled)
}

@Composable
private fun SheetAction(icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AppIcon(icon, null) },
        headlineContent = { Text(label) },
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
    ListAnythingTheme {
        ListDetailScreen(
            uiState = ListDetailUiState.Ready(
                list = SharedList("l1", "s1", "Supermarket", "🛒"),
                items = listOf(
                    ListItem("0", "Pharmacy", false, null, "Panos", null, null, 0, isHeader = true),
                    ListItem(
                        "1", "Shampoo", false, null, "Panos", null, null, 1, "https://shop.example/shampoo",
                        note = "The green one", quantity = 2, priceCents = 450, dueDay = 20_800, claimedByName = "Maria",
                    ),
                    ListItem("2", "Taverna by the harbour", false, null, "Maria", null, ItemLocation(35.5, 24.0, "To Limani"), 2),
                    ListItem("3", "Milk", true, "Maria", "Panos", null, null, 3),
                ),
                photoBusyItemIds = emptySet(),
                photoViewer = null,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAction = {},
            onTakePhoto = {},
            onChoosePhoto = {},
            onOpenInMaps = {},
            onOpenLink = {},
        )
    }
}
