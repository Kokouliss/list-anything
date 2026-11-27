package com.kokouliss.listanything.core.data.firebase

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kokouliss.listanything.core.data.di.IoDispatcher
import com.kokouliss.listanything.core.domain.model.ChatLook
import com.kokouliss.listanything.core.domain.model.ChatMessage
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.MAX_CHAT_EMOJI_LENGTH
import com.kokouliss.listanything.core.domain.model.MAX_MESSAGE_LENGTH
import com.kokouliss.listanything.core.domain.model.MessageKind
import com.kokouliss.listanything.core.domain.repository.ChatRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * One document per message, written by the device that sends it. A photo or a recording sits in a
 * document of its own under the message, like the photo of an item, so reading the chat downloads
 * only the small pictures. notifyChatMessage in firebase/functions tells the others.
 */
internal class FirestoreChatRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val photoCompressor: PhotoCompressor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChatRepository {

    private fun space(spaceId: String): DocumentReference = db.collection(Collections.SPACES).document(spaceId)

    private fun messages(spaceId: String): CollectionReference = space(spaceId).collection(Collections.MESSAGES)

    private fun DocumentReference.media(): DocumentReference = collection(Collections.MEDIA).document(FULL_PHOTO_ID)

    override fun observeMessages(spaceId: String, limit: Long): Flow<List<ChatMessage>> =
        // With metadata changes, so a message says when it has reached the cloud.
        messages(spaceId).orderBy(Fields.AT, Query.Direction.DESCENDING).limit(limit).snapshots(MetadataChanges.INCLUDE)
            .map { snapshot -> snapshot.documents.mapNotNull { it.toChatMessage() } }
            .distinctUntilChanged()

    /** What every message says: who sent it, in their own name, at the time the server reads it. */
    private fun message(vararg more: Pair<String, Any>): Map<String, Any> {
        val user = auth.requireUser()
        return mapOf(Fields.BY_ID to user.uid, Fields.BY_NAME to user.memberName, Fields.AT to FieldValue.serverTimestamp()) + more
    }

    override suspend fun sendText(spaceId: String, text: String) {
        val trimmed = text.trim().take(MAX_MESSAGE_LENGTH)
        if (trimmed.isEmpty()) return
        messages(spaceId).document().set(message(Fields.TEXT to trimmed)).logFailure("send message")
    }

    override suspend fun sendPhoto(spaceId: String, photoUri: String) {
        val photo = withContext(ioDispatcher) { photoCompressor.compress(Uri.parse(photoUri), CHAT_THUMBNAIL_PX) }
        val message = messages(spaceId).document()
        // shortcut: in Firestore like the photos of items, so the chat needs no Storage bucket. See
        // FirestoreItemRepository.setPhoto for when to move.
        db.batch()
            .set(message.media(), mapOf(Fields.DATA to Blob.fromBytes(photo.full)))
            .set(message, message(Fields.KIND to MessageKind.PHOTO.key, Fields.THUMBNAIL to Blob.fromBytes(photo.thumbnail)))
            .commit()
            .logFailure("send photo")
    }

    override suspend fun sendVoice(spaceId: String, audioPath: String, seconds: Int) {
        val audio = withContext(ioDispatcher) { File(audioPath).readBytes() }
        require(audio.size in 1..MAX_VOICE_BYTES) { "Voice message of ${audio.size} bytes" }
        val message = messages(spaceId).document()
        db.batch()
            .set(message.media(), mapOf(Fields.DATA to Blob.fromBytes(audio)))
            .set(message, message(Fields.KIND to MessageKind.VOICE.key, Fields.SECONDS to seconds))
            .commit()
            .logFailure("send voice message")
    }

    override suspend fun loadPhoto(spaceId: String, messageId: String): ImageData? = loadMedia(spaceId, messageId)?.let(::ImageData)

    override suspend fun loadVoice(spaceId: String, messageId: String): ByteArray? = loadMedia(spaceId, messageId)

    private suspend fun loadMedia(spaceId: String, messageId: String): ByteArray? =
        try {
            messages(spaceId).document(messageId).media().get().await().getBlob(Fields.DATA)?.toBytes()
        } catch (e: FirebaseFirestoreException) {
            // Offline and not in the local cache yet.
            null
        }

    override suspend fun deleteMessage(spaceId: String, messageId: String) {
        val message = messages(spaceId).document(messageId)
        db.batch().delete(message.media()).delete(message).commit().logFailure("delete message")
    }

    override suspend fun setLook(spaceId: String, look: ChatLook) {
        val chat = buildMap<String, Any> {
            // Stored as a whole number, like the pen colours of a drawing.
            look.color?.let { put(Fields.COLOR, it.toLong() and 0xFFFFFFFFL) }
            put(Fields.EMOJI, look.emoji.take(MAX_CHAT_EMOJI_LENGTH))
        }
        space(spaceId).update(Fields.CHAT, chat).logFailure("change the chat's look")
    }

    override suspend fun setMutedUntil(spaceId: String, untilMillis: Long?) {
        val uid = auth.requireUser().uid
        space(spaceId)
            .update(FieldPath.of(Fields.CHAT_MUTED, uid), untilMillis ?: FieldValue.delete())
            .logFailure("mute the chat")
    }

    private companion object {
        /** The longer side of the picture a photo message shows in its bubble. About 30 KB. */
        const val CHAT_THUMBNAIL_PX = 400
    }
}
