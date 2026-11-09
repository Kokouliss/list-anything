package com.kokouliss.listanything.core.domain.model

/** A group of people who share lists. */
data class Space(
    val id: String,
    val name: String,
    val ownerId: String,
    /** Anyone who has this code can join. The owner can reset it. */
    val inviteCode: String,
    val members: List<Member>,
    /** Lower-case email addresses that were invited but have not joined yet. */
    val invitedEmails: List<String>,
)

data class Member(
    val id: String,
    val name: String,
)

/** Outcome of trying to join a space with an invite code. */
enum class JoinResult { Joined, AlreadyMember, InvalidCode, Unavailable }
