package com.kokouliss.listanything

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.ui.component.AppIcons
import com.kokouliss.listanything.core.ui.component.EmptyState
import com.kokouliss.listanything.core.ui.component.LoadingBox
import com.kokouliss.listanything.core.ui.theme.ListAnythingTheme
import com.kokouliss.listanything.feature.auth.ProfileKey
import com.kokouliss.listanything.feature.auth.SignInRoute
import com.kokouliss.listanything.feature.auth.profileEntries
import com.kokouliss.listanything.feature.lists.ListDetailKey
import com.kokouliss.listanything.feature.lists.listsEntries
import com.kokouliss.listanything.feature.spaces.SpaceDetailKey
import com.kokouliss.listanything.feature.spaces.SpacesKey
import com.kokouliss.listanything.feature.spaces.spacesEntries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal enum class AuthState { Loading, SignedOut, SignedIn }

@HiltViewModel
internal class AppViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.currentUser
        .map { user -> if (user == null) AuthState.SignedOut else AuthState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)
}

@Composable
internal fun ListAnythingApp(viewModel: AppViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.Loading -> Surface { LoadingBox() }
        AuthState.SignedOut -> SignInRoute()
        AuthState.SignedIn -> SignedInNavigation()
    }
}

@Composable
private fun SignedInNavigation() {
    val backStack = rememberNavBackStack(SpacesKey)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            spacesEntries(
                onOpenSpace = { spaceId -> backStack.add(SpaceDetailKey(spaceId)) },
                onOpenList = { spaceId, listId -> backStack.add(ListDetailKey(spaceId, listId)) },
                onOpenProfile = { backStack.add(ProfileKey) },
                onBack = goBack,
            )
            listsEntries(onBack = goBack)
            profileEntries(onBack = goBack)
        },
    )
}

/** Shown by builds made without `app/google-services.json`, instead of crashing on the first Firebase call. */
@Composable
internal fun SetupNeededScreen() {
    Surface {
        EmptyState(
            icon = AppIcons.Refresh,
            title = stringResource(R.string.setup_needed_title),
            body = stringResource(R.string.setup_needed_body),
        )
    }
}

@PreviewLightDark
@Composable
private fun SetupNeededScreenPreview() {
    ListAnythingTheme(dynamicColor = false) { SetupNeededScreen() }
}
