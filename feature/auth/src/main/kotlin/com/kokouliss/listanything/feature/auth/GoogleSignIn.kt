package com.kokouliss.listanything.feature.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

internal sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object NoAccount : GoogleSignInResult

    /** Google refused after the account was chosen: usually an unregistered SHA-1, sometimes no network. */
    data object Rejected : GoogleSignInResult
    data object NotConfigured : GoogleSignInResult
    data object Failed : GoogleSignInResult
}

/**
 * Shows the system "Sign in with Google" sheet and returns the ID token of the chosen account.
 * The token is handed straight to Firebase Auth; nothing is stored here.
 */
internal suspend fun requestGoogleIdToken(activity: Activity): GoogleSignInResult {
    // The google-services plugin generates this string in the app module from google-services.json,
    // so it only exists once Google sign-in is enabled in the Firebase project. Looked up by name
    // because the project must still compile before that file is added.
    @SuppressLint("DiscouragedApi")
    val clientIdRes = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    if (clientIdRes == 0) return GoogleSignInResult.NotConfigured

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(activity.getString(clientIdRes)).build())
        .build()
    return try {
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleSignInResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleSignInResult.Failed
        }
    } catch (e: GetCredentialException) {
        Log.w("ListAnythingAuth", "Google sign-in did not finish", e)
        e.toSignInResult()
    } catch (e: GoogleIdTokenParsingException) {
        Log.w("ListAnythingAuth", "Google returned an unreadable ID token", e)
        GoogleSignInResult.Failed
    }
}

/**
 * Google only notices a signing key (SHA-1) that is missing from the Firebase project after the
 * account is chosen, and reports it as a "cancel" with status 16 or as error 28444. Neither is the
 * user backing out, so neither may end in silence.
 */
internal fun GetCredentialException.toSignInResult(): GoogleSignInResult {
    val text = message.orEmpty()
    return when {
        "[16]" in text -> GoogleSignInResult.Rejected
        "[28444]" in text -> GoogleSignInResult.NotConfigured
        this is GetCredentialCancellationException -> GoogleSignInResult.Cancelled
        this is NoCredentialException -> GoogleSignInResult.NoAccount
        else -> GoogleSignInResult.Failed
    }
}
