package com.onesteptwo.api

import com.onesteptwo.auth.AuthRepository
import com.onesteptwo.auth.buildHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class NotificationPreferenceResponse(val child_id: String, val enabled: Boolean)

@Serializable
data class PutNotificationPreferenceRequest(val child_id: String, val enabled: Boolean)

/** Typed wrapper around `/v1/notification-preferences` (REQ-022, `backend/internal/api/server.go`). */
class NotificationPreferencesApiClient(private val httpClient: HttpClient, private val baseUrl: String) {
    suspend fun getPreferences(): ApiResult<List<NotificationPreferenceResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/v1/notification-preferences")
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't load data. Pull down to refresh.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    suspend fun putPreference(childId: String, enabled: Boolean): ApiResult<NotificationPreferenceResponse> {
        return try {
            val response = httpClient.put("$baseUrl/v1/notification-preferences") {
                contentType(ContentType.Application.Json)
                setBody(PutNotificationPreferenceRequest(childId, enabled))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't save notification preference. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }
}

fun createNotificationPreferencesApiClient(
    authRepository: AuthRepository,
    baseUrl: String,
    isDebug: Boolean
): NotificationPreferencesApiClient =
    NotificationPreferencesApiClient(buildHttpClient(authRepository, baseUrl, isDebug), baseUrl)
