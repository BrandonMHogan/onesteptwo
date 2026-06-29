package com.onesteptwo.android

import android.app.Application
import com.clerk.api.Clerk
import timber.log.Timber

/**
 * Application subclass that initialises the Clerk Android SDK before any Activity or Service
 * is created (RESEARCH Pitfall 7). Calling Clerk.initialize here ensures that Clerk.auth,
 * Clerk.user, and Clerk.isSignedIn are all available when any component starts.
 *
 * Registered via android:name=".ClerkApp" in AndroidManifest.xml.
 */
class ClerkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Clerk.initialize(this, BuildConfig.CLERK_PUBLISHABLE_KEY)
        Timber.d("ClerkApp: Clerk.initialize called (key prefix=${BuildConfig.CLERK_PUBLISHABLE_KEY.take(8)}), isInitialized=${Clerk.isInitialized.value}")
    }
}
