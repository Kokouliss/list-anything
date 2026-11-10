package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
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

    override suspend fun registerDevice() {
        val user = auth.currentUser ?: return
        val token = deviceToken() ?: return
        tokenDocument(user.uid, token)
            .set(mapOf(Fields.UPDATED_AT to FieldValue.serverTimestamp()))
            .logFailure("save the notification address")
    }

    override suspend fun signOut() {
        // shortcut: waits a moment for the server, so signing out also works offline. Then this
        // device keeps getting the person's notifications until someone signs in on it again.
        withTimeoutOrNull(3_000) {
            val user = auth.currentUser
            val token = deviceToken()
            if (user != null && token != null) {
                try {
                    tokenDocument(user.uid, token).delete().await()
                } catch (e: FirebaseException) {
                    Log.w("ListAnythingAuth", "Could not remove the notification address", e)
                }
            }
        }
        auth.signOut()
    }

    /** The address Firebase sends this device's notifications to, or null on a phone that cannot get them. */
    private suspend fun deviceToken(): String? = try {
        messaging.token.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No Google Play services, or no connection the first time.
        Log.w("ListAnythingAuth", "No notification address for this device", e)
        null
    }

    private fun tokenDocument(userId: String, token: String) =
        db.collection(Collections.USERS).document(userId).collection(Collections.TOKENS).document(token)
}
