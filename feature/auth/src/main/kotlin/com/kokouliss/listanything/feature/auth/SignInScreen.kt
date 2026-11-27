package com.kokouliss.listanything.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.domain.repository.EmailAuthResult
import com.kokouliss.listanything.core.ui.component.AnyKindWordmark
import com.kokouliss.listanything.core.ui.component.AppLogo
import com.kokouliss.listanything.core.ui.component.TornPaper
import com.kokouliss.listanything.core.ui.component.pageWidth
import com.kokouliss.listanything.core.ui.component.rememberPaperScroll
import com.kokouliss.listanything.core.ui.component.ruledPaper
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.core.ui.theme.Motion
import com.kokouliss.listanything.core.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

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
    val still = rememberReducedMotion()
    Surface(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        // The keyboard is part of safe drawing, so the page gets only the room above it and scrolls in that.
        BoxWithConstraints(Modifier.ruledPaper(MaterialTheme.colorScheme.primary, rememberPaperScroll()).safeDrawingPadding()) {
            // A keyboard, a short window or large text: the logo shrinks and the tagline steps aside for the form.
            val short = maxHeight < SHORT_PAGE
            Column(
                modifier = Modifier
                    .pageWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 32.dp, vertical = if (short) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Springs above and below centre the page on a tall screen and give way on a short one. The
                // page itself has no weight: with one it was squeezed into the screen and cut off, not scrolled.
                Spacer(Modifier.weight(1f))
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val logo by animateDpAsState(
                        if (short) 56.dp else 104.dp,
                        if (still) snap() else tween(Motion.LONG, easing = Motion.Enter),
                        label = "logo",
                    )
                    AppLogo(Modifier.size(logo))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    AnimatedVisibility(
                        visible = !short,
                        enter = if (still) EnterTransition.None else fadeIn(tween(Motion.LONG)) + expandVertically(tween(Motion.LONG)),
                        exit = if (still) ExitTransition.None else fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.LONG)),
                    ) {
                        Text(
                            stringResource(R.string.feature_auth_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    val signingIn = stringResource(R.string.feature_auth_signing_in)
                    Button(
                        onClick = onSignInClick,
                        enabled = !uiState.isSigningIn,
                        modifier = Modifier.padding(top = if (short) 0.dp else 16.dp).then(FieldWidth),
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
                        Note(
                            stringResource(
                                when (error) {
                                    SignInError.NoAccount -> R.string.feature_auth_error_no_account
                                    SignInError.Rejected -> R.string.feature_auth_error_rejected
                                    SignInError.NotConfigured -> R.string.feature_auth_error_not_configured
                                    SignInError.Failed -> R.string.feature_auth_error_failed
                                },
                            ),
                            isError = true,
                        )
                    }
                    OrLine()
                    if (showEmail) {
                        EmailForm(uiState, onEmailSignIn, onCreateAccount, onResetPassword)
                    } else {
                        OutlinedButton(
                            onClick = { showEmail = true },
                            enabled = !uiState.isSigningIn,
                            modifier = FieldWidth,
                        ) {
                            Text(stringResource(R.string.feature_auth_continue_with_email))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // The collection this app belongs to, at the foot of the page. Not while room is short.
                if (!short) AnyKindWordmark(Modifier.padding(top = 24.dp))
            }
        }
    }
}

/** "or" between two short rules, like a line drawn across the page. */
@Composable
private fun OrLine() {
    Row(
        modifier = FieldWidth,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.feature_auth_or),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Sign in, or create an account, with an email address and a password, on a sheet torn off the pad. */
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
    var showPassword by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val canSubmit = !uiState.isSigningIn && email.isNotBlank() &&
        (if (creating) name.isNotBlank() && password.length >= MIN_PASSWORD else password.isNotEmpty())
    val submit = {
        if (canSubmit) {
            // The keyboard goes down, so the answer, good or bad, has the whole page to show in.
            focus.clearFocus()
            if (creating) onCreate(name, email, password) else onSignIn(email, password)
        }
    }
    // An error sits under the field it is about; the rest go in a note under the button.
    val error = uiState.emailError
    val emailError = error?.takeIf { it == EmailAuthResult.InvalidEmail || it == EmailAuthResult.EmailInUse }
    val passwordError = error?.takeIf { it == EmailAuthResult.WeakPassword || it == EmailAuthResult.WrongCredentials }
    val otherError = error?.takeIf { it != emailError && it != passwordError }

    Surface(
        shape = TornPaper,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = FieldWidth,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (creating) R.string.feature_auth_heading_create else R.string.feature_auth_heading_sign_in),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
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
                    modifier = Modifier.keepInView().fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                label = { Text(stringResource(R.string.feature_auth_email)) },
                enabled = !uiState.isSigningIn,
                isError = emailError != null,
                supportingText = emailError?.let { { Text(stringResource(it.message())) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.keepInView(alert = emailError).fillMaxWidth().semantics {
                    contentType = if (creating) ContentType.NewUsername + ContentType.EmailAddress
                    else ContentType.Username + ContentType.EmailAddress
                },
            )
            val show = stringResource(R.string.feature_auth_show_password)
            val hide = stringResource(R.string.feature_auth_hide_password)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                label = { Text(stringResource(R.string.feature_auth_password)) },
                enabled = !uiState.isSigningIn,
                isError = passwordError != null,
                supportingText = when {
                    passwordError != null -> { { Text(stringResource(passwordError.message())) } }
                    creating -> { { Text(stringResource(R.string.feature_auth_password_hint)) } }
                    else -> null
                },
                trailingIcon = {
                    TextButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.semantics { contentDescription = if (showPassword) hide else show },
                    ) {
                        Text(stringResource(if (showPassword) R.string.feature_auth_hide else R.string.feature_auth_show))
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                // The button under it comes along, so "Sign in" is in reach while the password is typed.
                modifier = Modifier.keepInView(alert = passwordError, below = 72.dp).fillMaxWidth().semantics {
                    contentType = if (creating) ContentType.NewPassword else ContentType.Password
                },
            )
            Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (uiState.isSigningIn) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (creating) R.string.feature_auth_create_account else R.string.feature_auth_sign_in))
            }
            uiState.resetSentTo?.let { Note(stringResource(R.string.feature_auth_reset_sent, it), isError = false) }
            otherError?.let { Note(stringResource(it.message()), isError = true) }
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
}

/** A message on its own patch of colour, read out when it appears and scrolled to, wherever the page was. */
@Composable
private fun Note(text: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.keepInView(alert = text).fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Keeps this in sight on the scrolling page: while it has focus, each time the keyboard comes up or
 * changes height, and each time [alert] turns up, so nobody types or gets an error under the keyboard.
 */
@Composable
private fun Modifier.keepInView(alert: Any? = null, below: Dp = 0.dp): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density)
    val show = suspend {
        // The wait lets the keyboard and the page settle; a newer change restarts it.
        delay(Motion.SHORT.toLong())
        requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + with(density) { below.toPx() }))
    }
    LaunchedEffect(focused, keyboard) { if (focused) show() }
    LaunchedEffect(alert) { if (alert != null) show() }
    return bringIntoViewRequester(requester).onSizeChanged { size = it }.onFocusEvent { focused = it.hasFocus }
}

/** Below this the page is short, as with a keyboard up: about what is left of a phone over a tall keyboard. */
private val SHORT_PAGE = 600.dp

private val FieldWidth = Modifier.widthIn(max = 360.dp).fillMaxWidth()

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
