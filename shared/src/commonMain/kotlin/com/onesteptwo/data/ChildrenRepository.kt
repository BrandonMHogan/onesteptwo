package com.onesteptwo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.onesteptwo.db.Children
import com.onesteptwo.db.OneStepTwoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChildrenRepository(private val db: OneStepTwoDatabase) {

    fun observeAll(): Flow<List<Children>> =
        db.childrenQueries.selectAll().asFlow().mapToList(Dispatchers.IO)

    suspend fun getAll(): List<Children> = withContext(Dispatchers.IO) {
        db.childrenQueries.selectAll().executeAsList()
    }

    suspend fun getById(id: String): Children? = withContext(Dispatchers.IO) {
        db.childrenQueries.selectById(id).executeAsOneOrNull()
    }

    suspend fun insert(
        id: String,
        clerkOrgId: String,
        nickname: String,
        birthMonth: Long,
        birthYear: Long,
        consentEventId: String,
        createdAt: String,
        updatedAt: String
    ) = withContext(Dispatchers.IO) {
        db.childrenQueries.insert(
            id = id,
            clerk_org_id = clerkOrgId,
            nickname = nickname,
            birth_month = birthMonth,
            birth_year = birthYear,
            consent_event_id = consentEventId,
            created_at = createdAt,
            updated_at = updatedAt
        )
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        db.childrenQueries.deleteById(id)
    }
}
