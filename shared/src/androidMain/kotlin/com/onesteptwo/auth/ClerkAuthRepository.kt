package com.onesteptwo.auth

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.GetTokenOptions

/**
 * Android implementation of [AuthRepository] backed by the Clerk Android SDK.
 *
 * Assumption A3 resolution (verified against clerk-android-api 1.0.31 bytecode):
 *  - [Clerk.auth.getToken] accepts a [GetTokenOptions] parameter with a [GetTokenOptions.skipCache]
 *    boolean. When [skipCache] is false (the default), the SDK returns a cached token if it is
 *    still valid. When [skipCache] is true, the SDK always fetches a fresh token from the network.
 *  - [getToken] uses the default (skipCache = false) — safe for normal request injection because
 *    the SDK handles expiry automatically.
 *  - [refreshToken] sets skipCache = true to guarantee the Ktor [refreshTokens] block never
 *    returns a stale token after a 401 response (T-3-09).
 *  - [isSignedIn] delegates to [Clerk.isSignedIn], a synchronous property on the Clerk singleton
 *    that reflects the current session state.
 *
 * Pitfall 7 note: [Clerk.initialize] MUST be called in an Application subclass before any
 * method here is invoked. Missing initialisation causes NPE / UninitializedPropertyAccessException.
 */
class ClerkAuthRepository : AuthRepository {

    /**
     * Returns the current Clerk session token (cached if still valid), or null when no
     * session is active or the SDK call fails.
     */
    override suspend fun getToken(): String? =
        when (val result = Clerk.auth.getToken(GetTokenOptions(skipCache = false))) {
            is ClerkResult.Success -> result.value
            else -> null
        }

    /**
     * Returns a freshly minted Clerk session token, bypassing the SDK cache.
     *
     * [skipCache = true] ensures that when Ktor's [refreshTokens] block calls this after
     * a 401 response, it receives a new JWT rather than replaying the same stale token
     * that triggered the 401 (T-3-09).
     */
    override suspend fun refreshToken(): String? =
        when (val result = Clerk.auth.getToken(GetTokenOptions(skipCache = true))) {
            is ClerkResult.Success -> result.value
            else -> null
        }

    /**
     * Returns true when there is an active Clerk session for the current user.
     *
     * [Clerk.isSignedIn] is a Kotlin boolean property on the Clerk singleton (confirmed in
     * clerk-android-api 1.0.31 bytecode — [com.clerk.api.Clerk.isSignedIn]).
     * Accessed as a property (no parentheses) per Kotlin conventions for `is*` boolean members.
     */
    override fun isSignedIn(): Boolean = Clerk.isSignedIn
}
