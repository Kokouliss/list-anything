package com.kokouliss.listanything.feature.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.component.pageWidth
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme

@Composable
internal fun ShareRoute(
    key: ShareKey,
    onAdded: (spaceId: String, listId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    ShareScreen(
        key = key,
        targets = targets,
        onPick = { list ->
            viewModel.addTo(list, key)
            onAdded(list.spaceId, list.id)
        },
        onBack = onBack,
    )
}

/** Asks which list a link shared from another app goes to. */
@Composable
internal fun ShareScreen(
    key: ShareKey,
    targets: List<ShareTarget>?,
    onPick: (SharedList) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.feature_lists_share_title)
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        when {
            targets == null -> LoadingBox(Modifier.padding(padding))
            targets.isEmpty() -> EmptyState(
                icon = AppIcons.Checklist,
                title = stringResource(R.string.feature_lists_share_empty_title),
                body = stringResource(R.string.feature_lists_share_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = padding, modifier = Modifier.pageWidth()) {
                item {
                    ListItem(
                        leadingContent = { Text("🔗", Modifier.clearAndSetSemantics { }) },
                        headlineContent = { Text(key.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = key.link?.let { link ->
                            { Text(link, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                    )
                    HorizontalDivider()
                }
                targets.forEach { target ->
                    item {
                        Text(
                            target.spaceName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                                .semantics { heading() },
                        )
                    }
                    items(target.lists, key = { "${it.spaceId}/${it.id}" }) { list ->
                        ListItem(
                            leadingContent = {
                                Text(list.emoji, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleLarge)
                            },
                            headlineContent = { Text(list.name) },
                            modifier = Modifier.clickable(role = Role.Button) { onPick(list) },
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ShareScreenPreview() {
    ListAnythingTheme {
        ShareScreen(
            key = ShareKey("Blue scarf", "https://shop.example/scarf"),
            targets = listOf(
                ShareTarget("Family", listOf(SharedList("l1", "s1", "Christmas gifts", "🎁"), SharedList("l2", "s1", "Supermarket", "🛒"))),
            ),
            onPick = {},
            onBack = {},
        )
    }
}
