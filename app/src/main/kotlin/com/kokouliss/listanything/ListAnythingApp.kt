package com.kokouliss.listanything

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kokouliss.listanything.feature.lists.ShareKey
import com.kokouliss.listanything.feature.lists.listsEntries
import com.kokouliss.listanything.feature.spaces.SpaceDetailKey
import com.kokouliss.listanything.feature.spaces.SpacesKey
import com.kokouliss.listanything.feature.spaces.spacesEntries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal enum class AuthState { Loading, SignedOut, SignedIn }

@HiltViewModel
internal class AppViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.currentUser
        .map { user -> if (user == null) AuthState.SignedOut else AuthState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    init {
        // On every start and after every sign-in, because the address notifications go to can change.
        viewModelScope.launch {
            authRepository.currentUser.filterNotNull().collect { authRepository.registerDevice() }
        }
    }
}

@Composable
internal fun ListAnythingApp(launch: Launch, onLaunchHandled: () -> Unit, viewModel: AppViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.Loading -> Surface { LoadingBox() }
        AuthState.SignedOut -> SignInRoute()
        // Until then [launch] waits, so an invite link also works for someone who signs in first.
        AuthState.SignedIn -> SignedInNavigation(launch, onLaunchHandled)
    }
}

@Composable
private fun SignedInNavigation(launch: Launch, onLaunchHandled: () -> Unit) {
    val backStack = rememberNavBackStack(SpacesKey)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    LaunchedEffect(launch) {
        val screen = launch.list ?: launch.shared?.let { ShareKey(it.text, it.link) }
        if (screen != null) {
            backStack.add(screen)
            onLaunchHandled()
        }
    }

    // Android 13 and newer show reminders and notifications only after this question. Asking again
    // after an answer shows nothing, so there is no need to remember that it was asked.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
                inviteCode = launch.inviteCode,
                onInviteHandled = onLaunchHandled,
            )
            listsEntries(
                onBack = goBack,
                onSharedItemAdded = { spaceId, listId ->
                    // The list takes the place of the "which list?" screen.
                    backStack.removeLastOrNull()
                    backStack.add(ListDetailKey(spaceId, listId))
                },
            )
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
