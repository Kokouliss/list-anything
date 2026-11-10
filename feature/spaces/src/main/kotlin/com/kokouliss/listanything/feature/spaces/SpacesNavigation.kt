package com.kokouliss.listanything.feature.spaces

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The signed-in start screen: the user's spaces and pending invitations. */
@Serializable
data object SpacesKey : NavKey

/** One space: its lists, members and invites. */
@Serializable
data class SpaceDetailKey(val spaceId: String) : NavKey

fun EntryProviderScope<NavKey>.spacesEntries(
    onOpenSpace: (spaceId: String) -> Unit,
    onOpenList: (spaceId: String, listId: String) -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
) {
    entry<SpacesKey> {
        SpacesRoute(onOpenSpace = onOpenSpace, onOpenProfile = onOpenProfile)
    }
    entry<SpaceDetailKey> { key ->
        SpaceDetailRoute(
            viewModel = hiltViewModel<SpaceDetailViewModel, SpaceDetailViewModel.Factory> { it.create(key) },
            onOpenList = { listId -> onOpenList(key.spaceId, listId) },
            onBack = onBack,
        )
    }
}
