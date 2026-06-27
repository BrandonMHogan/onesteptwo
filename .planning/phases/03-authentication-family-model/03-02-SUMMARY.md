---
phase: 03-authentication-family-model
plan: "02"
subsystem: shared-kmp-auth
tags: [ktor, auth, kmp, clerk-android, bearer-token, nonCancellableRefresh]
dependency_graph:
  requires: []
  provides:
    - AuthRepository interface (com.onesteptwo.auth)
    - HttpClientFactory.buildHttpClient (bearer auth + ContentNegotiation)
    - ClerkAuthRepository (Android Clerk SDK backed)
  affects:
    - shared/build.gradle.kts
    - gradle/libs.versions.toml
    - androidApp (can now depend on ClerkAuthRepository)
tech_stack:
  added:
    - io.ktor:ktor-client-core:3.5.1
    - io.ktor:ktor-client-auth:3.5.1
    - io.ktor:ktor-client-content-negotiation:3.5.1
    - io.ktor:ktor-serialization-kotlinx-json:3.5.1
    - io.ktor:ktor-client-okhttp:3.5.1 (androidMain)
    - io.ktor:ktor-client-darwin:3.5.1 (iosMain)
    - com.clerk:clerk-android-api:1.0.31 (androidMain)
  patterns:
    - Ktor bearer auth plugin with nonCancellableRefresh = true
    - Platform-agnostic repository interface with platform implementations
    - Host-scoped sendWithoutRequest allowlist
key_files:
  created:
    - shared/src/commonMain/kotlin/com/onesteptwo/auth/AuthRepository.kt
    - shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt
    - shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt
  modified:
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
decisions:
  - Assumption A3 resolved: GetTokenOptions.skipCache parameter confirmed in clerk-android-api 1.0.31 bytecode; getToken() uses skipCache=false (cached-or-fresh), refreshToken() uses skipCache=true (always fresh)
  - Clerk.isSignedIn is a Kotlin boolean property, not a function — accessed without parentheses
  - ClerkResult<String, _> unwrapped via is ClerkResult.Success -> result.value
metrics:
  duration: "~5 minutes"
  completed: "2026-06-27"
  tasks: 3
  files: 5
---

# Phase 03 Plan 02: KMP Shared HTTP Auth Layer Summary

**One-liner:** Ktor 3.5.1 bearer auth layer with nonCancellableRefresh=true and host-scoped token injection, backed by a Clerk Android SDK ClerkAuthRepository using GetTokenOptions.skipCache for race-safe 401 refresh.

## What Was Built

This plan delivered the KMP shared HTTP client authentication infrastructure that every platform UI will consume. Three tasks were executed:

**Task 1 — Catalog + shared module dependencies**
Added Ktor 3.5.1 and Clerk Android 1.0.31 to `gradle/libs.versions.toml`: two new version entries, seven library aliases (ktor-client-core, auth, content-negotiation, serialization-kotlinx-json, okhttp, darwin, clerk-android-api), and the `kotlin-serialization` plugin alias. Updated `shared/build.gradle.kts` to apply the serialization plugin and wire the correct libs per source set.

**Task 2 — AuthRepository interface + HttpClientFactory**
Created `AuthRepository.kt` in commonMain with `getToken(): String?`, `refreshToken(): String?`, and `isSignedIn(): Boolean`. Created `HttpClientFactory.kt` with `buildHttpClient(authRepository, baseUrl)` installing the Ktor `Auth` bearer plugin:
- `loadTokens` calls `authRepository.getToken()`
- `refreshTokens` calls `authRepository.refreshToken()`
- `nonCancellableRefresh = true` (REQ-NF-006 / KTOR-7852 fix)
- `sendWithoutRequest` allowlist: `*.onesteptwo.com`, `onesteptwo-staging.up.railway.app`, `localhost`, `10.0.2.2`
- `ContentNegotiation` with `json()` installed

**Task 3 — Android ClerkAuthRepository**
Created `ClerkAuthRepository.kt` in androidMain implementing the interface against `clerk-android-api:1.0.31`. Verified API surface from bytecode before writing. `getToken()` uses `GetTokenOptions(skipCache = false)` and `refreshToken()` uses `GetTokenOptions(skipCache = true)` to guarantee fresh tokens for 401 retries.

## Assumption A3 Resolution

Inspected `com/clerk/api/session/GetTokenOptions.class` from the cached `clerk-android-api-1.0.31-api.jar`:
- `GetTokenOptions` has three fields: `template: String`, `skipCache: Boolean`, `expirationBuffer: Long`
- `Auth.getToken(GetTokenOptions)` returns `ClerkResult<String, ClerkErrorResponse>`
- `ClerkResult.Success<T>` exposes `.value` to unwrap the token string
- `Clerk.isSignedIn` is a Kotlin boolean **property** (not function), accessed without `()`

SDK method names used:
- `Clerk.auth.getToken(GetTokenOptions(skipCache = false))` — for `getToken()`
- `Clerk.auth.getToken(GetTokenOptions(skipCache = true))` — for `refreshToken()`
- `Clerk.isSignedIn` — for `isSignedIn()`

A skip-cache option IS required and was used. Assumption A3 risk is resolved.

## Threat Model Coverage

| Threat | Disposition | Implementation |
|--------|-------------|----------------|
| T-3-07 Bearer token leakage to third-party hosts | Mitigated | `sendWithoutRequest` allowlist limits proactive auth header |
| T-3-08 Concurrent 401 refresh race (KTOR-7852) | Mitigated | `nonCancellableRefresh = true` in bearer block |
| T-3-09 Stale token replayed after refresh | Mitigated | `refreshToken()` uses `GetTokenOptions(skipCache = true)` |
| T-3-SC Package install legitimacy | Accepted | All packages verified in RESEARCH.md Package Legitimacy Audit |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Clerk.isSignedIn() → Clerk.isSignedIn (property access)**
- **Found during:** Task 3, first compile attempt
- **Issue:** `Clerk.isSignedIn()` caused "Cannot infer type for type parameter T, R" — bytecode confirms it is a Kotlin property, not a function, compiled as `getIsSignedIn()` but accessed in Kotlin as `Clerk.isSignedIn` (no parentheses).
- **Fix:** Changed `Clerk.isSignedIn()` to `Clerk.isSignedIn`
- **Files modified:** `shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt`
- **Commit:** ab635e6

## Self-Check: PASSED

| Item | Status |
|------|--------|
| shared/src/commonMain/kotlin/com/onesteptwo/auth/AuthRepository.kt | FOUND |
| shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt | FOUND |
| shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt | FOUND |
| Commit 94c8520 (Task 1 — catalog + deps) | FOUND |
| Commit d06d814 (Task 2 — AuthRepository + HttpClientFactory) | FOUND |
| Commit ab635e6 (Task 3 — ClerkAuthRepository) | FOUND |
