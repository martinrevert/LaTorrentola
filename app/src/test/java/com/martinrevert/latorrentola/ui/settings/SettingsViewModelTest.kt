package com.martinrevert.latorrentola.ui.settings

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val firebaseMessagingInitializer: FirebaseMessagingInitializer = mockk(relaxed = true)
    private val userLibraryRepository: UserLibraryRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        every { preferenceManager.getFilteredLanguages() } returns "es"
        every { preferenceManager.getVoiceSystem() } returns true
        every { preferenceManager.getVoiceSummary() } returns true
        every { preferenceManager.getVoiceTranslation() } returns false
        every { preferenceManager.getVibrator() } returns true
        every { preferenceManager.isPushEnabled() } returns true
        every { preferenceManager.getTheme() } returns PreferenceManager.THEME_DARK
        every { preferenceManager.filteredLanguagesFlow } returns MutableStateFlow("es")
        every { authRepository.authStateFlow } returns MutableStateFlow(null)
        
        viewModel = SettingsViewModel(preferenceManager, firebaseMessagingInitializer, userLibraryRepository, authRepository)
    }

    @Test
    fun `initial state should reflect preference manager values`() {
        val state = viewModel.uiState.value
        assertThat(state.filteredLanguages).isEqualTo("es")
        assertThat(state.voiceSystem).isTrue()
        assertThat(state.theme).isEqualTo(PreferenceManager.THEME_DARK)
    }

    @Test
    fun `toggleVoiceSystem should update preference and state`() {
        viewModel.toggleVoiceSystem(false)
        verify { preferenceManager.setVoiceSystem(false) }
        assertThat(viewModel.uiState.value.voiceSystem).isFalse()
    }

    @Test
    fun `setTheme should update preference and state`() {
        viewModel.setTheme(PreferenceManager.THEME_LIGHT)
        verify { preferenceManager.setTheme(PreferenceManager.THEME_LIGHT) }
        assertThat(viewModel.uiState.value.theme).isEqualTo(PreferenceManager.THEME_LIGHT)
    }

    @Test
    fun `setFilteredLanguages should update preference, state and call repository after delay`() = runTest {
        viewModel.setFilteredLanguages("fr")
        
        verify { preferenceManager.setFilteredLanguages("fr") }
        assertThat(viewModel.uiState.value.filteredLanguages).isEqualTo("fr")
        
        testScheduler.advanceTimeBy(1100)
        coVerify { userLibraryRepository.saveFilteredLanguages("fr") }
    }

    @Test
    fun `togglePushEnabled should update preference, state and sync topic`() {
        viewModel.togglePushEnabled(false)
        
        verify { preferenceManager.setPushEnabled(false) }
        assertThat(viewModel.uiState.value.pushEnabled).isFalse()
        verify { firebaseMessagingInitializer.syncTopicSubscription(false) }
    }
}
