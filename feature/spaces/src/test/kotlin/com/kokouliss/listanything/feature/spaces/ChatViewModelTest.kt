package com.kokouliss.listanything.feature.spaces

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.ChatLook
import com.kokouliss.listanything.core.domain.model.ChatMessage
import com.kokouliss.listanything.core.domain.model.ChatMute
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.MUTED_FOREVER
import com.kokouliss.listanything.core.domain.model.Member
import com.kokouliss.listanything.core.domain.model.Space
import com.kokouliss.listanything.core.domain.repository.ChatRepository
import com.kokouliss.listanything.core.domain.repository.ChatRepository.Companion.MESSAGES_PAGE
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class ChatViewModelTest {

    private val chat = FakeChatRepository()
    private val spaces = FakeSpaceRepository()
    private val viewModel by lazy { ChatViewModel(SpaceDetailKey("s1"), chat, spaces, FakeAuthRepository()) }
    private val photo = ImageData(byteArrayOf(1, 2, 3))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun space(look: ChatLook = ChatLook(), muted: Map<String, Long> = emptyMap()) = Space(
        id = "s1",
        name = "Family",
        ownerId = "u1",
        inviteCode = "ABCD2345",
        members = listOf(Member("u1", "Panos"), Member("u2", "Maria", photo = photo)),
        invitedEmails = emptyList(),
        chatLook = look,
        chatMutedUntil = muted,
    )

    @Test
    fun `the chat shows its messages under current names, with the look and the faces of the space`() = runTest {
        spaces.space.value = space(look = ChatLook(color = 0x7A4B77, emoji = "❤️"))
        chat.messages.value = listOf(ChatMessage("2", "u2", "Old name", "Hi", 2), ChatMessage("1", "u1", "Panos", "Hello", 1))

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.messages?.map { it.byName }).containsExactly("Maria", "Panos").inOrder()
            assertThat(state.currentUserId).isEqualTo("u1")
            assertThat(state.look).isEqualTo(ChatLook(color = 0x7A4B77, emoji = "❤️"))
            assertThat(state.photos).containsExactly("u2", photo)
            assertThat(state.mutedUntil).isNull()
            assertThat(state.canLoadMore).isFalse()

            // The quick button sends the emoji the space chose.
            viewModel.onAction(ChatAction.SendEmoji)
            assertThat(chat.sent).containsExactly("❤️")
        }
    }

    @Test
    fun `a full page offers older messages, and asking reads a longer one`() = runTest {
        spaces.space.value = space()
        chat.messages.value = List(120) { ChatMessage("$it", "u2", "Maria", "Hi", 1_000L - it) }

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().messages).hasSize(MESSAGES_PAGE.toInt())
            viewModel.onAction(ChatAction.LoadMore)
            val more = expectMostRecentItem()
            assertThat(more.messages).hasSize(2 * MESSAGES_PAGE.toInt())
            assertThat(more.canLoadMore).isTrue()
            viewModel.onAction(ChatAction.LoadMore)
            assertThat(expectMostRecentItem().canLoadMore).isFalse()
        }
    }

    @Test
    fun `muting stores until when, the bell shows it, and turning it back on clears it`() = runTest {
        spaces.space.value = space(muted = mapOf("u1" to MUTED_FOREVER, "u2" to 1))

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().mutedUntil).isEqualTo(MUTED_FOREVER)

            val before = System.currentTimeMillis()
            viewModel.onAction(ChatAction.Mute(ChatMute.HOUR))
            assertThat(chat.mutedUntil).isAtLeast(before + 3_600_000)
            viewModel.onAction(ChatAction.Mute(null))
            assertThat(chat.mutedUntil).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a photo that cannot be sent says so once, and a refused chat is an empty chat`() = runTest {
        spaces.space.value = space()
        chat.failMessages = true
        chat.failPhoto = true

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().messages).isEmpty()
            viewModel.onAction(ChatAction.SendPhoto("content://photo"))
            val failed = expectMostRecentItem().overlays
            assertThat(failed.sendingPhoto).isFalse()
            assertThat(failed.notice).isEqualTo(R.string.feature_spaces_chat_photo_failed)
            viewModel.onAction(ChatAction.NoticeShown)
            assertThat(expectMostRecentItem().overlays.notice).isNull()
        }
    }
}

private class FakeChatRepository : ChatRepository {
    val messages = MutableStateFlow(emptyList<ChatMessage>())
    var failMessages = false
    var failPhoto = false
    val sent = mutableListOf<String>()
    var mutedUntil: Long? = null

    override fun observeMessages(spaceId: String, limit: Long): Flow<List<ChatMessage>> = messages.map {
        if (failMessages) throw IOException("permission denied")
        it.take(limit.toInt())
    }

    override suspend fun sendText(spaceId: String, text: String) {
        sent += text
    }

    override suspend fun sendPhoto(spaceId: String, photoUri: String) {
        if (failPhoto) throw IOException("unreadable")
    }

    override suspend fun sendVoice(spaceId: String, audioPath: String, seconds: Int) = Unit
    override suspend fun loadPhoto(spaceId: String, messageId: String): ImageData? = null
    override suspend fun loadVoice(spaceId: String, messageId: String): ByteArray? = null
    override suspend fun deleteMessage(spaceId: String, messageId: String) = Unit
    override suspend fun setLook(spaceId: String, look: ChatLook) = Unit
    override suspend fun setMutedUntil(spaceId: String, untilMillis: Long?) {
        mutedUntil = untilMillis
    }
}
