package com.onesteptwo.data

import com.onesteptwo.db.Consent_events
import com.onesteptwo.db.OneStepTwoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConsentEventsRepository(private val db: OneStepTwoDatabase) {

    suspend fun insert(
        id: String,
        clerkUserId: String,
        consentedAt: String,
        appVersion: String,
        consentTextVersion: String
    ) = withContext(Dispatchers.IO) {
        db.consentEventsQueries.insert(
            id = id,
            clerk_user_id = clerkUserId,
            consented_at = consentedAt,
            app_version = appVersion,
            consent_text_version = consentTextVersion
        )
    }

    suspend fun getById(id: String): Consent_events? = withContext(Dispatchers.IO) {
        db.consentEventsQueries.selectById(id).executeAsOneOrNull()
    }
}
