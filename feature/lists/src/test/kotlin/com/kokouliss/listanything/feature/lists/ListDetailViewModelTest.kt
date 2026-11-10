package com.kokouliss.listanything.feature.lists

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kokouliss.listanything.core.domain.model.ImageData
import com.kokouliss.listanything.core.domain.model.ItemLocation
import com.kokouliss.listanything.core.domain.model.ListItem
import com.kokouliss.listanything.core.domain.model.ListRef
import com.kokouliss.listanything.core.domain.model.SharedList
import com.kokouliss.listanything.core.domain.model.User
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class ListDetailViewModelTest {

    private val lists = FakeListRepository()
    private val items = FakeItemRepository()
    private val viewModel by lazy { ListDetailViewModel(ListDetailKey("s1", "l1"), lists, items, FakeAuthRepository()) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `open items are shown before checked ones`() = runTest {
        items.items.value = listOf(item("milk", isChecked = true, createdAt = 1), item("bread", createdAt = 2))

        viewModel.uiState.test {
            val state = expectMostRecentItem() as ListDetailUiState.Ready
            assertThat(state.items.map { it.id }).containsExactly("bread", "milk").inOrder()
        }
    }

    @Test
    fun `a deleted list is reported as gone`() = runTest {
        viewModel.uiState.test {
            assertThat(expectMostRecentItem()).isInstanceOf(ListDetailUiState.Ready::class.java)

            lists.list.value = null

            assertThat(expectMostRecentItem()).isEqualTo(ListDetailUiState.Gone)
        }
    }

    @Test
    fun `blank items are not added and text is trimmed`() = runTest {
        viewModel.onAction(ListDetailAction.AddItem("   "))
        viewModel.onAction(ListDetailAction.AddItem("  Shampoo "))

        assertThat(items.added).containsExactly("Shampoo")
    }

    @Test
    fun `clearing removes only the checked items`() = runTest {
        items.items.value = listOf(item("milk", isChecked = true), item("bread"), item("eggs", isChecked = true))

        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.onAction(ListDetailAction.ClearChecked)
        }

        assertThat(items.deleted).containsExactly("milk", "eggs")
    }

    @Test
    fun `an unreadable photo is reported and the item stops showing progress`() = runTest {
        items.items.value = listOf(item("shampoo"))
        items.failPhoto = true

        viewModel.events.test {
            viewModel.onAction(ListDetailAction.SetPhoto("shampoo", "content://photo"))

            assertThat(awaitItem()).isEqualTo(ListDetailEvent.PhotoFailed)
        }
        viewModel.uiState.test {
            assertThat((expectMostRecentItem() as ListDetailUiState.Ready).photoBusyItemIds).isEmpty()
        }
    }

    @Test
    fun `opening a photo that is not on the device shows the viewer without an image`() = runTest {
        items.items.value = listOf(item("shampoo"))

        viewModel.uiState.test {
            viewModel.onAction(ListDetailAction.OpenPhoto("shampoo"))

            val state = expectMostRecentItem() as ListDetailUiState.Ready
            assertThat(state.photoViewer).isEqualTo(PhotoViewer("shampoo", image = null, isLoading = false))
        }
    }

    @Test
    fun `an item added to a section lands before the next heading`() = runTest {
        items.items.value = listOf(
            item("Bakery", createdAt = 10, isHeader = true),
            item("pie", createdAt = 20),
            item("Butcher", createdAt = 30, isHeader = true),
        )

        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.onAction(ListDetailAction.AddItem("bread", sectionId = "Bakery"))
            viewModel.onAction(ListDetailAction.AddItem("steak"))
            viewModel.onAction(ListDetailAction.AddSection("Pharmacy"))
        }

        assertThat(items.added).containsExactly("bread", "steak", "Pharmacy").inOrder()
        assertThat(items.addedOrders).containsExactly(25.0, null, null).inOrder()
        assertThat(items.addedHeaders).containsExactly(false, false, true).inOrder()
    }

    @Test
    fun `who takes what is hidden only from the person the wish list is for`() = runTest {
        viewModel.uiState.test {
            assertThat((expectMostRecentItem() as ListDetailUiState.Ready).hidesClaims).isFalse()

            lists.list.value = SharedList("l1", "s1", "Wishes", "🎁", giftForId = "u2", giftForName = "Maria")
            assertThat((expectMostRecentItem() as ListDetailUiState.Ready).hidesClaims).isFalse()

            lists.list.value = SharedList("l1", "s1", "Wishes", "🎁", giftForId = "u1", giftForName = "Panos")
            assertThat((expectMostRecentItem() as ListDetailUiState.Ready).hidesClaims).isTrue()
        }
    }

    private fun item(id: String, isChecked: Boolean = false, createdAt: Long = 0, isHeader: Boolean = false) =
        ListItem(id, id, isChecked, null, "Panos", null, null, createdAt, isHeader = isHeader)
}

private class FakeListRepository : ListRepository {
    val list = MutableStateFlow<SharedList?>(SharedList("l1", "s1", "Supermarket", "🛒"))

    override fun observeLists(spaceId: String): Flow<List<SharedList>> = flowOf(emptyList())
    override fun observeList(ref: ListRef): Flow<SharedList?> = list
    override suspend fun createList(spaceId: String, name: String, emoji: String) = Unit
    override suspend fun updateList(ref: ListRef, name: String, emoji: String) = Unit
    override suspend fun setGiftForMe(ref: ListRef, enabled: Boolean) = Unit
    override suspend fun deleteList(ref: ListRef) = Unit
}

private class FakeItemRepository : ItemRepository {
    val items = MutableStateFlow(emptyList<ListItem>())
    val added = mutableListOf<String>()
    val addedOrders = mutableListOf<Double?>()
    val addedHeaders = mutableListOf<Boolean>()
    val deleted = mutableListOf<String>()
    var failPhoto = false

    override fun observeItems(list: ListRef): Flow<List<ListItem>> = items

    override suspend fun addItem(list: ListRef, text: String, link: String?, isHeader: Boolean, order: Double?) {
        added += text
        addedOrders += order
        addedHeaders += isHeader
    }

    override suspend fun setPhoto(list: ListRef, itemId: String, photoUri: String) {
        if (failPhoto) throw IOException("cannot decode")
    }

    override suspend fun deleteItems(list: ListRef, itemIds: List<String>) {
        deleted += itemIds
    }

    override suspend fun loadPhoto(list: ListRef, itemId: String): ImageData? = null
    override suspend fun updateText(list: ListRef, itemId: String, text: String) = Unit
    override suspend fun setChecked(list: ListRef, itemId: String, checked: Boolean) = Unit
    override suspend fun setLocation(list: ListRef, itemId: String, location: ItemLocation?) = Unit
    override suspend fun setLink(list: ListRef, itemId: String, link: String?) = Unit
    override suspend fun setNote(list: ListRef, itemId: String, note: String?) = Unit
    override suspend fun setQuantity(list: ListRef, itemId: String, quantity: Int) = Unit
    override suspend fun setPrice(list: ListRef, itemId: String, priceCents: Long?) = Unit
    override suspend fun setDueDay(list: ListRef, itemId: String, dueDay: Long?) = Unit
    override suspend fun setClaimed(list: ListRef, itemId: String, claimed: Boolean) = Unit
    override suspend fun setOrder(list: ListRef, itemId: String, order: Double) = Unit
    override suspend fun loadDueItems(throughDay: Long): List<ListItem> = emptyList()
    override suspend fun removePhoto(list: ListRef, itemId: String) = Unit
}

private class FakeAuthRepository : AuthRepository {
    override val currentUser: Flow<User?> = flowOf(User("u1", "Panos", "panos@example.com"))
    override suspend fun signInWithGoogle(idToken: String) = true
    override suspend fun registerDevice() = Unit
    override suspend fun signOut() = Unit
}
