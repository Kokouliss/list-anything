package com.kokouliss.listanything.feature.lists

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** One list with its items. */
@Serializable
data class ListDetailKey(val spaceId: String, val listId: String) : NavKey

fun EntryProviderScope<NavKey>.listsEntries(onBack: () -> Unit) {
    entry<ListDetailKey> { key ->
        ListDetailRoute(
            viewModel = hiltViewModel<ListDetailViewModel, ListDetailViewModel.Factory> { it.create(key) },
            onBack = onBack,
        )
    }
}
