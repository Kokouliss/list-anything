package com.kokouliss.listanything.feature.spaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.pageWidth
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One list of a space with its items: what the search looks through. */
internal data class SearchSource(val space: Space, val list: SharedList, val items: List<ListItem>)

/** A list whose name matches, with [item] null, or an item whose text or note matches. */
internal data class SearchHit(val space: Space, val list: SharedList, val item: ListItem?)

/** Lists and items that contain [query], ignoring case, in the order of the sources. Deleted items are left out. */
internal fun List<SearchSource>.search(query: String): List<SearchHit> {
    val words = query.trim()
    if (words.isEmpty()) return emptyList()
    return flatMap { (space, list, items) ->
        val listHits = if (list.name.contains(words, ignoreCase = true)) listOf(SearchHit(space, list, null)) else emptyList()
        listHits + items
            .filter { !it.isDeleted && (it.text.contains(words, ignoreCase = true) || it.note?.contains(words, ignoreCase = true) == true) }
            .map { SearchHit(space, list, it) }
    }
}

@HiltViewModel
internal class SearchViewModel @Inject constructor(
    spaceRepository: SpaceRepository,
    listRepository: ListRepository,
    itemRepository: ItemRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    // shortcut: listens to every list of every space and filters on the phone, which Firestore
    // cannot do for "contains". Fine for a few dozen lists; add a search index when it is not.
    private val sources: Flow<List<SearchSource>> = spaceRepository.observeSpaces().flatMapLatest { spaces ->
        spaces.map { space ->
            listRepository.observeLists(space.id).catch { emit(emptyList()) }.flatMapLatest { lists ->
                lists.map { list ->
                    itemRepository.observeItems(ListRef(space.id, list.id))
                        // Losing access to a space ends its streams with an error; the space just drops out.
                        .catch { emit(emptyList()) }
                        .map { items -> SearchSource(space, list, items) }
                }.combineAll()
            }
        }.combineAll().map { it.flatten() }
    }.catch { emit(emptyList()) }

    /** Null until every list has arrived once. */
    val hits: StateFlow<List<SearchHit>?> = combine(query, sources) { words, sources -> sources.search(words) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Like combine, but an empty list of flows gives one empty list instead of nothing. */
private inline fun <reified T> List<Flow<T>>.combineAll(): Flow<List<T>> =
    if (isEmpty()) flowOf(emptyList()) else combine(this) { it.toList() }

@Composable
internal fun SearchRoute(
    onOpenList: (spaceId: String, listId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hits by viewModel.hits.collectAsStateWithLifecycle()
    SearchScreen(query, hits, onQueryChange = { viewModel.query.value = it }, onOpenList = onOpenList, onBack = onBack)
}

@Composable
internal fun SearchScreen(
    query: String,
    /** Null while the lists are still loading. */
    hits: List<SearchHit>?,
    onQueryChange: (String) -> Unit,
    onOpenList: (spaceId: String, listId: String) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.feature_spaces_search)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).pageWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.feature_spaces_search_hint)) },
                singleLine = true,
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            AppIcon(AppIcons.Close, stringResource(R.string.feature_spaces_search_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focusRequester),
            )
            when {
                query.isBlank() -> Unit
                hits == null -> LoadingBox()
                hits.isEmpty() -> EmptyState(
                    icon = AppIcons.Search,
                    title = stringResource(R.string.feature_spaces_search_nothing),
                    body = stringResource(R.string.feature_spaces_search_nothing_body, query.trim()),
                )
                else -> LazyColumn {
                    items(hits, key = { "${it.list.id}/${it.item?.id}" }) { hit ->
                        SearchRow(hit, onClick = { onOpenList(hit.space.id, hit.list.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, onClick: () -> Unit) {
    val item = hit.item
    ListItem(
        // The icon is decoration; a screen reader gets the names alone.
        leadingContent = { Text(hit.list.emoji, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.headlineSmall) },
        headlineContent = {
            Text(
                item?.text ?: hit.list.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item?.isChecked == true) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = { Text(if (item == null) hit.space.name else "${hit.space.name} › ${hit.list.name}") },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@PreviewLightDark
@Composable
private fun SearchScreenPreview() {
    val space = Space("s1", "Family", "u1", "ABCD2345", emptyList(), emptyList())
    val list = SharedList("l1", "s1", "Supermarket", "🛒")
    ListAnythingTheme {
        SearchScreen(
            query = "milk",
            hits = listOf(
                SearchHit(space, list, ListItem("i1", "Milk", true, "Maria", "Panos", null, null, 0)),
                SearchHit(space, list, ListItem("i2", "Almond milk", false, null, "Panos", null, null, 0)),
            ),
            onQueryChange = {},
            onOpenList = { _, _ -> },
            onBack = {},
        )
    }
}
