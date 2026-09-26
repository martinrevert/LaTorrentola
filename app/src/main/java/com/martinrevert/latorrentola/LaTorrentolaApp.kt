package com.martinrevert.latorrentola

import android.app.Application
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application entry point that initializes Firebase messaging once per process. */
@HiltAndroidApp
class LaTorrentolaApp : Application() {

	/** Coordinates initial Firebase Messaging setup. */
	@Inject
	lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

	/** Initializes the application and its Firebase messaging integration. */
	override fun onCreate() {
		super.onCreate()
		firebaseMessagingInitializer.initialize()
	}
}
