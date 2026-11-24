package com.kokouliss.listanything.core.data.firebase

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.EmailAuthResult
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val spaces: FirestoreSpaceRepository,
) : AuthRepository {

    override val currentUser: Flow<User?> = auth.userFlow().map { user ->
        user?.let {
            User(
                id = it.uid,
                name = it.memberName,
                email = it.email,
                usesPassword = it.providerData.any { p -> p.providerId == EmailAuthProvider.PROVIDER_ID },
                emailVerified = it.isEmailVerified,
            )
        }
    }

    override val asksForName = MutableStateFlow(false)

    override suspend fun signInWithGoogle(idToken: String): Boolean = try {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        // shortcut: kept in memory, so closing the app before answering skips the question. Settings can still rename.
        if (result.additionalUserInfo?.isNewUser == true) asksForName.value = true
        true
    } catch (e: FirebaseException) {
        Log.w("ListAnythingAuth", "Firebase rejected the Google sign-in", e)
        false
    }

    override suspend fun signInWithEmail(email: String, password: String) = emailAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun createAccount(name: String, email: String, password: String) = emailAuth {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email.trim(), password).await().user)
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        // A new token makes the signed-in screens show the name instead of the start of the address.
        user.getIdToken(true).await()
        sendVerificationEmail()
    }

    override suspend fun setName(name: String): Boolean = try {
        val user = auth.requireUser()
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        // As after creating an account: a new token makes the signed-in screens show the new name.
        user.getIdToken(true).await()
        asksForName.value = false
        spaces.renameMe(name.trim())
        true
    } catch (e: FirebaseException) {
        Log.w("ListAnythingAuth", "Could not change the name", e)
        false
    }

    override fun keepName() {
        asksForName.value = false
    }

    override suspend fun sendPasswordReset(email: String) = emailAuth {
        auth.useAppLanguage()
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun sendVerificationEmail(): Boolean = try {
        auth.useAppLanguage()
        auth.requireUser().sendEmailVerification().await()
        true
    } catch (e: FirebaseException) {
        Log.w("ListAnythingAuth", "Could not send the confirmation email", e)
        false
    }

    override suspend fun refreshEmailVerification() {
        val user = auth.currentUser?.takeIf { !it.isEmailVerified } ?: return
        try {
            user.reload().await()
            // The security rules read "confirmed" from the token, so it is renewed once the address is.
            if (user.isEmailVerified) user.getIdToken(true).await()
        } catch (e: FirebaseException) {
            Log.w("ListAnythingAuth", "Could not check whether the email was confirmed", e)
        }
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

    override suspend fun deleteAccount(idToken: String): Boolean =
        deleteAccountAfter(GoogleAuthProvider.getCredential(idToken, null))

    override suspend fun deleteAccountWithPassword(password: String): Boolean {
        val email = auth.currentUser?.email ?: return false
        return deleteAccountAfter(EmailAuthProvider.getCredential(email, password))
    }

    private suspend fun deleteAccountAfter(proof: AuthCredential): Boolean = try {
        val user = auth.requireUser()
        // Firebase wants a fresh sign-in right before an account is deleted. First, so that being
        // offline, another Google account or a wrong password stops everything before any space changes.
        user.reauthenticate(proof).await()
        spaces.loadMySpaces().forEach { spaces.leaveForGood(it) }
        val tokens = db.collection(Collections.USERS).document(user.uid).collection(Collections.TOKENS).get().await()
        if (!tokens.isEmpty) {
            val batch = db.batch()
            tokens.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        user.delete().await()
        true
    } catch (e: FirebaseException) {
        Log.w("ListAnythingAuth", "Could not delete the account", e)
        false
    }

    private suspend fun emailAuth(block: suspend () -> Unit): EmailAuthResult = try {
        block()
        EmailAuthResult.Success
    } catch (e: FirebaseException) {
        Log.w("ListAnythingAuth", "Email sign-in did not work", e)
        when (e) {
            is FirebaseNetworkException -> EmailAuthResult.Failed
            is FirebaseTooManyRequestsException -> EmailAuthResult.TooManyTries
            is FirebaseAuthException -> emailAuthResultFor(e.errorCode)
            else -> EmailAuthResult.Failed
        }
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

/** What the person can do about a refused email sign-in, from Firebase's error code. */
internal fun emailAuthResultFor(errorCode: String): EmailAuthResult = when (errorCode) {
    // With email enumeration protection, which new Firebase projects have, an unknown address and
    // a wrong password both come back as an invalid credential.
    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> EmailAuthResult.WrongCredentials
    "ERROR_INVALID_EMAIL" -> EmailAuthResult.InvalidEmail
    "ERROR_EMAIL_ALREADY_IN_USE" -> EmailAuthResult.EmailInUse
    "ERROR_WEAK_PASSWORD" -> EmailAuthResult.WeakPassword
    "ERROR_TOO_MANY_REQUESTS" -> EmailAuthResult.TooManyTries
    "ERROR_OPERATION_NOT_ALLOWED" -> EmailAuthResult.NotEnabled
    else -> EmailAuthResult.Failed
}
