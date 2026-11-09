package com.kokouliss.listanything.feature.spaces

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class SpacesViewModelTest {

    private val family = Space("s1", "Family", "u1", "ABCD2345", listOf(Member("u1", "Panos")), emptyList())
    private val flatmates = family.copy(id = "s2", name = "Flatmates")
    private val spaces = FakeSpaceRepository()
    private val auth = FakeAuthRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the user's spaces and invitations`() = runTest {
        spaces.spaces.value = listOf(family)
        spaces.invitations.value = listOf(flatmates)

        SpacesViewModel(spaces, auth).uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(
                SpacesUiState.Ready(
                    userName = "Panos",
                    userEmail = "panos@example.com",
                    spaces = listOf(family),
                    invitations = listOf(flatmates),
                    isJoining = false,
                ),
            )
        }
    }

    @Test
    fun `joining reports the result and stops the progress indicator`() = runTest {
        spaces.joinResult = JoinResult.InvalidCode
        val viewModel = SpacesViewModel(spaces, auth)

        viewModel.events.test {
            viewModel.joinWithCode("WRONG")

            assertThat(awaitItem()).isEqualTo(SpacesEvent.JoinFinished(JoinResult.InvalidCode))
        }
        viewModel.uiState.test {
            assertThat((expectMostRecentItem() as SpacesUiState.Ready).isJoining).isFalse()
        }
    }

    @Test
    fun `a failing stream shows the error state and retry recovers`() = runTest {
        spaces.failSpaces = true
        val viewModel = SpacesViewModel(spaces, auth)

        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isEqualTo(SpacesUiState.Error)

            spaces.failSpaces = false
            viewModel.retry()

            assertThat(expectMostRecentItem()).isInstanceOf(SpacesUiState.Ready::class.java)
        }
    }
}

private class FakeAuthRepository : AuthRepository {
    override val currentUser: Flow<User?> = flowOf(User("u1", "Panos", "panos@example.com"))
    override suspend fun signInWithGoogle(idToken: String) = true
    override suspend fun signOut() = Unit
}

private class FakeSpaceRepository : SpaceRepository {
    val spaces = MutableStateFlow(emptyList<Space>())
    val invitations = MutableStateFlow(emptyList<Space>())
    var failSpaces = false
    var joinResult = JoinResult.Joined

    override fun observeSpaces(): Flow<List<Space>> = flow {
        if (failSpaces) throw IOException("permission denied")
        emitAll(spaces)
    }

    override fun observeInvitations(): Flow<List<Space>> = invitations
    override fun observeSpace(spaceId: String): Flow<Space?> = flowOf(null)
    override suspend fun joinWithCode(code: String) = joinResult

    override suspend fun createSpace(name: String) = Unit
    override suspend fun renameSpace(spaceId: String, name: String) = Unit
    override suspend fun acceptInvitation(spaceId: String) = Unit
    override suspend fun declineInvitation(spaceId: String) = Unit
    override suspend fun inviteEmail(spaceId: String, email: String) = Unit
    override suspend fun cancelEmailInvite(spaceId: String, email: String) = Unit
    override suspend fun removeMember(space: Space, memberId: String) = Unit
    override suspend fun resetInviteCode(space: Space) = Unit
    override suspend fun leaveSpace(spaceId: String) = Unit
    override suspend fun deleteSpace(space: Space) = Unit
}
