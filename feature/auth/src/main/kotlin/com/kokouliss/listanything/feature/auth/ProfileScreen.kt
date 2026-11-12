package com.kokouliss.listanything.feature.auth

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.ui.component.AnyKindWordmark
import com.kokouliss.listanything.core.ui.component.AppIcon
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.BackButton
import com.kokouliss.listanything.core.ui.component.ConfirmDialog
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.component.pageWidth
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme

/**
 * Language tag to the language's own name, which is not translated. Keep in step with the
 * values-xx folders and with app/src/main/res/xml/locales_config.xml.
 */
private val Languages = listOf("en" to "English", "el" to "Ελληνικά")

/** The tag that means "follow the phone's language". */
private const val SYSTEM_LANGUAGE = ""

/** The choices under Appearance: an AppCompatDelegate night mode and its label. */
private val NightModes = listOf(
    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.feature_auth_same_as_phone,
    AppCompatDelegate.MODE_NIGHT_NO to R.string.feature_auth_appearance_light,
    AppCompatDelegate.MODE_NIGHT_YES to R.string.feature_auth_appearance_dark,
)

@Composable
internal fun ProfileRoute(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Kept here as well: a choice that looks the same as before, such as Light on a light phone,
    // does not restart the activity.
    var nightMode by remember { mutableIntStateOf(AppCompatDelegate.getDefaultNightMode()) }
    ProfileScreen(
        user = user,
        // Not observed: choosing a language restarts the activity, which reads this again.
        languageTag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: SYSTEM_LANGUAGE,
        nightMode = nightMode,
        onLanguageChange = { tag ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        },
        onNightModeChange = { mode ->
            nightMode = mode
            saveNightMode(context, mode)
        },
        onSignOut = viewModel::signOut,
        onBack = onBack,
    )
}

@Composable
internal fun ProfileScreen(
    user: User?,
    languageTag: String,
    nightMode: Int,
    onLanguageChange: (String) -> Unit,
    onNightModeChange: (Int) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.feature_auth_profile_title)
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).pageWidth().verticalScroll(rememberScrollState())) {
            val name = user?.name.orEmpty()
            ListItem(
                leadingContent = { InitialAvatar(name, size = 56.dp) },
                headlineContent = { Text(name, style = MaterialTheme.typography.titleLarge) },
                supportingContent = user?.email?.let { email -> { Text(email) } },
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.feature_auth_appearance))
            Column(Modifier.selectableGroup()) {
                NightModes.forEach { (mode, label) ->
                    Choice(stringResource(label), selected = nightMode == mode, onClick = { onNightModeChange(mode) })
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            SectionTitle(stringResource(R.string.feature_auth_language))
            Column(Modifier.selectableGroup()) {
                Choice(
                    label = stringResource(R.string.feature_auth_same_as_phone),
                    selected = languageTag == SYSTEM_LANGUAGE,
                    onClick = { onLanguageChange(SYSTEM_LANGUAGE) },
                )
                Languages.forEach { (tag, name) ->
                    Choice(label = name, selected = languageTag == tag, onClick = { onLanguageChange(tag) })
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            ListItem(
                leadingContent = { AppIcon(AppIcons.Logout, null) },
                headlineContent = { Text(stringResource(R.string.feature_auth_sign_out)) },
                modifier = Modifier.clickable(role = Role.Button, onClick = { confirmSignOut = true }),
            )
            // The maker, said once to a screen reader: the wordmark alone would say only "AnyKind".
            val maker = stringResource(R.string.feature_auth_maker)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 24.dp)
                    .clearAndSetSemantics { contentDescription = maker },
            ) {
                AnyKindWordmark()
                Text(
                    maker,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.feature_auth_sign_out),
            text = stringResource(R.string.feature_auth_sign_out_confirm_text),
            confirmLabel = stringResource(R.string.feature_auth_sign_out),
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** One radio row of a section. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The whole row is the control. A second click target inside it would be read out twice.
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@PreviewLightDark
@Composable
private fun ProfileScreenPreview() {
    ListAnythingTheme {
        ProfileScreen(
            user = User("u1", "Panos", "panos@example.com"),
            languageTag = "el",
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            onLanguageChange = {},
            onNightModeChange = {},
            onSignOut = {},
            onBack = {},
        )
    }
}
