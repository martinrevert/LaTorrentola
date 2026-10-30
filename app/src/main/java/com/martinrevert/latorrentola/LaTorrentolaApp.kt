package com.martinrevert.latorrentola

import android.app.Application
import com.martinrevert.latorrentola.network.CrashlyticsManager
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.network.PublicTrackersRepository
import com.martinrevert.latorrentola.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application entry point that initializes Firebase services once per process. */
@HiltAndroidApp
class LaTorrentolaApp : Application() {

	/** Coordinates initial Firebase Messaging setup. */
	@Inject
	lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

	/** Coordinates Firebase Crashlytics logging and diagnostics. */
	@Inject
	lateinit var crashlyticsManager: CrashlyticsManager

	/** Coordinates live public BitTorrent trackers cache and background refreshes. */
	@Inject
	lateinit var publicTrackersRepository: PublicTrackersRepository

	/** Initializes the application and its Firebase integrations. */
	override fun onCreate() {
		super.onCreate()
		AppLogger.init(crashlyticsManager)
		crashlyticsManager.setCollectionEnabled(true)
		crashlyticsManager.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
		crashlyticsManager.setCustomKey("version_name", BuildConfig.VERSION_NAME)
		crashlyticsManager.setCustomKey("version_code", BuildConfig.VERSION_CODE.toString())
		firebaseMessagingInitializer.initialize()

		// Refresh public BitTorrent trackers in the background if cache is older than 24 hours
		CoroutineScope(Dispatchers.IO).launch {
			publicTrackersRepository.refreshTrackersIfStale()
		}
	}
}
