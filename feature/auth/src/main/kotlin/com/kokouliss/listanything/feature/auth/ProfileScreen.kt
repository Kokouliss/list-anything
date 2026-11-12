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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
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
import com.kokouliss.listanything.core.ui.component.InitialAvatar
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme

/**
 * Language tag to the language's own name, which is not translated. Keep in step with the
 * values-xx folders and with app/src/main/res/xml/locales_config.xml.
 */
private val Languages = listOf("en" to "English", "el" to "Ελληνικά")

/** The tag that means "follow the phone's language". */
private const val SYSTEM_LANGUAGE = ""

@Composable
internal fun ProfileRoute(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    ProfileScreen(
        user = user,
        // Not observed: choosing a language restarts the activity, which reads this again.
        languageTag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: SYSTEM_LANGUAGE,
        onLanguageChange = { tag ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        },
        onSignOut = viewModel::signOut,
        onBack = onBack,
    )
}

@Composable
internal fun ProfileScreen(
    user: User?,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_auth_profile_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            val name = user?.name.orEmpty()
            ListItem(
                leadingContent = { InitialAvatar(name, size = 56.dp) },
                headlineContent = { Text(name, style = MaterialTheme.typography.titleLarge) },
                supportingContent = user?.email?.let { email -> { Text(email) } },
            )
            HorizontalDivider()

            Text(
                stringResource(R.string.feature_auth_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
            Column(Modifier.selectableGroup()) {
                LanguageOption(
                    label = stringResource(R.string.feature_auth_language_system),
                    selected = languageTag == SYSTEM_LANGUAGE,
                    onClick = { onLanguageChange(SYSTEM_LANGUAGE) },
                )
                Languages.forEach { (tag, name) ->
                    LanguageOption(label = name, selected = languageTag == tag, onClick = { onLanguageChange(tag) })
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            ListItem(
                leadingContent = { AppIcon(AppIcons.Logout, null) },
                headlineContent = { Text(stringResource(R.string.feature_auth_sign_out)) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onSignOut),
            )
            AnyKindWordmark(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 24.dp))
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
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
            onLanguageChange = {},
            onSignOut = {},
            onBack = {},
        )
    }
}
