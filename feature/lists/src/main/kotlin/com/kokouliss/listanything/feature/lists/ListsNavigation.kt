package com.kokouliss.listanything.feature.lists

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** One list with its items. */
@Serializable
data class ListDetailKey(
    val spaceId: String,
    val listId: String,
    /** Opens with the add bar out, for the plus on the home-screen widget. */
    val adding: Boolean = false,
) : NavKey

/** Asks which list gets a link that another app shared. [text] becomes the item, [link] its link. */
@Serializable
data class ShareKey(val text: String, val link: String?) : NavKey

fun EntryProviderScope<NavKey>.listsEntries(
    onBack: () -> Unit,
    onSharedItemAdded: (spaceId: String, listId: String) -> Unit,
    /** The list was deleted or access to it ended: the screen closes and [message] goes on the one below. */
    onGone: (message: String) -> Unit,
    /** Puts a widget of the list on the home screen. Null when the launcher cannot place widgets on request. */
    onPinToHome: ((spaceId: String, listId: String) -> Unit)? = null,
) {
    entry<ShareKey> { key ->
        ShareRoute(key = key, onAdded = onSharedItemAdded, onBack = onBack)
    }
    entry<ListDetailKey> { key ->
        ListDetailRoute(
            viewModel = hiltViewModel<ListDetailViewModel, ListDetailViewModel.Factory> { it.create(key) },
            onBack = onBack,
            onGone = onGone,
            startAdding = key.adding,
            onPinToHome = onPinToHome?.let { pin -> { pin(key.spaceId, key.listId) } },
        )
    }
}
