package com.kokouliss.listanything.feature.spaces

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kokouliss.listanything.core.domain.model.InviteCode
import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.ui.R as CoreR
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.TextInputDialog
import com.kokouliss.listanything.core.ui.component.UndoSnackbar
import com.kokouliss.listanything.core.ui.component.pageWidth
import com.kokouliss.listanything.core.ui.component.rememberPaperScroll
import com.kokouliss.listanything.core.ui.component.ruledPaper
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.theme.bubbleColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SpacesDialog { None, Create, Join }

@Composable
internal fun SpacesRoute(
    onOpenSpace: (String) -> Unit,
    onOpenProfile: () -> Unit,
    notice: String?,
    onNoticeShown: () -> Unit,
    inviteCode: String? = null,
    onInviteHandled: () -> Unit = {},
    viewModel: SpacesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = { message: Int ->
        scope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
    }

    // An invite link opened the app, so join right away. The result arrives like any other join.
    LaunchedEffect(inviteCode) {
        if (inviteCode != null) {
            viewModel.joinWithCode(inviteCode)
            onInviteHandled()
        }
    }

    UndoSnackbar(viewModel.declines, snackbarHostState) {
        resources.getString(R.string.feature_spaces_invitation_declined, it.name)
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            scope.launch { snackbarHostState.showSnackbar(notice) }
            onNoticeShown()
        }
    }

    SpacesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenSpace = onOpenSpace,
        onCreateSpace = viewModel::createSpace,
        onJoinWithCode = viewModel::joinWithCode,
        onJoinShown = viewModel::joinShown,
        onScanCode = {
            // The camera screen comes from Google Play services, so the app needs no camera permission.
            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode ->
                    val code = barcode.rawValue?.let(InviteCode::fromLink)
                    if (code != null) viewModel.joinWithCode(code) else showMessage(R.string.feature_spaces_scan_not_invite)
                }
                // Closing the scanner is reported as a cancel, not as a failure.
                .addOnFailureListener { showMessage(R.string.feature_spaces_scan_failed) }
        },
        onAcceptInvitation = viewModel::acceptInvitation,
        onDeclineInvitation = viewModel::declineInvitation,
        onOpenProfile = onOpenProfile,
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
    /** The result of a join has been shown; the next state is Idle. */
    onJoinShown: () -> Unit,
    onScanCode: () -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onDeclineInvitation: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onRetry: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(SpacesDialog.None) }
    val ready = uiState as? SpacesUiState.Ready
    val title = stringResource(R.string.feature_spaces_title)
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val join = ready?.join
    LaunchedEffect(join) {
        if (join !is JoinState.Done) return@LaunchedEffect
        // A code that did not work stays in the dialog, with the reason under the field.
        if (dialog == SpacesDialog.Join && join.result.failed) return@LaunchedEffect
        if (dialog == SpacesDialog.Join) dialog = SpacesDialog.None
        onJoinShown()
        scope.launch { snackbarHostState.showSnackbar(resources.getString(join.result.message)) }
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = onScanCode) {
                            AppIcon(AppIcons.QrCodeScanner, stringResource(R.string.feature_spaces_scan_code))
                        }
                        IconButton(onClick = { dialog = SpacesDialog.Join }) {
                            AppIcon(AppIcons.GroupAdd, stringResource(R.string.feature_spaces_join_with_code))
                        }
                        // The avatar itself is decorative, so the button carries the name.
                        val profileLabel = stringResource(R.string.feature_spaces_account)
                        IconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier.semantics { contentDescription = profileLabel },
                        ) {
                            InitialAvatar(ready.userName, size = 32.dp)
                        }
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
        Box(
            Modifier
                .fillMaxSize()
                .ruledPaper(MaterialTheme.colorScheme.primary, rememberPaperScroll())
                .padding(padding)
                .pageWidth(),
        ) {
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
                            TextButton(onClick = onScanCode) {
                                Text(stringResource(R.string.feature_spaces_scan_code))
                            }
                        }
                    } else {
                        SpaceList(uiState, onOpenSpace, onAcceptInvitation, onDeclineInvitation)
                    }
                    if (uiState.join == JoinState.Joining) {
                        val joining = stringResource(R.string.feature_spaces_joining)
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().semantics {
                                contentDescription = joining
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
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
            isBusy = join == JoinState.Joining,
            errorText = (join as? JoinState.Done)?.result?.takeIf { it.failed }?.let { stringResource(it.message) },
            onConfirm = onJoinWithCode,
            onDismiss = {
                dialog = SpacesDialog.None
                onJoinShown()
            },
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
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(148.dp),
        // Bottom room so the last bubble is not hidden behind the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(state.invitations, key = { "invite-${it.id}" }, span = { StaggeredGridItemSpan.FullLine }) { space ->
            InvitationCard(space, onAccept = { onAcceptInvitation(space.id) }, onDecline = { onDeclineInvitation(space.id) })
        }
        itemsIndexed(state.spaces, key = { _, space -> space.id }) { index, space ->
            // The second column starts lower, so the bubbles do not line up in rows.
            Box(
                Modifier.fillMaxWidth().padding(top = if (index == 1) 40.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                SpaceBubble(space, index, onClick = { onOpenSpace(space.id) })
            }
        }
    }
}

private val JoinResult.failed: Boolean
    get() = this == JoinResult.InvalidCode || this == JoinResult.Unavailable

private val JoinResult.message: Int
    get() = when (this) {
        JoinResult.Joined -> R.string.feature_spaces_join_done
        JoinResult.AlreadyMember -> R.string.feature_spaces_join_already_member
        JoinResult.InvalidCode -> R.string.feature_spaces_join_invalid_code
        JoinResult.Unavailable -> R.string.feature_spaces_join_unavailable
    }

/** How much of its cell a bubble fills, so the bubbles are not all one size. */
private val BubbleSizes = floatArrayOf(1f, 0.84f, 0.92f)

/** A space as a round bubble that bobs slowly. Its colour and size come from its id, so they stay. */
@Composable
private fun SpaceBubble(space: Space, index: Int, onClick: () -> Unit) {
    // Colour by position, so two bubbles side by side never match; size by id, so it stays the same.
    val (fill, ink) = bubbleColors(index)
    // Each bubble settles once when it appears, a beat after the one before it, then keeps still.
    val drop = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(index % 5 * 100L)
        drop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    // At a large font the words no longer fit a circle, so the bubble becomes a tall rounded card.
    val roomy = LocalDensity.current.fontScale > 1.3f
    val shape = if (roomy) MaterialTheme.shapes.extraLarge else CircleShape
    Column(
        Modifier
            .fillMaxWidth(BubbleSizes[space.id.hashCode().mod(BubbleSizes.size)])
            .then(if (roomy) Modifier.heightIn(min = 120.dp) else Modifier.aspectRatio(1f))
            // Read here and not in the composition, so a moving bubble is only drawn again.
            .graphicsLayer { translationY = drop.value * 12.dp.toPx() }
            .shadow(6.dp, shape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            space.name,
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            pluralStringResource(R.plurals.feature_spaces_member_count, space.members.size, space.members.size),
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InvitationCard(space: Space, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        // Yellow, like a sticky note left on the notebooks.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                // Every card has these two buttons, so each one names its space for a screen reader.
                val declineLabel = stringResource(R.string.feature_spaces_decline_space, space.name)
                val acceptLabel = stringResource(R.string.feature_spaces_accept_space, space.name)
                TextButton(
                    onClick = onDecline,
                    // The blue of a text button cannot be read on the yellow at night.
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.semantics { contentDescription = declineLabel },
                ) { Text(stringResource(R.string.feature_spaces_decline)) }
                Button(onClick = onAccept, modifier = Modifier.semantics { contentDescription = acceptLabel }) {
                    Text(stringResource(R.string.feature_spaces_accept))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SpacesScreenPreview() {
    val family = Space("1", "Family", "u1", "ABCD2345", listOf(Member("u1", "Panos"), Member("u2", "Maria")), emptyList())
    ListAnythingTheme {
        SpacesScreen(
            uiState = SpacesUiState.Ready(
                userName = "Panos",
                spaces = listOf(family),
                invitations = listOf(family.copy(id = "2", name = "Flatmates")),
                join = JoinState.Idle,
            ),
            snackbarHostState = SnackbarHostState(),
            onOpenSpace = {},
            onCreateSpace = {},
            onJoinWithCode = {},
            onJoinShown = {},
            onScanCode = {},
            onAcceptInvitation = {},
            onDeclineInvitation = {},
            onOpenProfile = {},
            onRetry = {},
        )
    }
}
