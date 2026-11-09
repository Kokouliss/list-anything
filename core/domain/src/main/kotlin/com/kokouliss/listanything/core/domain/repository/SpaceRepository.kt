package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Space
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    /** Spaces the signed-in user is a member of. */
    fun observeSpaces(): Flow<List<Space>>

    /** Spaces that invited the signed-in user's email address and are waiting for an answer. */
    fun observeInvitations(): Flow<List<Space>>

    /** Emits null once the space is deleted or the user is no longer a member. */
    fun observeSpace(spaceId: String): Flow<Space?>

    suspend fun createSpace(name: String)

    suspend fun renameSpace(spaceId: String, name: String)

    /** Needs a connection: a space cannot be read before its server accepts the new member. */
    suspend fun joinWithCode(code: String): JoinResult

    suspend fun acceptInvitation(spaceId: String)

    suspend fun declineInvitation(spaceId: String)

    /** [email] must already be normalized with `normalizeEmail`. */
    suspend fun inviteEmail(spaceId: String, email: String)

    suspend fun cancelEmailInvite(spaceId: String, email: String)

    /** Owner only. Also resets the invite code so the removed person cannot rejoin with the old one. */
    suspend fun removeMember(space: Space, memberId: String)

    /** Owner only. The old code stops working. */
    suspend fun resetInviteCode(space: Space)

    suspend fun leaveSpace(spaceId: String)

    /** Owner only. */
    suspend fun deleteSpace(space: Space)
}
