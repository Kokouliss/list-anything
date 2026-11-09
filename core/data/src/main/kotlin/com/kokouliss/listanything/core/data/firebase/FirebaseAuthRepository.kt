package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

internal class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val currentUser: Flow<User?> = auth.userFlow().map { user ->
        user?.let { User(id = it.uid, name = it.memberName, email = it.email) }
    }

    override suspend fun signInWithGoogle(idToken: String): Boolean = try {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        true
    } catch (e: FirebaseException) {
        Log.w("ListAnythingAuth", "Firebase rejected the Google sign-in", e)
        false
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}
