package com.kokouliss.listanything.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.ui.R

/** A dialog that asks for one line of text. Confirm stays disabled while the field is blank. */
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
                supportingText = supportingText?.let { { Text(it) } },
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
        confirmButton = { TextButton(onClick = submit, enabled = value.isNotBlank()) { Text(confirmLabel) } },
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

/** Name and icon of a list. Used both to create a list and to edit one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEditorDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialEmoji: String = ListEmojis.first(),
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji) }
    val submit = { if (name.isNotBlank()) onConfirm(name.trim(), emoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.core_ui_list_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ListEmojis.forEach { option ->
                        val isSelected = option == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { selected = isSelected }
                                .clickable(role = Role.RadioButton) { emoji = option },
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
