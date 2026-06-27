package com.onesteptwo.auth

/**
 * Platform-agnostic interface for retrieving Clerk session tokens.
 *
 * Android and iOS each provide a concrete implementation backed by their
 * respective Clerk SDKs. The shared [HttpClientFactory] depends only on
 * this interface so the Ktor layer stays decoupled from platform SDKs.
 *
 * REQ-019: getToken / refreshToken feed the Ktor bearer auth plugin.
 */
interface AuthRepository {
    /**
     * Returns the current Clerk session token, or null when no session is active.
     * May return a cached token if one is still valid.
     */
    suspend fun getToken(): String?

    /**
     * Returns a freshly minted Clerk session token, or null when no session is active.
     * Implementations MUST bypass any cache so the Ktor [refreshTokens] block
     * does not replay a stale token on a 401 retry.
     */
    suspend fun refreshToken(): String?

    /**
     * Returns true when there is an active Clerk session for the current user.
     */
    fun isSignedIn(): Boolean
}
