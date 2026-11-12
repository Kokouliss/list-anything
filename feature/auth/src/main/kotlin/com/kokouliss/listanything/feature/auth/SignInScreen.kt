package com.kokouliss.listanything.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.listanything.core.ui.component.AnyKindWordmark
import com.kokouliss.listanything.core.ui.component.AppLogo
import com.kokouliss.listanything.core.ui.component.ruledPaper
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import kotlinx.coroutines.launch

/** Shown by the app whenever nobody is signed in. */
@Composable
fun SignInRoute() {
    val viewModel: SignInViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    SignInScreen(
        uiState = uiState,
        onSignInClick = {
            if (activity != null) {
                viewModel.onSignInStarted()
                scope.launch { viewModel.onGoogleResult(requestGoogleIdToken(activity)) }
            }
        },
    )
}

@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    onSignInClick: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .ruledPaper(MaterialTheme.colorScheme.primary)
                .safeDrawingPadding()
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
                    stringResource(R.string.feature_auth_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.feature_auth_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onSignInClick,
                    enabled = !uiState.isSigningIn,
                    modifier = Modifier.padding(top = 16.dp).widthIn(max = 360.dp).fillMaxWidth(),
                ) {
                    if (uiState.isSigningIn) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.feature_auth_continue_with_google))
                    }
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
            }
            // The collection this app belongs to, at the foot of the page.
            AnyKindWordmark()
        }
    }
}

@PreviewLightDark
@Composable
private fun SignInScreenPreview() {
    ListAnythingTheme {
        SignInScreen(uiState = SignInUiState(error = SignInError.Failed), onSignInClick = {})
    }
}
