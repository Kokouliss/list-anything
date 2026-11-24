package com.kokouliss.listanything.feature.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.EmailAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class NameViewModelTest {

    private val auth = FakeAuthRepository()
    private val viewModel by lazy { NameViewModel(auth) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a new Google account is asked once, and keeping the name stops the question`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem()).isEqualTo(NameDialogState.Closed)
            auth.asksForName.value = true
            assertThat(awaitItem()).isEqualTo(NameDialogState.Open)
            viewModel.close()
            assertThat(expectMostRecentItem()).isEqualTo(NameDialogState.Closed)
            assertThat(auth.asksForName.value).isFalse()
        }
    }

    @Test
    fun `a name that could not be saved keeps the dialog open with the error, and a saved one closes it`() = runTest {
        viewModel.state.test {
            skipItems(1)
            viewModel.open()
            auth.saves = false
            viewModel.save("Maria")
            assertThat(expectMostRecentItem()).isEqualTo(NameDialogState.Failed)
            auth.saves = true
            viewModel.save("Maria")
            assertThat(expectMostRecentItem()).isEqualTo(NameDialogState.Closed)
            assertThat(auth.names).containsExactly("Maria", "Maria")
        }
    }
}

private class FakeAuthRepository : AuthRepository {
    var saves = true
    val names = mutableListOf<String>()
    override val asksForName = MutableStateFlow(false)
    override val currentUser = flowOf(User("u1", "Panos", "panos@example.com"))
    override suspend fun setName(name: String): Boolean {
        names += name
        if (saves) asksForName.value = false
        return saves
    }
    override fun keepName() {
        asksForName.value = false
    }
    override suspend fun signInWithGoogle(idToken: String) = true
    override suspend fun signInWithEmail(email: String, password: String) = EmailAuthResult.Success
    override suspend fun createAccount(name: String, email: String, password: String) = EmailAuthResult.Success
    override suspend fun sendPasswordReset(email: String) = EmailAuthResult.Success
    override suspend fun sendVerificationEmail() = true
    override suspend fun refreshEmailVerification() = Unit
    override suspend fun registerDevice() = Unit
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(idToken: String) = true
    override suspend fun deleteAccountWithPassword(password: String) = true
}
