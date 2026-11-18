package com.kokouliss.listanything.core.data.firebase

import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.repository.EmailAuthResult
import org.junit.Test

class EmailAuthResultTest {

    @Test
    fun `an unknown address and a wrong password read the same`() {
        listOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND").forEach {
            assertThat(emailAuthResultFor(it)).isEqualTo(EmailAuthResult.WrongCredentials)
        }
    }

    @Test
    fun `each fixable code has its own answer`() {
        assertThat(emailAuthResultFor("ERROR_INVALID_EMAIL")).isEqualTo(EmailAuthResult.InvalidEmail)
        assertThat(emailAuthResultFor("ERROR_EMAIL_ALREADY_IN_USE")).isEqualTo(EmailAuthResult.EmailInUse)
        assertThat(emailAuthResultFor("ERROR_WEAK_PASSWORD")).isEqualTo(EmailAuthResult.WeakPassword)
        assertThat(emailAuthResultFor("ERROR_TOO_MANY_REQUESTS")).isEqualTo(EmailAuthResult.TooManyTries)
        assertThat(emailAuthResultFor("ERROR_OPERATION_NOT_ALLOWED")).isEqualTo(EmailAuthResult.NotEnabled)
    }

    @Test
    fun `anything else is a plain failure`() {
        assertThat(emailAuthResultFor("ERROR_USER_DISABLED")).isEqualTo(EmailAuthResult.Failed)
        assertThat(emailAuthResultFor("")).isEqualTo(EmailAuthResult.Failed)
    }
}
