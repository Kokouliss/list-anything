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
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleSignInResult.NoAccount
    } catch (e: GetCredentialException) {
        Log.w("ListAnythingAuth", "Google sign-in failed", e)
        GoogleSignInResult.Failed
    } catch (e: GoogleIdTokenParsingException) {
        Log.w("ListAnythingAuth", "Google returned an unreadable ID token", e)
        GoogleSignInResult.Failed
    }
}
