package com.kokouliss.listanything.core.domain.model

/** The signed-in person. */
data class User(
    val id: String,
    val name: String,
    val email: String?,
)
