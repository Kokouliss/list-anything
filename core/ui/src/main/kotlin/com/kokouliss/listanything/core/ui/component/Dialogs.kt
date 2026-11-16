package com.kokouliss.listanything.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.ui.R
import com.kokouliss.listanything.core.domain.model.ListKind

/**
 * A dialog that asks for one line of text. Confirm stays disabled while the field is blank or
 * [isBusy]; [errorText] shows under the field and keeps the dialog open for another try.
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    maxLength: Int = 60,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    supportingText: String? = null,
    isBusy: Boolean = false,
    errorText: String? = null,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val submit = { if (value.isNotBlank()) onConfirm(value.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(maxLength) },
                label = { Text(label) },
                enabled = !isBusy,
                isError = errorText != null,
                supportingText = when {
                    errorText != null -> { { Text(errorText) } }
                    value.length >= maxLength -> { { Text(stringResource(R.string.core_ui_at_limit, maxLength)) } }
                    supportingText != null -> { { Text(supportingText) } }
                    else -> null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = capitalization,
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = value.isNotBlank() && !isBusy) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_cancel)) } },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_cancel)) } },
    )
}

/** Emoji a list can use as its icon. The first one is the default. */
val ListEmojis = listOf("📝", "🛒", "🍽️", "🎁", "✅", "✈️", "🏠", "🎬", "📚", "💊", "🐾", "💡")

/** The name of each kind of list, for the create dialog and the list settings. */
fun ListKind.label(): Int = when (this) {
    ListKind.SHOPPING -> R.string.core_ui_kind_shopping
    ListKind.TODO -> R.string.core_ui_kind_todo
    ListKind.WISH_LIST -> R.string.core_ui_kind_wish_list
    ListKind.PLACES -> R.string.core_ui_kind_places
    ListKind.NOTES -> R.string.core_ui_kind_notes
    ListKind.EVERYTHING -> R.string.core_ui_kind_everything
}

/**
 * Name and icon of a list. Used both to create a list and to edit one. [withKind] adds the kind
 * of list, which a new list is set up for; an existing list changes its features in its settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEditorDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, emoji: String, kind: ListKind) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialEmoji: String = ListEmojis.first(),
    withKind: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji) }
    var kind by rememberSaveable { mutableStateOf(ListKind.EVERYTHING) }
    val submit = { if (name.isNotBlank()) onConfirm(name.trim(), emoji, kind) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.core_ui_list_name)) },
                    supportingText = if (name.length < 60) null else { { Text(stringResource(R.string.core_ui_at_limit, 60)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (withKind) {
                    Text(
                        stringResource(R.string.core_ui_list_kind),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListKind.entries.forEach { option ->
                            FilterChip(
                                selected = option == kind,
                                onClick = {
                                    kind = option
                                    // A kind brings its icon along; the icon can still be changed below.
                                    emoji = option.emoji
                                },
                                label = { Text(stringResource(option.label())) },
                                // Material's chip border is the faint ruled-line blue; the edge of a control needs the outline.
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = option == kind,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.core_ui_list_icon),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ListEmojis.forEach { option ->
                        val isSelected = option == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(selected = isSelected, role = Role.RadioButton) { emoji = option },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(option, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = submit, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_cancel)) } },
    )
}
