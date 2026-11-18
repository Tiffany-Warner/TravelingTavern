package com.tiffles.travelingtavern.ui.lorebooks

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeLorebookRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.lorebook
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.usecase.CreateLorebookUseCase
import com.tiffles.travelingtavern.domain.usecase.FilterLorebooksUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebooksUseCase
import com.tiffles.travelingtavern.domain.usecase.LorebookNameCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LorebooksViewModelTest {

    private val lorebooks = FakeLorebookRepository()
    private val characters = FakeCharacterRepository()
    private lateinit var viewModel: LorebooksViewModel

    private val seraphina = character("Seraphina")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LorebooksViewModel(
            GetLorebooksUseCase(lorebooks, characters),
            FilterLorebooksUseCase(),
            CreateLorebookUseCase(lorebooks),
            lorebooks
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun library() {
        characters.characters.value = listOf(seraphina)
        lorebooks.lorebooks.value = listOf(
            lorebook("Eldoria", characterIds = listOf(seraphina.id)),
            lorebook("Rules", global = true),
            lorebook("Old notes")
        )
    }

    private val success get() = viewModel.uiState.value as LorebooksUiState.Success

    @Test
    fun `loading, then empty, and a page error wins`() = runTest {
        collectState()
        assertEquals(LorebooksUiState.Loading, viewModel.uiState.value)

        characters.characters.value = emptyList()
        lorebooks.lorebooks.value = emptyList()
        assertEquals(LorebooksUiState.Empty, viewModel.uiState.value)

        lorebooks.pageState.value = PageState.Error(PageError.Unreachable)
        assertEquals(LorebooksUiState.Error(PageError.Unreachable), viewModel.uiState.value)
    }

    @Test
    fun `chips and search filter the grid, and clear filters resets both`() = runTest {
        collectState()
        library()
        assertEquals(3, success.cards.size)
        assertEquals(listOf(seraphina), success.filterCharacters)

        viewModel.setFilter(LorebookFilter.Unused)
        assertEquals(listOf("Old notes"), success.cards.map { it.lorebook.name })

        viewModel.setFilter(LorebookFilter.All)
        viewModel.setQuery("sera")
        assertEquals(listOf("Eldoria"), success.cards.map { it.lorebook.name })

        viewModel.clearFilters()
        assertEquals(3, success.cards.size)
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `character filter shows the character, and clears itself once they have no lorebook`() = runTest {
        collectState()
        library()

        viewModel.setFilter(LorebookFilter.Character(seraphina.id))
        assertEquals(seraphina, success.selectedCharacter)
        assertEquals(listOf("Eldoria"), success.cards.map { it.lorebook.name })

        lorebooks.lorebooks.value = listOf(lorebook("Rules", global = true))
        assertEquals(LorebookFilter.All, success.filter)
        assertNull(success.selectedCharacter)
    }

    @Test
    fun `create checks the name as it is typed`() = runTest {
        collectState()
        library()
        viewModel.startCreate()
        assertFalse(viewModel.createState.value.canCreate)

        viewModel.setCreateName("eldoria")
        assertEquals(LorebookNameCheck.Taken, viewModel.createState.value.check)
        assertFalse(viewModel.createState.value.canCreate)

        viewModel.setCreateName("Kingdoms")
        assertTrue(viewModel.createState.value.canCreate)
    }

    @Test
    fun `a successful create opens the new lorebook`() = runTest {
        collectState()
        library()
        val opened = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.openLorebook.collect { opened += it } }

        viewModel.setCreateName("Kingdoms")
        viewModel.create()

        assertEquals(listOf("Kingdoms"), lorebooks.created)
        assertEquals(listOf("Kingdoms"), opened)
        assertEquals(CreateLorebookState(), viewModel.createState.value)
    }

    @Test
    fun `SillyTavern refusing the name shows Taken, other failures show failed`() = runTest {
        collectState()
        library()

        lorebooks.failure = LorebookFailure.NameTaken
        viewModel.setCreateName("Kingdoms")
        viewModel.create()
        assertEquals(LorebookNameCheck.Taken, viewModel.createState.value.check)

        lorebooks.failure = LorebookFailure.Failed
        viewModel.setCreateName("Kingdoms 2")
        viewModel.create()
        assertTrue(viewModel.createState.value.failed)
        assertFalse(viewModel.createState.value.isCreating)
    }

    @Test
    fun `refresh reports progress and ignores repeats`() = runTest {
        viewModel.refresh()
        assertEquals(1, lorebooks.refreshCalls)
        assertFalse(viewModel.isRefreshing.value)
    }
}
