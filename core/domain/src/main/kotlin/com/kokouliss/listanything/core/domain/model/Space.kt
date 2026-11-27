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
    /** The colour and quick emoji of the space's chat, the same for every member. */
    val chatLook: ChatLook = ChatLook(),
    /** Until when each member, by id, hears nothing from the chat. Missing or past: not muted. */
    val chatMutedUntil: Map<String, Long> = emptyMap(),
)

data class Member(
    val id: String,
    val name: String,
    /** The small picture this person chose for themselves, or null for the first letter of the name. */
    val photo: ImageData? = null,
)

/** The profile photo of each member who has one, by id. */
fun List<Member>.photos(): Map<String, ImageData> = mapNotNull { member -> member.photo?.let { member.id to it } }.toMap()

/** Outcome of trying to join a space with an invite code. */
enum class JoinResult { Joined, AlreadyMember, InvalidCode, Unavailable }
