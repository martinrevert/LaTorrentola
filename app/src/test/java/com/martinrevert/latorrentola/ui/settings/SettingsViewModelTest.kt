package com.martinrevert.latorrentola.ui.settings

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.network.AuthRepository
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.rules.MainDispatcherRule
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.OpenSubtitlesCredentialStore
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
/** Verifies local and remotely synchronized settings behavior. */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val openSubtitlesCredentialStore: OpenSubtitlesCredentialStore = mockk(relaxed = true)
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
        every { preferenceManager.getMinimumRating() } returns PreferenceManager.DEFAULT_MINIMUM_RATING
        every { preferenceManager.getTorrentHandlingMode() } returns TorrentHandlingMode.EXTERNAL_CLIENT
        every { preferenceManager.filteredLanguagesFlow } returns MutableStateFlow("es")
        every { preferenceManager.minimumRatingFlow } returns MutableStateFlow(PreferenceManager.DEFAULT_MINIMUM_RATING)
        every { authRepository.authStateFlow } returns MutableStateFlow(null)
        
        viewModel = SettingsViewModel(
            preferenceManager,
            openSubtitlesCredentialStore,
            firebaseMessagingInitializer,
            userLibraryRepository,
            authRepository
        )
    }

    @Test
    fun `initial state should reflect preference manager values`() {
        val state = viewModel.uiState.value
        assertThat(state.filteredLanguages).isEqualTo("es")
        assertThat(state.voiceSystem).isTrue()
        assertThat(state.theme).isEqualTo(PreferenceManager.THEME_DARK)
        assertThat(state.minimumRating).isEqualTo(PreferenceManager.DEFAULT_MINIMUM_RATING)
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

    /** Checks that selecting an in-app mode updates persisted and displayed state. */
    @Test
    fun `setTorrentHandlingMode should update preference and state`() {
        viewModel.setTorrentHandlingMode(TorrentHandlingMode.LOCAL_PLAYBACK)

        verify { preferenceManager.setTorrentHandlingMode(TorrentHandlingMode.LOCAL_PLAYBACK) }
        assertThat(viewModel.uiState.value.torrentHandlingMode)
            .isEqualTo(TorrentHandlingMode.LOCAL_PLAYBACK)
    }

    /** Checks that subtitle account credentials are stored and cleared through the secure store. */
    @Test
    fun `saving and clearing subtitle credentials updates local settings state`() {
        viewModel.saveOpenSubtitlesCredentials("user", "password")

        verify { openSubtitlesCredentialStore.save(match { it.username == "user" && it.password == "password" }) }
        assertThat(viewModel.uiState.value.openSubtitlesUsername).isEqualTo("user")
        assertThat(viewModel.uiState.value.openSubtitlesCredentialsConfigured).isTrue()

        viewModel.clearOpenSubtitlesCredentials()

        verify { openSubtitlesCredentialStore.clear() }
        assertThat(viewModel.uiState.value.openSubtitlesUsername).isEmpty()
        assertThat(viewModel.uiState.value.openSubtitlesCredentialsConfigured).isFalse()
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

    @Test
    fun `setMinimumRating should update preference and persist to Firestore after debounce`() = runTest {
        viewModel.setMinimumRating(8f)

        verify { preferenceManager.setMinimumRating(8f) }
        assertThat(viewModel.uiState.value.minimumRating).isEqualTo(8f)

        testScheduler.advanceTimeBy(1100)
        coVerify { userLibraryRepository.saveMinimumRating(8f) }
    }
}
