package com.onesteptwo.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds the shared [HttpClient] used by every platform UI.
 *
 * Security properties enforced here:
 *  - T-3-07: bearer token is sent proactively ONLY to OneStepTwo API hosts; arbitrary
 *    third-party hosts never receive the token ([sendWithoutRequest] allowlist).
 *  - T-3-08 / REQ-019 / REQ-NF-006: [nonCancellableRefresh] = true prevents KTOR-7852
 *    where cancelling the originating coroutine mid-refresh rolls back a completed token
 *    update, causing a stuck-session loop of repeated 401s.
 *  - T-3-09: [refreshTokens] always calls [AuthRepository.refreshToken] which MUST
 *    return a freshly minted token (no cache), so 401 retries use a new JWT.
 *
 * @param authRepository Platform-specific Clerk session token provider.
 * @param baseUrl        Informational parameter; not used for routing — the
 *                       [sendWithoutRequest] allowlist controls proactive auth.
 */
fun buildHttpClient(authRepository: AuthRepository, baseUrl: String = ""): HttpClient {
    return HttpClient {
        install(Auth) {
            bearer {
                // loadTokens: called before every request that lacks an Authorization header.
                // Returns the current (possibly cached) token.
                loadTokens {
                    authRepository.getToken()?.let { BearerTokens(it, "") }
                }

                // refreshTokens: called after a 401 response to obtain a fresh token.
                // REQ-019 / T-3-09: authRepository.refreshToken() MUST bypass cache.
                refreshTokens {
                    authRepository.refreshToken()?.let { BearerTokens(it, "") }
                }

                // REQ-NF-006 / T-3-08: KTOR-7852 fix — prevents caller cancellation from
                // rolling back a completed token refresh, which would cause a stuck-session
                // loop. Must be true for a production app where navigation can cancel coroutines.
                nonCancellableRefresh = true

                // T-3-07: Send the Authorization header proactively ONLY to OneStepTwo API
                // hosts. Requests to third-party URLs (analytics, CDN, etc.) never receive
                // the bearer token. Never blanket-return true here.
                sendWithoutRequest { request ->
                    val host = request.url.host
                    host.endsWith("onesteptwo.com") ||              // production + subdomains
                    host == "onesteptwo-staging.up.railway.app" ||  // Railway staging
                    host == "localhost" ||                           // local dev
                    host == "10.0.2.2"                              // Android emulator loopback
                }
            }
        }

        install(ContentNegotiation) {
            json()
        }
    }
}
