package com.onesteptwo.api

import com.onesteptwo.auth.AuthRepository
import com.onesteptwo.auth.buildHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
data class PatchChildRequest(
    val nickname: String? = null,
    val birth_month: Int? = null,
    val birth_year: Int? = null
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
 * Typed wrapper around `/v1/children` and `/v1/account` (`backend/internal/api/server.go`).
 * `createChild`/`getChildren` shipped in 05-01-PLAN.md (Stage 1); `patchChild`/`deleteChild`/
 * `deleteAccount` are 05-02-PLAN.md Stage 2 additions.
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
     * returning/invited caregiver with an empty local database skips the onboarding wizard.
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

    /** Settings "Edit child" — partial update; only non-null params are changed server-side. */
    suspend fun patchChild(
        id: String,
        nickname: String?,
        birthMonth: Int?,
        birthYear: Int?
    ): ApiResult<ChildResponse> {
        return try {
            val response = httpClient.patch("$baseUrl/v1/children/$id") {
                contentType(ContentType.Application.Json)
                setBody(PatchChildRequest(nickname, birthMonth, birthYear))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't update your child's profile. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /** Settings "Remove child" — REQ-011 erasure cascade. */
    suspend fun deleteChild(id: String): ApiResult<Unit> {
        return try {
            val response = httpClient.delete("$baseUrl/v1/children/$id")
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("Couldn't remove child. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /** Settings admin "Delete my data" — REQ-012 full family erasure cascade. */
    suspend fun deleteAccount(): ApiResult<Unit> {
        return try {
            val response = httpClient.delete("$baseUrl/v1/account")
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("Couldn't delete your data. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }
}

fun createChildrenApiClient(authRepository: AuthRepository, baseUrl: String, isDebug: Boolean): ChildrenApiClient =
    ChildrenApiClient(buildHttpClient(authRepository, baseUrl, isDebug), baseUrl)
