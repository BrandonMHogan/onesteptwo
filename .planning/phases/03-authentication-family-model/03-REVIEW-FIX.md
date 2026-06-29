---
phase: 03-authentication-family-model
fixed_at: 2026-06-29T00:00:00Z
review_path: .planning/phases/03-authentication-family-model/03-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 11
skipped: 0
status: all_fixed
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-06-29
**Source review:** .planning/phases/03-authentication-family-model/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (2 critical, 9 warnings)
- Fixed: 11
- Skipped: 0

## Fixed Issues

### CR-01: `Clerk.auth.setActive()` unguarded in `navigateAfterAuth` — crash on API failure

**Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt`
**Commit:** `6f3fc34`
**Applied fix:** Wrapped the bare `Clerk.auth.setActive(sessionId, orgId)` call in the `1 ->` branch of `navigateAfterAuth` with a `try/catch (e: Exception)` block. On failure, logs via `Timber.e` and continues to navigate to "postauth" so the app does not crash. This mirrors the error-handling pattern already present in `OrgPickerScreen.kt`.

---

### CR-02: `Timber.DebugTree` planted unconditionally — session IDs and user IDs logged in production

**Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt`
**Commit:** `95d685d`
**Applied fix:** Wrapped `Timber.plant(Timber.DebugTree())` with `if (BuildConfig.DEBUG)` so the debug tree (which routes all `Timber.*` calls to `android.util.Log`) is only installed in debug builds. Release builds will not log session IDs, user IDs, or auth events to logcat.

---

### WR-01: iOS `PostAuthRouter` does not call `setActive` for single-org users — JWT lacks `org_id`

**Files modified:** `iosApp/iosApp/ContentView.swift` (iOS sub-repo commit `cf899a0`)
**Commit:** `cf899a0` (iOS sub-repo; combined with WR-03 — same file)
**Applied fix:** Added auto-activation logic inside `loadMemberships()`: when exactly one org is returned and `lastActiveOrganizationId` is nil, calls `Clerk.shared.auth.setActive(sessionId:organizationId:)` with `try?` (non-fatal). Mirrors the Android `navigateAfterAuth` single-org path. Without this, the JWT lacked `org_id` and all backend calls requiring `ActiveOrganizationID` returned 403.

---

### WR-02: `PostAuthStub` reads org role once — stale state, not reactively observed

**Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/ui/PostAuthStub.kt`
**Commit:** `225d420`
**Applied fix:** Replaced `mutableStateOf(Clerk.organizationMembership?.role == "org:admin")` with `derivedStateOf { Clerk.organizationMembership?.role == "org:admin" }` inside `remember`. Also swapped the now-unused `mutableStateOf` import for `derivedStateOf`. The derived state re-evaluates reactively whenever `Clerk.organizationMembership` changes.

---

### WR-03: iOS `loadMemberships` silently ignores errors — multi-org user loses org picker

**Files modified:** `iosApp/iosApp/ContentView.swift` (iOS sub-repo commit `cf899a0`)
**Commit:** `cf899a0` (iOS sub-repo; combined with WR-01 — same file)
**Applied fix:** Added `@State private var loadError: String?` to `PostAuthRouter`. The `catch` block now sets `loadError = "Couldn't load your account. Pull down to try again."` instead of silently swallowing the error. Added an error banner inside the post-auth stub `VStack` that displays when `loadError` is non-nil. Added `.refreshable { await loadMemberships() }` modifier to provide a retry path.

---

### WR-04: iOS `InviteCaregiverView` email validation too weak — `@.` passes

**Files modified:** `iosApp/iosApp/Settings/InviteCaregiverView.swift` (iOS sub-repo commit `023340a`)
**Commit:** `023340a` (iOS sub-repo)
**Applied fix:** Added a private `isValidEmail(_ email: String) -> Bool` helper using the regex pattern `^[^\s@]+@[^\s@]+\.[^\s@]+$`. Replaced the `trimmedEmail.contains("@") && trimmedEmail.contains(".")` guard with `isValidEmail(trimmedEmail)`. The new validator rejects `@.`, `a@`, `@b.` and similar malformed inputs, consistent with Android's `Patterns.EMAIL_ADDRESS` matcher.

---

### WR-05: `CLERK_AUTHORIZED_PARTY` unset means `azp` claim is never verified

**Files modified:** `backend/cmd/server/main.go`
**Commit:** `7c29ad2`
**Applied fix:** Extracted `os.Getenv("CLERK_AUTHORIZED_PARTY")` into a named `authorizedParty` variable. Added an `if authorizedParty == ""` block that logs a startup warning (`log.Println("WARNING: CLERK_AUTHORIZED_PARTY is not set — azp claim unverified; set to mobile bundle ID for production")`). The warning makes the unverified-azp risk visible in server logs rather than being silently ignored.

---

### WR-06: `clerk-sdk-go/v2` marked `// indirect` despite direct imports in two files

**Files modified:** `backend/go.mod`
**Commit:** `39d343e`
**Applied fix:** Moved `github.com/clerk/clerk-sdk-go/v2 v2.7.0` from the `// indirect` block to the direct-dependency `require` block, removing the incorrect `// indirect` annotation. The entry is now alphabetically ordered alongside the other direct dependencies.

---

### WR-07: No `db.Ping()` after `sql.Open` — server starts with an unreachable database

**Files modified:** `backend/cmd/server/main.go`
**Commit:** `6899a8f`
**Applied fix:** Added `"context"` import and a `db.PingContext(context.Background())` call immediately after `db.SetMaxIdleConns(5)`. On failure, logs a fatal error (`log.Fatalf("database unreachable: %v", err)`) so the server exits at startup rather than silently accepting requests that will all return 500.

---

### WR-08: Bearer token allowlist includes `localhost` and `10.0.2.2` unconditionally

**Files modified:**
- `shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt` (commit `f955a98`)
- `iosApp/iosApp/Auth/ClerkAuthRepository.swift` (iOS sub-repo commit `79ee32a`)

**Commits:** `f955a98` (shared Kotlin), `79ee32a` (iOS sub-repo)
**Applied fix:** Added `isDebug: Boolean = false` parameter to `buildHttpClient()`. The `localhost` and `10.0.2.2` allowlist entries are now wrapped in `(isDebug && (...))` so release builds never proactively attach bearer tokens to loopback addresses. On iOS, `buildClient()` was updated to pass `isDebug: true` inside `#if DEBUG` and `isDebug: false` in the `#else` branch, using Swift's compile-time flag to select the correct value.

---

### WR-09: `navigateAfterAuth` ignores `setActive` return value — silent failure possible

**Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt`
**Commit:** `67014ad`
**Applied fix:** Added a `Timber.d(...)` call inside the `try` block immediately after `Clerk.auth.setActive(sessionId, orgId)` that logs the post-activation org ID and role (`Clerk.organizationMembership?.organization?.id` and `Clerk.organizationMembership?.role`). If `setActive` completes without throwing but the membership state is still null, this log line surfaces the condition in debug logcat.

---

_Fixed: 2026-06-29_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
