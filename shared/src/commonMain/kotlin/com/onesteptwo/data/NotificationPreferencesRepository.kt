package com.onesteptwo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import com.onesteptwo.db.Notification_preferences
import com.onesteptwo.db.OneStepTwoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotificationPreferencesRepository(private val db: OneStepTwoDatabase) {

    /** REQ-023 opt-in default: a child with no local row yet reads as enabled. */
    fun observe(childId: String): Flow<Boolean> =
        db.notificationPreferencesQueries.selectByChildId(childId)
            .asFlow()
            .mapToOneOrDefault(Notification_preferences(childId, 1L), Dispatchers.IO)
            .map { it.enabled == 1L }

    suspend fun setLocal(childId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.notificationPreferencesQueries.upsert(childId, if (enabled) 1L else 0L)
    }

    suspend fun deleteLocal(childId: String) = withContext(Dispatchers.IO) {
        db.notificationPreferencesQueries.deleteByChildId(childId)
    }
}
