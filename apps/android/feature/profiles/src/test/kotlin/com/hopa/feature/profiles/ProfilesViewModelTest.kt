package com.hopa.feature.profiles

import com.hopa.stores.AppSettings
import com.hopa.stores.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshReadsTheStoredProfile() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(AppSettings(profile = "power"))
        val viewModel = ProfilesViewModel(deps(settings))

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("power", viewModel.state.value.selected)
    }

    @Test
    fun selectShowsTheChoiceBeforeItIsStored() {
        val viewModel = ProfilesViewModel(deps(FakeSettingsRepository()))

        viewModel.select("easy")

        assertEquals("easy", viewModel.state.value.selected)
    }

    @Test
    fun saveWritesTheProfileThroughTheStore() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(AppSettings(profile = "everyday"))
        val viewModel = ProfilesViewModel(deps(settings))

        viewModel.save("kid")
        advanceUntilIdle()

        assertEquals("kid", settings.current.profile)
    }

    private fun deps(settings: SettingsRepository) = object : ProfilesDependencies {
        override val settings: SettingsRepository = settings
    }
}
