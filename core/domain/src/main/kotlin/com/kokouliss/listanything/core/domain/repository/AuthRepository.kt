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
}
