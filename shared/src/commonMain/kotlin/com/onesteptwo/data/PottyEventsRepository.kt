package com.onesteptwo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.onesteptwo.db.OneStepTwoDatabase
import com.onesteptwo.db.Potty_events
import com.onesteptwo.db.SelectDailyCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PottyEventsRepository(private val db: OneStepTwoDatabase) {

    /** Inserts a new event immediately (event_type=NULL, sync_status='pending'); returns its client-generated id (REQ-006). */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun logNewEvent(childId: String, loggedBy: String, now: String): String =
        withContext(Dispatchers.IO) {
            val id = Uuid.random().toString()
            db.pottyEventsQueries.insertPending(
                id = id,
                child_id = childId,
                logged_by = loggedBy,
                occurred_at = now,
                created_at = now,
                updated_at = now
            )
            id
        }

    suspend fun setEventType(id: String, eventType: String?, updatedBy: String?, now: String) =
        withContext(Dispatchers.IO) {
            db.pottyEventsQueries.setEventType(
                event_type = eventType,
                updated_at = now,
                updated_by = updatedBy,
                id = id
            )
        }

    /** Updates type/note/time while preserving the original created_at (Event Detail sheet "Save details"). */
    suspend fun updateDetails(
        id: String,
        eventType: String?,
        notes: String?,
        occurredAt: String,
        updatedBy: String?,
        now: String
    ) = withContext(Dispatchers.IO) {
        db.pottyEventsQueries.updateDetails(
            event_type = eventType,
            notes = notes,
            occurred_at = occurredAt,
            updated_at = now,
            updated_by = updatedBy,
            id = id
        )
    }

    /** Soft delete (REQ-007) — hard delete is reserved for erasure requests only. */
    suspend fun deleteEvent(id: String, deletedBy: String?, now: String) = withContext(Dispatchers.IO) {
        db.pottyEventsQueries.deleteEvent(deleted_at = now, deleted_by = deletedBy, id = id)
    }

    suspend fun getById(id: String): Potty_events? = withContext(Dispatchers.IO) {
        db.pottyEventsQueries.selectById(id).executeAsOneOrNull()
    }

    fun observeByChildAndDayRange(childId: String, startInclusive: String, endExclusive: String): Flow<List<Potty_events>> =
        db.pottyEventsQueries.selectByChildAndDayRange(childId, startInclusive, endExclusive)
            .asFlow().mapToList(Dispatchers.IO)

    /** Status chip "[N] need details" — active child's events only, per D-10. */
    fun observePendingDetailsCount(childId: String, startInclusive: String, endExclusive: String): Flow<Long> =
        db.pottyEventsQueries.selectPendingDetailsCountForChildDayRange(childId, startInclusive, endExclusive)
            .asFlow().mapToOne(Dispatchers.IO)

    /** Status chip "[N] syncing…" — local pending-sync queue depth (full sync mechanics land in Phase 6). */
    fun observePendingSyncCount(childId: String): Flow<Long> =
        db.pottyEventsQueries.selectPendingSyncCountForChild(childId)
            .asFlow().mapToOne(Dispatchers.IO)

    suspend fun getFirstPendingDetailsEvent(childId: String, startInclusive: String, endExclusive: String): Potty_events? =
        withContext(Dispatchers.IO) {
            db.pottyEventsQueries.selectFirstPendingDetailsEventForChildDayRange(childId, startInclusive, endExclusive)
                .executeAsOneOrNull()
        }

    /** Daily event counts since [sinceInclusive] for the History heatmap (Stage 2). */
    suspend fun getDailyCounts(childId: String, sinceInclusive: String): List<SelectDailyCounts> =
        withContext(Dispatchers.IO) {
            db.pottyEventsQueries.selectDailyCounts(childId, sinceInclusive).executeAsList()
        }
}
