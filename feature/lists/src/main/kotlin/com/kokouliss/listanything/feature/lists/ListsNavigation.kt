package com.kokouliss.listanything.feature.lists

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** One list with its items. */
@Serializable
data class ListDetailKey(val spaceId: String, val listId: String) : NavKey

/** Asks which list gets a link that another app shared. [text] becomes the item, [link] its link. */
@Serializable
data class ShareKey(val text: String, val link: String?) : NavKey

fun EntryProviderScope<NavKey>.listsEntries(
    onBack: () -> Unit,
    onSharedItemAdded: (spaceId: String, listId: String) -> Unit,
) {
    entry<ShareKey> { key ->
        ShareRoute(key = key, onAdded = onSharedItemAdded, onBack = onBack)
    }
    entry<ListDetailKey> { key ->
        ListDetailRoute(
            viewModel = hiltViewModel<ListDetailViewModel, ListDetailViewModel.Factory> { it.create(key) },
            onBack = onBack,
        )
    }
}
