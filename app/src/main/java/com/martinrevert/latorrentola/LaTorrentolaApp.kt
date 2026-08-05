package com.martinrevert.latorrentola

import android.app.Application
import com.martinrevert.latorrentola.network.FirebaseMessagingInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LaTorrentolaApp : Application() {

	@Inject
	lateinit var firebaseMessagingInitializer: FirebaseMessagingInitializer

	override fun onCreate() {
		super.onCreate()
		firebaseMessagingInitializer.initialize()
	}
}
