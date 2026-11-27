package com.kokouliss.listanything.feature.spaces

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.domain.model.ChatLook
import com.kokouliss.listanything.core.domain.model.ChatMute
import com.kokouliss.listanything.core.domain.model.MAX_CHAT_EMOJI_LENGTH
import com.kokouliss.listanything.core.domain.model.MUTED_FOREVER
import com.kokouliss.listanything.core.domain.model.isOnlyEmoji
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.TornPaper
import com.kokouliss.listanything.core.ui.theme.ChatColors
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.theme.chatColors

/** What the quick button can send, besides an emoji a member types. The first one is the default. */
private val QuickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉", "✅", "🛒", "😘", "👏")

/** The name of each colour in [ChatColors], in the same order. */
private val ChatColorNames = listOf(
    R.string.feature_spaces_chat_color_teal,
    R.string.feature_spaces_chat_color_red,
    R.string.feature_spaces_chat_color_ochre,
    R.string.feature_spaces_chat_color_mauve,
    R.string.feature_spaces_chat_color_green,
    R.string.feature_spaces_chat_color_graphite,
)

// A custom colour is a hue and a brightness; how strong it is stays fixed, so it sits with the others.
private const val CUSTOM_SATURATION = 0.7f
private const val DARKEST = 0.3f

/** The bell of the chat: mutes it for a while or for good, and says until when it is muted. */
@Composable
internal fun ChatMuteButton(mutedUntil: Long?, onMute: (ChatMute?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val choose = { mute: ChatMute? ->
        expanded = false
        onMute(mute)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            if (mutedUntil == null) {
                AppIcon(AppIcons.Notifications, stringResource(R.string.feature_spaces_chat_mute))
            } else {
                AppIcon(AppIcons.NotificationsOff, stringResource(R.string.feature_spaces_chat_is_muted))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (mutedUntil == null) {
                Text(
                    stringResource(R.string.feature_spaces_chat_mute),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChatMute.entries.forEach { mute ->
                    DropdownMenuItem(text = { Text(stringResource(mute.label())) }, onClick = { choose(mute) })
                }
            } else {
                val until = if (mutedUntil >= MUTED_FOREVER) {
                    stringResource(R.string.feature_spaces_chat_muted_forever)
                } else {
                    // The time alone says enough today; later than that, the day goes with it.
                    val day = if (DateUtils.isToday(mutedUntil)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
                    stringResource(
                        R.string.feature_spaces_chat_muted_until,
                        DateUtils.formatDateTime(context, mutedUntil, DateUtils.FORMAT_SHOW_TIME or day),
                    )
                }
                Text(
                    until,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_spaces_chat_unmute)) },
                    leadingIcon = { AppIcon(AppIcons.Notifications, null) },
                    onClick = { choose(null) },
                )
            }
        }
    }
}

private fun ChatMute.label(): Int = when (this) {
    ChatMute.HOUR -> R.string.feature_spaces_chat_mute_hour
    ChatMute.EIGHT_HOURS -> R.string.feature_spaces_chat_mute_eight_hours
    ChatMute.DAY -> R.string.feature_spaces_chat_mute_day
    ChatMute.FOREVER -> R.string.feature_spaces_chat_mute_forever
}

/** The colour of the chat and its quick emoji. Each choice is saved as it is made, for everyone in the space. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatLookSheet(look: ChatLook, onLook: (ChatLook) -> Unit, onDismiss: () -> Unit) {
    val isCustom = look.color != null && look.color !in ChatColors
    var showCustom by rememberSaveable { mutableStateOf(isCustom) }
    // The sliders start from the custom colour the space has, or from a blue in the middle.
    val start = remember {
        FloatArray(3).also { hsv -> if (isCustom) android.graphics.Color.colorToHSV(look.color ?: 0, hsv) else { hsv[0] = 210f; hsv[2] = 0.7f } }
    }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var brightness by rememberSaveable { mutableFloatStateOf(start[2].coerceAtLeast(DARKEST)) }
    val custom = android.graphics.Color.HSVToColor(floatArrayOf(hue, CUSTOM_SATURATION, brightness))
    // The preview follows the sliders while they move; the space gets the colour when the finger lifts.
    val shown = if (showCustom) custom else look.color

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = TornPaper,
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.feature_spaces_chat_look))
            Hint(stringResource(R.string.feature_spaces_chat_look_hint))

            SectionTitle(stringResource(R.string.feature_spaces_chat_color))
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch(null, stringResource(R.string.feature_spaces_chat_color_ink), selected = !showCustom && look.color == null) {
                    showCustom = false
                    onLook(look.copy(color = null))
                }
                ChatColors.forEachIndexed { index, color ->
                    Swatch(color, stringResource(ChatColorNames[index]), selected = !showCustom && look.color == color) {
                        showCustom = false
                        onLook(look.copy(color = color))
                    }
                }
                Swatch(custom, stringResource(R.string.feature_spaces_chat_color_custom), selected = showCustom, isCustom = true) {
                    showCustom = true
                    onLook(look.copy(color = custom))
                }
            }
            if (showCustom) {
                val save = { onLook(look.copy(color = custom)) }
                val hueLabel = stringResource(R.string.feature_spaces_chat_color_hue)
                Text(hueLabel, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    onValueChangeFinished = save,
                    valueRange = 0f..360f,
                    modifier = Modifier.semantics { contentDescription = hueLabel },
                )
                val brightnessLabel = stringResource(R.string.feature_spaces_chat_color_brightness)
                Text(brightnessLabel, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = save,
                    valueRange = DARKEST..1f,
                    modifier = Modifier.semantics { contentDescription = brightnessLabel },
                )
            }
            val (fill, onFill) = chatColors(shown)
            Surface(color = fill, contentColor = onFill, shape = MaterialTheme.shapes.large, modifier = Modifier.align(Alignment.End)) {
                Text(
                    stringResource(R.string.feature_spaces_chat_preview),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            SectionTitle(stringResource(R.string.feature_spaces_chat_emoji))
            Hint(stringResource(R.string.feature_spaces_chat_emoji_hint))
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickEmojis.forEach { option ->
                    val isSelected = option == look.emoji
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp).selectable(selected = isSelected, role = Role.RadioButton) { onLook(look.copy(emoji = option)) },
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(option, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            // Any other emoji: taken as soon as what is typed is an emoji and nothing else.
            var own by rememberSaveable { mutableStateOf(look.emoji.takeIf { it !in QuickEmojis }.orEmpty()) }
            OutlinedTextField(
                value = own,
                onValueChange = { typed ->
                    own = typed.take(MAX_CHAT_EMOJI_LENGTH)
                    if (isOnlyEmoji(own)) onLook(look.copy(emoji = own.trim()))
                },
                label = { Text(stringResource(R.string.feature_spaces_chat_emoji_own)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One colour to pick. The tick, not the colour alone, says which one is chosen. */
@Composable
private fun Swatch(color: Int?, name: String, selected: Boolean, isCustom: Boolean = false, onClick: () -> Unit) {
    val (fill, onFill) = chatColors(color)
    // Until it is chosen, "your own colour" is a palette, not one more colour among the others.
    val plain = isCustom && !selected
    Surface(
        shape = CircleShape,
        color = if (plain) MaterialTheme.colorScheme.surfaceContainerHigh else fill,
        contentColor = if (plain) MaterialTheme.colorScheme.onSurface else onFill,
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent),
        modifier = Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                selected -> AppIcon(AppIcons.Check, null)
                isCustom -> AppIcon(AppIcons.Palette, null)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ChatLookSheetPreview() {
    ListAnythingTheme { ChatLookSheet(ChatLook(color = ChatColors[1], emoji = "❤️"), onLook = {}, onDismiss = {}) }
}
