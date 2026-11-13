package com.kokouliss.listanything.feature.spaces

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The signed-in start screen: the user's spaces and pending invitations. */
@Serializable
data object SpacesKey : NavKey

/** One space: its lists, drawings, members and invites. */
@Serializable
data class SpaceDetailKey(val spaceId: String) : NavKey

/** One drawing page of a space, which every member draws on. */
@Serializable
data class DrawingKey(val spaceId: String, val drawingId: String) : NavKey

fun EntryProviderScope<NavKey>.spacesEntries(
    onOpenSpace: (spaceId: String) -> Unit,
    onOpenList: (spaceId: String, listId: String) -> Unit,
    onOpenDrawing: (spaceId: String, drawingId: String) -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
    /** The space was deleted or access to it ended: the screen closes and [message] goes on the one below. */
    onGone: (message: String) -> Unit,
    /** A message from a screen that closed itself, shown once by the screen now on top. */
    notice: String?,
    onNoticeShown: () -> Unit,
    /** The code of an invite link that opened the app. The spaces screen joins with it. */
    inviteCode: String? = null,
    onInviteHandled: () -> Unit = {},
) {
    entry<SpacesKey> {
        SpacesRoute(
            onOpenSpace = onOpenSpace,
            onOpenProfile = onOpenProfile,
            notice = notice,
            onNoticeShown = onNoticeShown,
            inviteCode = inviteCode,
            onInviteHandled = onInviteHandled,
        )
    }
    entry<SpaceDetailKey> { key ->
        SpaceDetailRoute(
            viewModel = hiltViewModel<SpaceDetailViewModel, SpaceDetailViewModel.Factory> { it.create(key) },
            onOpenList = { listId -> onOpenList(key.spaceId, listId) },
            onOpenDrawing = { drawingId -> onOpenDrawing(key.spaceId, drawingId) },
            onBack = onBack,
            onGone = onGone,
            notice = notice,
            onNoticeShown = onNoticeShown,
        )
    }
    entry<DrawingKey> { key ->
        DrawingRoute(
            viewModel = hiltViewModel<DrawingViewModel, DrawingViewModel.Factory> { it.create(key) },
            onBack = onBack,
            onGone = onGone,
        )
    }
}
