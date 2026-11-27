package com.kokouliss.listanything.core.domain.model

/** The signed-in person. */
data class User(
    val id: String,
    val name: String,
    val email: String?,
    /** Signed up with an email and password, so confirming it is them takes the password. */
    val usesPassword: Boolean = false,
    /** False until the person taps the link in the confirmation email; invites by email wait for it. */
    val emailVerified: Boolean = true,
    /** The profile photo the others see, or null while the person has none. */
    val photo: ImageData? = null,
)
