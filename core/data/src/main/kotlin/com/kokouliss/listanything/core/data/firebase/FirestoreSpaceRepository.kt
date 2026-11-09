package com.kokouliss.listanything.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.domain.model.InviteCode
import com.kokouliss.listanything.core.domain.model.JoinResult
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal class FirestoreSpaceRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : SpaceRepository {

    private val spaces get() = db.collection(Collections.SPACES)
    private val invites get() = db.collection(Collections.INVITES)

    override fun observeSpaces(): Flow<List<Space>> = auth.userFlow().flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            spaces.whereArrayContains(Fields.MEMBER_IDS, user.uid).snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { it.toSpace() }.sortedBy { it.name.lowercase() }
            }
        }
    }

    override fun observeInvitations(): Flow<List<Space>> = auth.userFlow().flatMapLatest { user ->
        val email = user?.verifiedEmail
        if (email == null) {
            flowOf(emptyList())
        } else {
            spaces.whereArrayContains(Fields.INVITED_EMAILS, email).snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { it.toSpace() }
            }
        }
    }

    override fun observeSpace(spaceId: String): Flow<Space?> =
        spaces.document(spaceId).snapshots()
            .map { it.toSpace()?.takeIf { space -> space.members.any { m -> m.id == auth.currentUser?.uid } } }
            // Being removed from a space ends the listener with "permission denied".
            .catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    override suspend fun createSpace(name: String) {
        val user = auth.requireUser()
        val space = spaces.document()
        val code = InviteCode.generate()
        db.batch()
            .set(
                space,
                mapOf(
                    Fields.NAME to name,
                    Fields.OWNER_ID to user.uid,
                    Fields.MEMBER_IDS to listOf(user.uid),
                    Fields.MEMBERS to mapOf(user.uid to mapOf(Fields.NAME to user.memberName)),
                    Fields.INVITE_CODE to code,
                    Fields.INVITED_EMAILS to emptyList<String>(),
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .set(invites.document(code), mapOf(Fields.SPACE_ID to space.id))
            .commit()
            .logFailure("create space")
    }

    override suspend fun renameSpace(spaceId: String, name: String) {
        spaces.document(spaceId).update(Fields.NAME, name).logFailure("rename space")
    }

    override suspend fun joinWithCode(code: String): JoinResult {
        val user = auth.requireUser()
        val normalized = InviteCode.normalize(code)
        if (!InviteCode.isValid(normalized)) return JoinResult.InvalidCode

        return withTimeoutOrNull(JOIN_TIMEOUT_MS) {
            try {
                // Filtered on the device: a second server-side filter would need a composite index.
                val mine = spaces.whereArrayContains(Fields.MEMBER_IDS, user.uid).get().await()
                if (mine.documents.any { it.getString(Fields.INVITE_CODE) == normalized }) {
                    return@withTimeoutOrNull JoinResult.AlreadyMember
                }
                val spaceId = invites.document(normalized).get().await().getString(Fields.SPACE_ID)
                    ?: return@withTimeoutOrNull JoinResult.InvalidCode
                // The rules only accept a new member entry that carries the space's current invite code.
                spaces.document(spaceId).update(
                    FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayUnion(user.uid),
                    FieldPath.of(Fields.MEMBERS, user.uid),
                    mapOf(Fields.NAME to user.memberName, Fields.INVITE_CODE to normalized),
                ).await()
                JoinResult.Joined
            } catch (e: FirebaseFirestoreException) {
                when (e.code) {
                    // The code was reset or the space was deleted after the invite was looked up.
                    Code.PERMISSION_DENIED, Code.NOT_FOUND -> JoinResult.InvalidCode
                    else -> JoinResult.Unavailable
                }
            }
        } ?: JoinResult.Unavailable
    }

    override suspend fun acceptInvitation(spaceId: String) {
        val user = auth.requireUser()
        // Invitations are only listed for a verified email, so there is nothing to accept without one.
        val email = user.verifiedEmail ?: return
        spaces.document(spaceId).update(
            FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayUnion(user.uid),
            FieldPath.of(Fields.MEMBERS, user.uid), mapOf(Fields.NAME to user.memberName),
            FieldPath.of(Fields.INVITED_EMAILS), FieldValue.arrayRemove(email),
        ).logFailure("accept invitation")
    }

    override suspend fun declineInvitation(spaceId: String) {
        val email = auth.requireUser().verifiedEmail ?: return
        spaces.document(spaceId)
            .update(Fields.INVITED_EMAILS, FieldValue.arrayRemove(email))
            .logFailure("decline invitation")
    }

    override suspend fun inviteEmail(spaceId: String, email: String) {
        spaces.document(spaceId)
            .update(Fields.INVITED_EMAILS, FieldValue.arrayUnion(email))
            .logFailure("invite email")
    }

    override suspend fun cancelEmailInvite(spaceId: String, email: String) {
        spaces.document(spaceId)
            .update(Fields.INVITED_EMAILS, FieldValue.arrayRemove(email))
            .logFailure("cancel email invite")
    }

    override suspend fun removeMember(space: Space, memberId: String) {
        val newCode = InviteCode.generate()
        db.batch()
            .update(
                spaces.document(space.id),
                FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayRemove(memberId),
                FieldPath.of(Fields.MEMBERS, memberId), FieldValue.delete(),
                FieldPath.of(Fields.INVITE_CODE), newCode,
            )
            .replaceInvite(space, newCode)
            .commit()
            .logFailure("remove member")
    }

    override suspend fun resetInviteCode(space: Space) {
        val newCode = InviteCode.generate()
        db.batch()
            .update(spaces.document(space.id), Fields.INVITE_CODE, newCode)
            .replaceInvite(space, newCode)
            .commit()
            .logFailure("reset invite code")
    }

    override suspend fun leaveSpace(spaceId: String) {
        val user = auth.requireUser()
        spaces.document(spaceId).update(
            FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayRemove(user.uid),
            FieldPath.of(Fields.MEMBERS, user.uid), FieldValue.delete(),
        ).logFailure("leave space")
    }

    override suspend fun deleteSpace(space: Space) {
        // shortcut: only the space and its invite are deleted. Its lists, items and photos become
        // unreachable (the rules need the space document) but stay stored. Add a Cloud Function or
        // an admin clean-up script when stored data starts to matter.
        db.batch()
            .delete(invites.document(space.inviteCode))
            .delete(spaces.document(space.id))
            .commit()
            .logFailure("delete space")
    }

    private fun WriteBatch.replaceInvite(space: Space, newCode: String): WriteBatch =
        delete(invites.document(space.inviteCode))
            .set(invites.document(newCode), mapOf(Fields.SPACE_ID to space.id))

    private companion object {
        const val JOIN_TIMEOUT_MS = 20_000L
    }
}
