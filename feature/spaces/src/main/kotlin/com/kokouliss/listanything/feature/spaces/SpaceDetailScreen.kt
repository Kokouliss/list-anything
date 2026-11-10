package com.kokouliss.listanything.feature.spaces

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kokouliss.listanything.core.domain.model.InviteCode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.normalizeEmail
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.ConfirmDialog
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.component.ListEditorDialog
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.TextInputDialog
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.R as CoreR

private enum class SpaceDialog { None, NewList, Rename, Leave, Delete, ResetCode }

@Composable
internal fun SpaceDetailRoute(
    viewModel: SpaceDetailViewModel,
    onOpenList: (listId: String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    if (uiState is SpaceDetailUiState.Gone) {
        LaunchedEffect(Unit) { onBack() }
    }

    SpaceDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenList = onOpenList,
        onCreateList = viewModel::createList,
        onRenameSpace = viewModel::renameSpace,
        onShareCode = { space ->
            val text = resources.getString(
                R.string.feature_spaces_share_text,
                space.name,
                space.inviteCode,
                InviteCode.link(space.inviteCode),
            )
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null))
        },
        onResetCode = viewModel::resetInviteCode,
        onInviteEmail = viewModel::inviteEmail,
        onCancelEmailInvite = viewModel::cancelEmailInvite,
        onRemoveMember = viewModel::removeMember,
        onLeaveSpace = viewModel::leaveSpace,
        onDeleteSpace = viewModel::deleteSpace,
    )
}

@Composable
internal fun SpaceDetailScreen(
    uiState: SpaceDetailUiState,
    onBack: () -> Unit,
    onOpenList: (listId: String) -> Unit,
    onCreateList: (name: String, emoji: String) -> Unit,
    onRenameSpace: (String) -> Unit,
    onShareCode: (Space) -> Unit,
    onResetCode: () -> Unit,
    onInviteEmail: (String) -> Unit,
    onCancelEmailInvite: (String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
    onLeaveSpace: () -> Unit,
    onDeleteSpace: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(SpaceDialog.None) }
    var showMembers by rememberSaveable { mutableStateOf(false) }
    var memberToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = uiState as? SpaceDetailUiState.Ready
    val closeDialog = { dialog = SpaceDialog.None }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ready?.space?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { showMembers = true }) {
                            AppIcon(AppIcons.PersonAdd, stringResource(R.string.feature_spaces_members))
                        }
                        SpaceMenu(isOwner = ready.isOwner, onSelect = { dialog = it })
                    }
                },
            )
        },
        floatingActionButton = {
            if (ready != null && ready.lists.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = SpaceDialog.NewList },
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.feature_spaces_new_list)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ready == null -> LoadingBox()
                ready.lists.isEmpty() -> EmptyState(
                    icon = AppIcons.Checklist,
                    title = stringResource(R.string.feature_spaces_lists_empty_title),
                    body = stringResource(R.string.feature_spaces_lists_empty_body),
                ) {
                    Button(onClick = { dialog = SpaceDialog.NewList }) {
                        Text(stringResource(R.string.feature_spaces_new_list))
                    }
                }
                else -> ListOfLists(ready.lists, onOpenList)
            }
        }
    }

    if (ready != null && showMembers) {
        MembersSheet(
            state = ready,
            onDismiss = { showMembers = false },
            onShareCode = { onShareCode(ready.space) },
            onResetCode = { dialog = SpaceDialog.ResetCode },
            onInviteEmail = onInviteEmail,
            onCancelEmailInvite = onCancelEmailInvite,
            onRemoveMember = { memberToRemove = it },
        )
    }

    when (dialog) {
        SpaceDialog.None -> Unit
        SpaceDialog.NewList -> ListEditorDialog(
            title = stringResource(R.string.feature_spaces_new_list),
            confirmLabel = stringResource(R.string.feature_spaces_create),
            onConfirm = { name, emoji ->
                onCreateList(name, emoji)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SpaceDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.feature_spaces_rename_space),
            label = stringResource(R.string.feature_spaces_space_name),
            confirmLabel = stringResource(CoreR.string.core_ui_save),
            initialValue = ready?.space?.name.orEmpty(),
            onConfirm = {
                onRenameSpace(it)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SpaceDialog.Leave -> ConfirmDialog(
            title = stringResource(R.string.feature_spaces_leave_space),
            text = stringResource(R.string.feature_spaces_leave_confirm_text),
            confirmLabel = stringResource(R.string.feature_spaces_leave),
            onConfirm = {
                closeDialog()
                onLeaveSpace()
            },
            onDismiss = closeDialog,
        )
        SpaceDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.feature_spaces_delete_space),
            text = stringResource(R.string.feature_spaces_delete_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onDeleteSpace()
            },
            onDismiss = closeDialog,
        )
        SpaceDialog.ResetCode -> ConfirmDialog(
            title = stringResource(R.string.feature_spaces_reset_code_confirm_title),
            text = stringResource(R.string.feature_spaces_reset_code_confirm_text),
            confirmLabel = stringResource(R.string.feature_spaces_reset_code),
            onConfirm = {
                closeDialog()
                onResetCode()
            },
            onDismiss = closeDialog,
        )
    }

    val removing = ready?.space?.members?.firstOrNull { it.id == memberToRemove }
    if (removing != null) {
        ConfirmDialog(
            title = stringResource(R.string.feature_spaces_remove_member, removing.name),
            text = stringResource(R.string.feature_spaces_remove_member_confirm_text, removing.name),
            confirmLabel = stringResource(R.string.feature_spaces_remove),
            onConfirm = {
                memberToRemove = null
                onRemoveMember(removing.id)
            },
            onDismiss = { memberToRemove = null },
        )
    }
}

@Composable
private fun ListOfLists(lists: List<SharedList>, onOpenList: (String) -> Unit) {
    LazyColumn(
        // Bottom room so the last row is not hidden behind the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lists, key = { it.id }) { list ->
            Card(onClick = { onOpenList(list.id) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Text(list.emoji, style = MaterialTheme.typography.headlineSmall) },
                    headlineContent = { Text(list.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun SpaceMenu(isOwner: Boolean, onSelect: (SpaceDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val select = { target: SpaceDialog ->
        expanded = false
        onSelect(target)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isOwner) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_spaces_rename_space)) },
                    leadingIcon = { AppIcon(AppIcons.Edit, null) },
                    onClick = { select(SpaceDialog.Rename) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_spaces_delete_space)) },
                    leadingIcon = { AppIcon(AppIcons.Delete, null) },
                    onClick = { select(SpaceDialog.Delete) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_spaces_leave_space)) },
                    leadingIcon = { AppIcon(AppIcons.Logout, null) },
                    onClick = { select(SpaceDialog.Leave) },
                )
            }
        }
    }
}

@Composable
private fun MembersSheet(
    state: SpaceDetailUiState.Ready,
    onDismiss: () -> Unit,
    onShareCode: () -> Unit,
    onResetCode: () -> Unit,
    onInviteEmail: (String) -> Unit,
    onCancelEmailInvite: (String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
) {
    val space = state.space
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.feature_spaces_invite_code_title))
            Hint(stringResource(R.string.feature_spaces_invite_code_hint))
            SelectionContainer {
                Text(
                    space.inviteCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                )
            }
            Hint(stringResource(R.string.feature_spaces_qr_hint))
            QrCode(InviteCode.link(space.inviteCode), Modifier.size(200.dp).align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onShareCode) {
                    AppIcon(AppIcons.Share, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.feature_spaces_share_code))
                }
                // Only the owner may replace the code (enforced by the security rules as well).
                if (state.isOwner) {
                    TextButton(onClick = onResetCode) { Text(stringResource(R.string.feature_spaces_reset_code)) }
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.feature_spaces_invite_by_email_title))
            EmailInviteField(onInviteEmail)
            space.invitedEmails.forEach { email ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppIcons.Mail, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        email,
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onCancelEmailInvite(email) }) {
                        AppIcon(AppIcons.Close, stringResource(R.string.feature_spaces_cancel_invite, email))
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.feature_spaces_members_title))
            space.members.forEach { member ->
                MemberRow(
                    member = member,
                    isYou = member.id == state.currentUserId,
                    isSpaceOwner = member.id == space.ownerId,
                    onRemove = if (state.isOwner && member.id != space.ownerId) {
                        { onRemoveMember(member.id) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** Black on white whatever the theme, because that is what cameras read best. */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    // Size 0 asks for the smallest code: one cell per module plus the quiet border.
    val matrix = remember(content) { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0) }
    val description = stringResource(R.string.feature_spaces_qr_description)
    Canvas(modifier.background(Color.White).semantics { contentDescription = description }) {
        val cell = size.minDimension / matrix.width
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                // Half a pixel wider, so rounding leaves no hairlines between dark cells.
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

@Composable
private fun EmailInviteField(onInviteEmail: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    val isValid = normalizeEmail(email) != null
    val send = {
        if (isValid) {
            onInviteEmail(email)
            email = ""
        }
    }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it.take(254) },
        label = { Text(stringResource(R.string.feature_spaces_email_label)) },
        supportingText = { Text(stringResource(R.string.feature_spaces_email_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = isValid) {
                AppIcon(AppIcons.Send, stringResource(R.string.feature_spaces_send_invite))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MemberRow(member: Member, isYou: Boolean, isSpaceOwner: Boolean, onRemove: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InitialAvatar(member.name)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (isYou) stringResource(R.string.feature_spaces_you, member.name) else member.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSpaceOwner) Hint(stringResource(R.string.feature_spaces_owner))
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                AppIcon(AppIcons.Close, stringResource(R.string.feature_spaces_remove_member, member.name))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@PreviewLightDark
@Composable
private fun SpaceDetailScreenPreview() {
    ListAnythingTheme(dynamicColor = false) {
        SpaceDetailScreen(
            uiState = SpaceDetailUiState.Ready(
                space = Space(
                    id = "1",
                    name = "Family",
                    ownerId = "u1",
                    inviteCode = "ABCD2345",
                    members = listOf(Member("u1", "Panos"), Member("u2", "Maria")),
                    invitedEmails = listOf("friend@example.com"),
                ),
                lists = listOf(SharedList("l1", "1", "Supermarket", "🛒"), SharedList("l2", "1", "Places to eat", "🍽️")),
                currentUserId = "u1",
            ),
            onBack = {},
            onOpenList = {},
            onCreateList = { _, _ -> },
            onRenameSpace = {},
            onShareCode = {},
            onResetCode = {},
            onInviteEmail = {},
            onCancelEmailInvite = {},
            onRemoveMember = {},
            onLeaveSpace = {},
            onDeleteSpace = {},
        )
    }
}
