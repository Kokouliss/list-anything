package com.kokouliss.listanything.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoogleSignInTest {

    @Test
    fun `a refusal after the account is chosen is not treated as the user backing out`() {
        val refusal = GetCredentialCancellationException("[16] Account reauth failed.")

        assertThat(refusal.toSignInResult()).isEqualTo(GoogleSignInResult.Rejected)
    }

    @Test
    fun `closing the account sheet stays a silent cancel`() {
        val cancel = GetCredentialCancellationException("activity is cancelled by the user.")

        assertThat(cancel.toSignInResult()).isEqualTo(GoogleSignInResult.Cancelled)
    }
}
