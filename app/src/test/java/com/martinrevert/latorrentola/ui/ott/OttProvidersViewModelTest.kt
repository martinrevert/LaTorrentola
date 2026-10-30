package com.martinrevert.latorrentola.ui.ott

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvider
import com.martinrevert.latorrentola.network.TmdbRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OttProvidersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tmdbRepository: TmdbRepository = mockk()
    private lateinit var viewModel: OttProvidersViewModel

    private val sampleMovieProviders = listOf(
        TmdbWatchProvider(providerId = 8, providerName = "Netflix", logoPath = "/netflix.jpg", displayPriority = 1),
        TmdbWatchProvider(providerId = 119, providerName = "Prime Video", logoPath = "/prime.jpg", displayPriority = 2)
    )

    private val sampleTvProviders = listOf(
        TmdbWatchProvider(providerId = 337, providerName = "Disney+", logoPath = "/disney.jpg", displayPriority = 1)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OttProvidersViewModel(tmdbRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadProviders with isTvMode false fetches movie watch providers`() = runTest {
        coEvery { tmdbRepository.getMovieWatchProviders() } returns sampleMovieProviders

        viewModel.loadProviders(isTvMode = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OttProvidersUiState.Success::class.java)
        val successState = state as OttProvidersUiState.Success
        assertThat(successState.providers).isEqualTo(sampleMovieProviders)
        coVerify(exactly = 1) { tmdbRepository.getMovieWatchProviders() }
    }

    @Test
    fun `loadProviders with isTvMode true fetches tv watch providers`() = runTest {
        coEvery { tmdbRepository.getTvWatchProviders() } returns sampleTvProviders

        viewModel.loadProviders(isTvMode = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OttProvidersUiState.Success::class.java)
        val successState = state as OttProvidersUiState.Success
        assertThat(successState.providers).isEqualTo(sampleTvProviders)
        coVerify(exactly = 1) { tmdbRepository.getTvWatchProviders() }
    }

    @Test
    fun `setting and clearing last clicked provider updates state`() {
        viewModel.setLastClickedProviderId(8)
        assertThat(viewModel.lastClickedProviderId.value).isEqualTo(8)

        viewModel.clearLastClickedProviderId()
        assertThat(viewModel.lastClickedProviderId.value).isNull()
    }

    @Test
    fun `loadProviders failure sets Error state with message`() = runTest {
        coEvery { tmdbRepository.getMovieWatchProviders() } throws RuntimeException("Network down")

        viewModel.loadProviders(isTvMode = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OttProvidersUiState.Error::class.java)
        val errorState = state as OttProvidersUiState.Error
        val errorMsg = errorState.message
        assertThat(errorMsg).isInstanceOf(com.martinrevert.latorrentola.utils.UiText.DynamicString::class.java)
        assertThat((errorMsg as com.martinrevert.latorrentola.utils.UiText.DynamicString).value).isEqualTo("Network down")
    }

    @Test
    fun `rapid successive loadProviders calls cancel previous job`() = runTest {
        coEvery { tmdbRepository.getMovieWatchProviders() } coAnswers {
            kotlinx.coroutines.delay(1000)
            sampleMovieProviders
        }
        coEvery { tmdbRepository.getTvWatchProviders() } returns sampleTvProviders

        viewModel.loadProviders(isTvMode = false)
        viewModel.loadProviders(isTvMode = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OttProvidersUiState.Success::class.java)
        val successState = state as OttProvidersUiState.Success
        assertThat(successState.providers).isEqualTo(sampleTvProviders)
    }
}
