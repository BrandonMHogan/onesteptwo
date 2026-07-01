package com.onesteptwo.android

import android.content.Context
import com.onesteptwo.android.BuildConfig
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.api.createChildrenApiClient
import com.onesteptwo.auth.ClerkAuthRepository
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.ConsentEventsRepository
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.DatabaseDriverFactory
import com.onesteptwo.db.OneStepTwoDatabase

/**
 * Hand-rolled composition root (no DI framework) — built once per process and held by
 * [ClerkApp]. Screens reach repositories via `(application as ClerkApp).container`.
 */
class AppContainer(context: Context) {
    private val database: OneStepTwoDatabase by lazy {
        OneStepTwoDatabase(DatabaseDriverFactory(context).createDriver())
    }

    val childrenRepository: ChildrenRepository by lazy { ChildrenRepository(database) }
    val pottyEventsRepository: PottyEventsRepository by lazy { PottyEventsRepository(database) }
    val consentEventsRepository: ConsentEventsRepository by lazy { ConsentEventsRepository(database) }

    val childrenApiClient: ChildrenApiClient by lazy {
        createChildrenApiClient(
            authRepository = ClerkAuthRepository(),
            baseUrl = BuildConfig.API_BASE_URL,
            isDebug = BuildConfig.DEBUG
        )
    }
}
