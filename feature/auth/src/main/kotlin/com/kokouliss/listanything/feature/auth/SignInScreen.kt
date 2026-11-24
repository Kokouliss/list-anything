package com.kokouliss.listanything.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kokouliss.listanything.core.domain.repository.EmailAuthResult
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.ui.component.AnyKindWordmark
import com.kokouliss.listanything.core.ui.component.AppLogo
import com.kokouliss.listanything.core.ui.component.pageWidth
import com.kokouliss.listanything.core.ui.component.rememberPaperScroll
import com.kokouliss.listanything.core.ui.component.ruledPaper
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme

/** Shown by the app whenever nobody is signed in. */
@Composable
fun SignInRoute() {
    val viewModel: SignInViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    SignInScreen(
        uiState = uiState,
        onSignInClick = { if (activity != null) viewModel.signIn(activity) },
        onEmailSignIn = viewModel::signInWithEmail,
        onCreateAccount = viewModel::createAccount,
        onResetPassword = viewModel::sendPasswordReset,
    )
}

@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    onSignInClick: () -> Unit,
    onEmailSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: (name: String, email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
) {
    var showEmail by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.feature_auth_title)
    Surface(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        // Scrolls when large text or a short window makes the page taller than the screen.
        BoxWithConstraints(Modifier.ruledPaper(MaterialTheme.colorScheme.primary, rememberPaperScroll()).safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .pageWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppLogo(Modifier.size(104.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.feature_auth_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    val signingIn = stringResource(R.string.feature_auth_signing_in)
                    Button(
                        onClick = onSignInClick,
                        enabled = !uiState.isSigningIn,
                        modifier = Modifier.padding(top = 16.dp).widthIn(max = 360.dp).fillMaxWidth(),
                    ) {
                        if (uiState.isSigningIn) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        // The button keeps its name while it waits; the state tells a screen reader why it is off.
                        Text(
                            stringResource(R.string.feature_auth_continue_with_google),
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                if (uiState.isSigningIn) stateDescription = signingIn
                            },
                        )
                    }
                    uiState.error?.let { error ->
                        Text(
                            text = stringResource(
                                when (error) {
                                    SignInError.NoAccount -> R.string.feature_auth_error_no_account
                                    SignInError.Rejected -> R.string.feature_auth_error_rejected
                                    SignInError.NotConfigured -> R.string.feature_auth_error_not_configured
                                    SignInError.Failed -> R.string.feature_auth_error_failed
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Text(
                        stringResource(R.string.feature_auth_or),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showEmail) {
                        EmailForm(uiState, onEmailSignIn, onCreateAccount, onResetPassword)
                    } else {
                        OutlinedButton(
                            onClick = { showEmail = true },
                            enabled = !uiState.isSigningIn,
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.feature_auth_continue_with_email))
                        }
                    }
                }
                // The collection this app belongs to, at the foot of the page.
                AnyKindWordmark()
            }
        }
    }
}

/** Sign in, or create an account, with an email address and a password. */
@Composable
private fun EmailForm(
    uiState: SignInUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onCreate: (name: String, email: String, password: String) -> Unit,
    onReset: (email: String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: a password should not be written into the saved state.
    var password by remember { mutableStateOf("") }
    val canSubmit = !uiState.isSigningIn && email.isNotBlank() &&
        (if (creating) name.isNotBlank() && password.length >= MIN_PASSWORD else password.isNotEmpty())
    val submit = {
        if (canSubmit) {
            if (creating) onCreate(name, email, password) else onSignIn(email, password)
        }
    }
    val fieldWidth = Modifier.widthIn(max = 360.dp).fillMaxWidth()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (creating) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.feature_auth_your_name)) },
                enabled = !uiState.isSigningIn,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = fieldWidth.semantics { contentType = ContentType.PersonFullName },
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(254) },
            label = { Text(stringResource(R.string.feature_auth_email)) },
            enabled = !uiState.isSigningIn,
            isError = uiState.emailError == EmailAuthResult.InvalidEmail,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = fieldWidth.semantics {
                contentType = if (creating) ContentType.NewUsername + ContentType.EmailAddress
                else ContentType.Username + ContentType.EmailAddress
            },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(128) },
            label = { Text(stringResource(R.string.feature_auth_password)) },
            enabled = !uiState.isSigningIn,
            isError = uiState.emailError == EmailAuthResult.WeakPassword,
            supportingText = if (creating) { { Text(stringResource(R.string.feature_auth_password_hint)) } } else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = fieldWidth.semantics {
                contentType = if (creating) ContentType.NewPassword else ContentType.Password
            },
        )
        Button(onClick = submit, enabled = canSubmit, modifier = fieldWidth.padding(top = 8.dp)) {
            if (uiState.isSigningIn) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (creating) R.string.feature_auth_create_account else R.string.feature_auth_sign_in))
        }
        val message = uiState.resetSentTo?.let { stringResource(R.string.feature_auth_reset_sent, it) }
            ?: uiState.emailError?.let { stringResource(it.message()) }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.resetSentTo != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!creating) {
            TextButton(onClick = { onReset(email) }, enabled = email.isNotBlank() && !uiState.isSigningIn) {
                Text(stringResource(R.string.feature_auth_forgot_password))
            }
        }
        TextButton(onClick = { creating = !creating }, enabled = !uiState.isSigningIn) {
            Text(stringResource(if (creating) R.string.feature_auth_have_account else R.string.feature_auth_new_here))
        }
    }
}

/** Firebase turns down shorter passwords; the strings say 6 too. */
private const val MIN_PASSWORD = 6

private fun EmailAuthResult.message(): Int = when (this) {
    EmailAuthResult.WrongCredentials -> R.string.feature_auth_error_wrong_credentials
    EmailAuthResult.InvalidEmail -> R.string.feature_auth_error_invalid_email
    EmailAuthResult.EmailInUse -> R.string.feature_auth_error_email_in_use
    EmailAuthResult.WeakPassword -> R.string.feature_auth_error_weak_password
    EmailAuthResult.TooManyTries -> R.string.feature_auth_error_too_many_tries
    EmailAuthResult.NotEnabled -> R.string.feature_auth_error_email_not_enabled
    EmailAuthResult.Success, EmailAuthResult.Failed -> R.string.feature_auth_error_failed
}

@PreviewLightDark
@Composable
private fun SignInScreenPreview() {
    ListAnythingTheme {
        SignInScreen(
            uiState = SignInUiState(error = SignInError.Failed),
            onSignInClick = {},
            onEmailSignIn = { _, _ -> },
            onCreateAccount = { _, _, _ -> },
            onResetPassword = {},
        )
    }
}
