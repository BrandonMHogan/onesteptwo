package com.onesteptwo.db

import app.cash.sqldelight.db.SqlDriver

/** Platform-specific SQLDelight driver construction (Android: AndroidSqliteDriver). */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
