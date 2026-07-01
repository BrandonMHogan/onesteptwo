package com.onesteptwo.api

import com.onesteptwo.auth.AuthRepository
import com.onesteptwo.auth.buildHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class CreateChildConsent(val app_version: String, val consent_text_version: String)

@Serializable
data class CreateChildRequest(
    val nickname: String,
    val birth_month: Int,
    val birth_year: Int,
    val consent: CreateChildConsent
)

@Serializable
data class ChildResponse(
    val id: String,
    val clerk_org_id: String,
    val nickname: String,
    val birth_month: Int,
    val birth_year: Int
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String) : ApiResult<Nothing>
}

/**
 * Thin wrapper around the one endpoint Stage 1 needs (`POST /v1/children`, the consent-gate
 * handler — `backend/internal/api/server.go` `PostV1Children`). A fuller typed `ApiClient`
 * covering more endpoints is Stage 2 scope (`05-02-PLAN.md`).
 */
class ChildrenApiClient(private val httpClient: HttpClient, private val baseUrl: String) {
    suspend fun createChild(
        nickname: String,
        birthMonth: Int,
        birthYear: Int,
        appVersion: String,
        consentTextVersion: String
    ): ApiResult<ChildResponse> {
        return try {
            val response = httpClient.post("$baseUrl/v1/children") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateChildRequest(
                        nickname = nickname,
                        birth_month = birthMonth,
                        birth_year = birthYear,
                        consent = CreateChildConsent(appVersion, consentTextVersion)
                    )
                )
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't save your child's profile. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /**
     * Lists the caller's active-org children — used once on the post-auth routing path so a
     * returning/invited caregiver with an empty local database skips the onboarding wizard
     * (the wizard is admin-only per REQ-036; `GET /v1/children` ships in 05-01-PLAN.md M8).
     */
    suspend fun getChildren(): ApiResult<List<ChildResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/v1/children")
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't load your family. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }
}

fun createChildrenApiClient(authRepository: AuthRepository, baseUrl: String, isDebug: Boolean): ChildrenApiClient =
    ChildrenApiClient(buildHttpClient(authRepository, baseUrl, isDebug), baseUrl)
