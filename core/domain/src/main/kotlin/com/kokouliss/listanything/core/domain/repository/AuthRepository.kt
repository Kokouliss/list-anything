package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** The signed-in user, or null when signed out. */
    val currentUser: Flow<User?>

    /**
     * [idToken] is the Google ID token the sign-in screen obtained from the system account picker.
     * Returns false when the token was rejected or the server could not be reached.
     */
    suspend fun signInWithGoogle(idToken: String): Boolean

    suspend fun signInWithEmail(email: String, password: String): EmailAuthResult

    /** Makes an email and password account named [name], signs in and emails a link to confirm the address. */
    suspend fun createAccount(name: String, email: String, password: String): EmailAuthResult

    /** Emails a link to choose a new password. Succeeds for unknown addresses too, so nobody can probe for accounts. */
    suspend fun sendPasswordReset(email: String): EmailAuthResult

    /** Emails the signed-in person a new link to confirm their address. */
    suspend fun sendVerificationEmail(): Boolean

    /** Asks the server whether the address was confirmed meanwhile, so invites by email show up without signing in again. */
    suspend fun refreshEmailVerification()

    /** Lets this device get the signed-in user's notifications. Call it on every start; it is cheap. */
    suspend fun registerDevice()

    /** Also stops this device getting the user's notifications. */
    suspend fun signOut()

    /**
     * Deletes the account for good: the user leaves every space (a space they own goes to its next
     * member, one with nobody else in it is deleted), their devices stop getting notifications and
     * the sign-in is removed. Items they added to shared spaces stay. [idToken] is a fresh Google
     * sign-in token, which Firebase asks for before it deletes an account. Returns false when the
     * server could not be reached or the token belongs to another account.
     */
    suspend fun deleteAccount(idToken: String): Boolean

    /** As [deleteAccount], for an email and password account: [password] confirms it is them. */
    suspend fun deleteAccountWithPassword(password: String): Boolean
}

enum class EmailAuthResult { Success, WrongCredentials, InvalidEmail, EmailInUse, WeakPassword, TooManyTries, NotEnabled, Failed }
