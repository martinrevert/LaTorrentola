package com.martinrevert.latorrentola

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.martinrevert.latorrentola.network.CrashlyticsManager
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import com.martinrevert.latorrentola.network.PublicTrackersRepository
import com.martinrevert.latorrentola.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application entry point that initializes Firebase services and Coil ImageLoader with SVG support. */
@HiltAndroidApp
class LaTorrentolaApp : Application(), SingletonImageLoader.Factory {

	/** Coordinates initial Firebase Messaging setup. */
	@Inject
	lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

	/** Coordinates Firebase Crashlytics logging and diagnostics. */
	@Inject
	lateinit var crashlyticsManager: CrashlyticsManager

	/** Coordinates live public BitTorrent trackers cache and background refreshes. */
	@Inject
	lateinit var publicTrackersRepository: PublicTrackersRepository

	override fun newImageLoader(context: PlatformContext): ImageLoader {
		return ImageLoader.Builder(context)
			.components {
				add(SvgDecoder.Factory())
			}
			.build()
	}

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
