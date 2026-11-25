package com.kokouliss.listanything.feature.spaces

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.domain.model.Activity
import com.kokouliss.listanything.core.domain.model.ActivityKind
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme

/** The space's history, newest first, from `forMember`. Null while it is still loading. */
@Composable
internal fun HistoryTab(lines: List<Activity>?, now: Long = System.currentTimeMillis()) {
    when {
        lines == null -> LoadingBox()
        lines.isEmpty() -> EmptyState(
            icon = AppIcons.History,
            title = stringResource(R.string.feature_spaces_history_empty_title),
            body = stringResource(R.string.feature_spaces_history_empty_body),
        )
        // Cards like the lists and drawings of the other tabs, so the ruled paper never runs through a line.
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines, key = { it.id }) { ElevatedCard(Modifier.animateItem().fillMaxWidth()) { HistoryRow(it, now) } }
        }
    }
}

@Composable
private fun HistoryRow(line: Activity, now: Long) {
    // shortcut: "5 minutes ago" is worked out when the tab is drawn, not on a timer. A new line redraws it.
    val time = if (now - line.atMillis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.feature_spaces_history_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(line.atMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { InitialAvatar(line.byName) },
        headlineContent = { Text(sentence(line)) },
        supportingContent = {
            // A line about a list names it already.
            val where = if (line.kind == ActivityKind.LIST_ADDED || line.kind == ActivityKind.LIST_DELETED) "" else line.listName
            Text(
                listOf(where, time).filter { it.isNotEmpty() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // One line for a screen reader: who did what, then where and when.
        modifier = Modifier.semantics(mergeDescendants = true) { },
    )
}

@Composable
private fun sentence(line: Activity): String {
    // Three names, then how many more: a line is read at a glance.
    val what = if (line.texts.size <= 3) {
        line.texts.joinToString(", ")
    } else {
        val more = line.texts.size - 3
        pluralStringResource(R.plurals.feature_spaces_history_more, more, line.texts.take(3).joinToString(", "), more)
    }
    val text = when (line.kind) {
        ActivityKind.ADDED -> R.string.feature_spaces_history_added
        ActivityKind.CHECKED -> R.string.feature_spaces_history_checked
        ActivityKind.UNCHECKED -> R.string.feature_spaces_history_unchecked
        ActivityKind.CHANGED -> if (line.was == null) R.string.feature_spaces_history_changed else R.string.feature_spaces_history_renamed
        ActivityKind.DELETED -> R.string.feature_spaces_history_deleted
        ActivityKind.RESTORED -> R.string.feature_spaces_history_restored
        ActivityKind.LIST_ADDED -> R.string.feature_spaces_history_list_added
        ActivityKind.LIST_DELETED -> R.string.feature_spaces_history_list_deleted
    }
    return stringResource(text, line.byName, what, line.was.orEmpty())
}

@PreviewLightDark
@Composable
private fun HistoryTabPreview() {
    val now = System.currentTimeMillis()
    ListAnythingTheme {
        HistoryTab(
            lines = listOf(
                Activity("a1", ActivityKind.CHECKED, "u2", "Maria", "l1", "🛒 Supermarket", listOf("Milk", "Eggs", "Bread", "Apples", "Coffee"), now - 120_000),
                Activity("a2", ActivityKind.CHANGED, "u1", "Panos", "l1", "🛒 Supermarket", listOf("Oat milk"), now - 3_600_000, was = "Milk"),
                Activity("a3", ActivityKind.LIST_ADDED, "u1", "Panos", "l2", "🎁 Birthday", listOf("Birthday"), now - 86_400_000),
            ),
            now = now,
        )
    }
}
