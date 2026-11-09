package com.kokouliss.listanything.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.TextInputDialog
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.R as CoreR

private enum class SpacesDialog { None, Create, Join }

@Composable
internal fun SpacesRoute(
    onOpenSpace: (String) -> Unit,
    viewModel: SpacesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SpacesEvent.JoinFinished -> snackbarHostState.showSnackbar(
                    resources.getString(
                        when (event.result) {
                            JoinResult.Joined -> R.string.feature_spaces_join_done
                            JoinResult.AlreadyMember -> R.string.feature_spaces_join_already_member
                            JoinResult.InvalidCode -> R.string.feature_spaces_join_invalid_code
                            JoinResult.Unavailable -> R.string.feature_spaces_join_unavailable
                        },
                    ),
                )
            }
        }
    }

    SpacesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenSpace = onOpenSpace,
        onCreateSpace = viewModel::createSpace,
        onJoinWithCode = viewModel::joinWithCode,
        onAcceptInvitation = viewModel::acceptInvitation,
        onDeclineInvitation = viewModel::declineInvitation,
        onSignOut = viewModel::signOut,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun SpacesScreen(
    uiState: SpacesUiState,
    snackbarHostState: SnackbarHostState,
    onOpenSpace: (String) -> Unit,
    onCreateSpace: (String) -> Unit,
    onJoinWithCode: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onDeclineInvitation: (String) -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(SpacesDialog.None) }
    val ready = uiState as? SpacesUiState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_spaces_title)) },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { dialog = SpacesDialog.Join }) {
                            AppIcon(AppIcons.GroupAdd, stringResource(R.string.feature_spaces_join_with_code))
                        }
                        AccountMenu(ready.userName, ready.userEmail, onSignOut)
                    }
                },
            )
        },
        floatingActionButton = {
            if (ready != null && (ready.spaces.isNotEmpty() || ready.invitations.isNotEmpty())) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = SpacesDialog.Create },
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.feature_spaces_new_space)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                SpacesUiState.Loading -> LoadingBox()
                SpacesUiState.Error -> EmptyState(
                    icon = AppIcons.Group,
                    title = stringResource(R.string.feature_spaces_error_title),
                    body = stringResource(CoreR.string.core_ui_error_generic),
                ) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.feature_spaces_retry)) }
                }
                is SpacesUiState.Ready -> {
                    if (uiState.spaces.isEmpty() && uiState.invitations.isEmpty()) {
                        EmptyState(
                            icon = AppIcons.Group,
                            title = stringResource(R.string.feature_spaces_empty_title),
                            body = stringResource(R.string.feature_spaces_empty_body),
                        ) {
                            Button(onClick = { dialog = SpacesDialog.Create }) {
                                Text(stringResource(R.string.feature_spaces_new_space))
                            }
                            OutlinedButton(onClick = { dialog = SpacesDialog.Join }) {
                                Text(stringResource(R.string.feature_spaces_join_with_code))
                            }
                        }
                    } else {
                        SpaceList(uiState, onOpenSpace, onAcceptInvitation, onDeclineInvitation)
                    }
                    if (uiState.isJoining) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }

    when (dialog) {
        SpacesDialog.None -> Unit
        SpacesDialog.Create -> TextInputDialog(
            title = stringResource(R.string.feature_spaces_new_space),
            label = stringResource(R.string.feature_spaces_space_name),
            supportingText = stringResource(R.string.feature_spaces_space_name_hint),
            confirmLabel = stringResource(R.string.feature_spaces_create),
            onConfirm = {
                onCreateSpace(it)
                dialog = SpacesDialog.None
            },
            onDismiss = { dialog = SpacesDialog.None },
        )
        SpacesDialog.Join -> TextInputDialog(
            title = stringResource(R.string.feature_spaces_join_title),
            label = stringResource(R.string.feature_spaces_invite_code),
            confirmLabel = stringResource(R.string.feature_spaces_join),
            capitalization = KeyboardCapitalization.Characters,
            maxLength = 12,
            onConfirm = {
                onJoinWithCode(it)
                dialog = SpacesDialog.None
            },
            onDismiss = { dialog = SpacesDialog.None },
        )
    }
}

@Composable
private fun SpaceList(
    state: SpacesUiState.Ready,
    onOpenSpace: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onDeclineInvitation: (String) -> Unit,
) {
    LazyColumn(
        // Bottom room so the last row is not hidden behind the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.invitations, key = { "invite-${it.id}" }) { space ->
            InvitationCard(space, onAccept = { onAcceptInvitation(space.id) }, onDecline = { onDeclineInvitation(space.id) })
        }
        items(state.spaces, key = { it.id }) { space ->
            Card(onClick = { onOpenSpace(space.id) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { InitialAvatar(space.name) },
                    headlineContent = { Text(space.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.feature_spaces_member_count,
                                space.members.size,
                                space.members.size,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun InvitationCard(space: Space, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.feature_spaces_invitation_title, space.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                pluralStringResource(R.plurals.feature_spaces_member_count, space.members.size, space.members.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDecline) { Text(stringResource(R.string.feature_spaces_decline)) }
                Button(onClick = onAccept) { Text(stringResource(R.string.feature_spaces_accept)) }
            }
        }
    }
}

@Composable
private fun AccountMenu(userName: String, userEmail: String?, onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(R.string.feature_spaces_account))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(userName, style = MaterialTheme.typography.titleSmall)
                if (userEmail != null) {
                    Text(
                        userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_spaces_sign_out)) },
                leadingIcon = { AppIcon(AppIcons.Logout, null) },
                onClick = {
                    expanded = false
                    onSignOut()
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SpacesScreenPreview() {
    val family = Space("1", "Family", "u1", "ABCD2345", listOf(Member("u1", "Panos"), Member("u2", "Maria")), emptyList())
    ListAnythingTheme(dynamicColor = false) {
        SpacesScreen(
            uiState = SpacesUiState.Ready(
                userName = "Panos",
                userEmail = "panos@example.com",
                spaces = listOf(family),
                invitations = listOf(family.copy(id = "2", name = "Flatmates")),
                isJoining = false,
            ),
            snackbarHostState = SnackbarHostState(),
            onOpenSpace = {},
            onCreateSpace = {},
            onJoinWithCode = {},
            onAcceptInvitation = {},
            onDeclineInvitation = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}
