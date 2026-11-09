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

    suspend fun signOut()
}
