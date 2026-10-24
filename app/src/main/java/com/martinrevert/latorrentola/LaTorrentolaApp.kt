package com.martinrevert.latorrentola

import android.app.Application
import com.martinrevert.latorrentola.network.CrashlyticsManager
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
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

	/** Initializes the application and its Firebase integrations. */
	override fun onCreate() {
		super.onCreate()
		crashlyticsManager.setCollectionEnabled(true)
		crashlyticsManager.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
		crashlyticsManager.setCustomKey("version_name", BuildConfig.VERSION_NAME)
		crashlyticsManager.setCustomKey("version_code", BuildConfig.VERSION_CODE.toString())
		firebaseMessagingInitializer.initialize()
	}
}
