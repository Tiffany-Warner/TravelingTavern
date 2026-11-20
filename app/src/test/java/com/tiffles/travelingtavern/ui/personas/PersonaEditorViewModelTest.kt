package com.tiffles.travelingtavern.ui.personas

import androidx.lifecycle.SavedStateHandle
import com.tiffles.travelingtavern.domain.FakePersonaRepository
import com.tiffles.travelingtavern.domain.model.PersonaChanges
import com.tiffles.travelingtavern.domain.persona
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import com.tiffles.travelingtavern.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonaEditorViewModelTest {

    private val repository = FakePersonaRepository()
    private val dispatcher = StandardTestDispatcher()
    private val mira = persona("1-Mira.png", name = "Mira", title = "Bard", description = "Sings {{char}} to sleep.")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.personas.value = listOf(mira)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(avatarId: String?) = PersonaEditorViewModel(
        SavedStateHandle(if (avatarId == null) emptyMap() else mapOf(Routes.FullScreen.PersonaEditor.ARG_AVATAR_ID to avatarId)),
        repository
    )

    private val PersonaEditorViewModel.editing get() = uiState.value as PersonaEditorUiState.Editing

    private fun TestScope.events(vm: PersonaEditorViewModel): Pair<MutableList<Unit>, MutableList<PersonaFailure>> {
        val done = mutableListOf<Unit>()
        val failed = mutableListOf<PersonaFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.done.collect { done += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.failed.collect { failed += it } }
        return done to failed
    }

    @Test
    fun `new persona needs a name before Save is enabled`() = runTest(dispatcher) {
        val vm = viewModel(null)
        assertTrue(vm.editing.isNew)
        assertFalse(vm.editing.canSave)

        vm.update(PersonaForm(name = "   "))
        assertFalse(vm.editing.canSave)

        vm.update(PersonaForm(name = "Ash"))
        assertTrue(vm.editing.canSave)
    }

    @Test
    fun `create sends every field and the picked picture`() = runTest(dispatcher) {
        val vm = viewModel(null)
        val (done, _) = events(vm)

        vm.update(PersonaForm(name = " Ash ", title = " Ranger ", description = "Quiet."))
        vm.setPicture("content://media/picker/1")
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf(PersonaChanges("Ash", "Ranger", "Quiet.", "content://media/picker/1")), repository.created)
        assertEquals(1, done.size)
    }

    @Test
    fun `edit loads the persona and Save waits for a change`() = runTest(dispatcher) {
        val vm = viewModel("1-Mira.png")
        assertEquals(PersonaEditorUiState.Loading, vm.uiState.value)
        runCurrent()

        assertEquals(PersonaForm("Mira", "Bard", "Sings {{char}} to sleep."), vm.editing.form)
        assertFalse(vm.editing.isNew)
        assertFalse(vm.editing.canSave)

        vm.setPicture("content://media/picker/2")
        assertTrue(vm.editing.canSave)
    }

    @Test
    fun `edit sends only the changed fields`() = runTest(dispatcher) {
        val vm = viewModel("1-Mira.png")
        val (done, _) = events(vm)
        runCurrent()

        vm.update(vm.editing.form.copy(description = "Hums {{user}} to sleep."))
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("1-Mira.png" to PersonaChanges(description = "Hums {{user}} to sleep.")), repository.saved)
        assertEquals(1, done.size)
    }

    @Test
    fun `a missing persona is NotFound`() = runTest(dispatcher) {
        val vm = viewModel("gone.png")
        runCurrent()
        assertEquals(PersonaEditorUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `a failed save keeps the form and reports why`() = runTest(dispatcher) {
        val vm = viewModel(null)
        val (done, failed) = events(vm)
        repository.failure = PersonaFailure.PictureUnreadable

        vm.update(PersonaForm(name = "Ash"))
        vm.save()
        advanceUntilIdle()

        assertTrue(done.isEmpty())
        assertEquals(listOf(PersonaFailure.PictureUnreadable), failed)
        assertEquals("Ash", vm.editing.form.name)
        assertFalse(vm.editing.isWorking)
    }

    @Test
    fun `description tokens are counted after a pause`() = runTest(dispatcher) {
        val vm = viewModel(null)
        runCurrent()

        vm.update(PersonaForm(name = "Ash", description = "Quiet ranger"))
        advanceTimeBy(600)
        runCurrent()

        assertEquals("Quiet ranger", repository.countedTexts.last())
        assertEquals(7, vm.editing.tokenCount)
    }
}
