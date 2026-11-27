package com.kokouliss.listanything.core.domain.repository

import com.kokouliss.listanything.core.domain.model.ChatLook
import com.kokouliss.listanything.core.domain.model.ChatMessage
import com.kokouliss.listanything.core.domain.model.ImageData
import kotlinx.coroutines.flow.Flow

/**
 * The chat of a space: every member reads and writes it. Messages are applied on the device at
 * once and synced when a connection is available, like items.
 */
interface ChatRepository {
    /** The newest [limit] messages, newest first. */
    fun observeMessages(spaceId: String, limit: Long): Flow<List<ChatMessage>>

    suspend fun sendText(spaceId: String, text: String)

    /** Shrinks the picture at [photoUri] and sends it. */
    suspend fun sendPhoto(spaceId: String, photoUri: String)

    /** Sends the recording in the file at [audioPath]. */
    suspend fun sendVoice(spaceId: String, audioPath: String, seconds: Int)

    /** The full-size photo of a message, or null when it has not synced to this device yet. */
    suspend fun loadPhoto(spaceId: String, messageId: String): ImageData?

    /** The recording of a voice message, or null when it has not synced to this device yet. */
    suspend fun loadVoice(spaceId: String, messageId: String): ByteArray?

    /** Takes back a message of the signed-in user, with its photo or recording. */
    suspend fun deleteMessage(spaceId: String, messageId: String)

    /** Changes the chat's colour and quick emoji for everyone in the space. */
    suspend fun setLook(spaceId: String, look: ChatLook)

    /** The signed-in user hears nothing from this chat until [untilMillis]; null turns it back on. */
    suspend fun setMutedUntil(spaceId: String, untilMillis: Long?)

    companion object {
        /** How many messages a visit reads first, and how many more each time the top is reached. */
        const val MESSAGES_PAGE = 50L
    }
}
