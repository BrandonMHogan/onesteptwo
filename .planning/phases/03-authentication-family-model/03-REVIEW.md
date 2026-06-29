---
phase: 03-authentication-family-model
reviewed: 2026-06-29T00:00:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - androidApp/build.gradle.kts
  - androidApp/src/main/AndroidManifest.xml
  - androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/OrgPickerScreen.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignInScreen.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignUpScreen.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/ui/PostAuthStub.kt
  - androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/InviteCaregiverScreen.kt
  - backend/cmd/server/main.go
  - backend/go.mod
  - backend/internal/api/auth_test.go
  - backend/internal/api/children_handler_test.go
  - backend/internal/api/erasure_handler_test.go
  - backend/internal/api/server.go
  - gradle/libs.versions.toml
  - iosApp/iosApp/Auth/ClerkAuthRepository.swift
  - iosApp/iosApp/Auth/OrgPickerView.swift
  - iosApp/iosApp/Auth/SignInView.swift
  - iosApp/iosApp/Auth/SignUpView.swift
  - iosApp/iosApp/ContentView.swift
  - iosApp/iosApp/iosAppApp.swift
  - iosApp/iosApp/Settings/InviteCaregiverView.swift
  - shared/build.gradle.kts
  - shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt
  - shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt
findings:
  critical: 2
  warning: 9
  info: 2
  total: 13
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-06-29
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Reviewed the full Phase 03 authentication and family-model implementation across the Android app, iOS app, shared KMP module, and Go backend. The backend handlers are well-structured with correct FK-safe deletion ordering, proper IDOR checks, and consistent auth gating. The iOS and Android UI flows are largely correct.

Two blockers were found: (1) an unguarded `setActive` suspend call in `navigateAfterAuth` that can crash the Android app on failure, and (2) unconditional `Timber.DebugTree` planting that logs session IDs and user IDs to logcat in production release builds. Nine warnings cover a stale role read in `PostAuthStub`, a missing single-org activation step on iOS, silent failure of the org-membership fetch in `ContentView`, a weak iOS email validator, missing `db.Ping` in the backend, an unverified `azp` claim, an overly broad bearer-token host allowlist, `clerk-sdk-go/v2` incorrectly marked indirect, and an unguarded `setActive` return value. Two info items cover Android backup configuration and a gap in IDOR unit-test coverage.

---

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: `Clerk.auth.setActive()` unguarded in `navigateAfterAuth` — crash on API failure

**File:** `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt:218-225`
**Issue:** In the `1 ->` branch of `navigateAfterAuth`, `Clerk.auth.setActive(sessionId, orgId)` is called as a bare suspend call with no surrounding try/catch. If `setActive` throws (e.g., network error, stale session), the exception propagates out of `navigateAfterAuth` into the `coroutineScope.launch { navigateAfterAuth(navController) }` callsite at lines 128/135. `rememberCoroutineScope()` uses a `Job`-backed scope (not `SupervisorJob`), so the uncaught exception cancels the scope and reaches the thread's uncaught exception handler — crashing the app. The identical call in `OrgPickerScreen.kt` is correctly wrapped in try/catch (lines 203–217), making this inconsistency clearly unintentional.

**Fix:**
```kotlin
1 -> {
    val orgId = memberships.first().organization.id
    val sessionId = Clerk.session?.id ?: ""
    try {
        Clerk.auth.setActive(sessionId, orgId)
    } catch (e: Exception) {
        Timber.e(e, "navigateAfterAuth: setActive failed for org=$orgId")
        // Proceed to postauth; the JWT will lack org_id, but Phase 5 handles this.
    }
    navController.navigate("postauth") { popUpTo(0) { inclusive = true } }
}
```

---

### CR-02: `Timber.DebugTree` planted unconditionally — session IDs and user IDs logged in production

**File:** `androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt:17`
**Issue:** `Timber.plant(Timber.DebugTree())` is called unconditionally in all build variants including release. `DebugTree` routes every `Timber.d/w/e/i` call to `android.util.Log`, which writes to logcat. Several call sites log sensitive identifiers:
- `AppNavigation.kt:73`: `"sessionId=${currentSession?.id}"` — Clerk session token IDs
- `AppNavigation.kt:100`: `"sessionId=${event.session.id}, userId=${event.user.id}"` — user identifiers
- `AppNavigation.kt:103`: `"AuthEvent.SignedOut"` with `isSignedIn`
- `SignInScreen.kt:98`: `"sessionId=${Clerk.session?.id}, userId=${Clerk.user?.id}"`

Session IDs in logcat are accessible to crash-reporting SDKs (Crashlytics, Sentry) that attach logcat, to other apps holding `READ_LOGS` permission, and to anyone with physical device access + `adb logcat`. Leaking session IDs gives an attacker the ability to impersonate users whose session tokens are unexpired.

**Fix:**
```kotlin
override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    }
    Clerk.initialize(this, BuildConfig.CLERK_PUBLISHABLE_KEY)
}
```
Replace all `Timber.d(...)` calls that log session/user IDs with `Timber.d(...)` guarded by `BuildConfig.DEBUG`, or omit them entirely from logging callsites.

---

## Warnings

### WR-01: iOS `PostAuthRouter` does not call `setActive` for single-org users — JWT lacks `org_id`

**File:** `iosApp/iosApp/ContentView.swift:68`
**Issue:** `PostAuthRouter` routes to the org picker only when `memberships.count > 1 && clerk.session?.lastActiveOrganizationId == nil`. When a user has exactly one org and `lastActiveOrganizationId` is nil (just signed in), the condition is false and the user lands on the post-auth stub with no active organization. The session JWT never carries an `org_id` claim, and every backend call requiring `ActiveOrganizationID` will fail with 403. The Android counterpart (`navigateAfterAuth`, lines 218–225) explicitly calls `setActive` for the single-org case before navigating.

**Fix:**
```swift
private func loadMemberships() async {
    do {
        let result = try await Clerk.shared.user?.getOrganizationMemberships()
        memberships = result?.data ?? []

        // Auto-activate single org — mirrors Android navigateAfterAuth single-org path.
        if memberships.count == 1,
           let org = memberships.first,
           let sessionId = Clerk.shared.session?.id,
           Clerk.shared.session?.lastActiveOrganizationId == nil {
            try? await Clerk.shared.auth.setActive(
                sessionId: sessionId, organizationId: org.organization.id)
        }
    } catch {
        // Non-fatal fallback
    }
    isLoading = false
}
```

---

### WR-02: `PostAuthStub` reads org role once — stale state, not reactively observed

**File:** `androidApp/src/main/kotlin/com/onesteptwo/android/ui/PostAuthStub.kt:36-38`
**Issue:**
```kotlin
val isAdmin by remember {
    mutableStateOf(Clerk.organizationMembership?.role == "org:admin")
}
```
`Clerk.organizationMembership?.role` is read once at initial composition and cached. If the active org membership changes (e.g., a remote role change, sign out/sign in with a different role), the `isAdmin` value never updates and the Invite button visibility becomes stale. This also creates a subtle risk: if `PostAuthStub` is ever composed before `setActive` completes (timing edge case), `organizationMembership` may be null and `isAdmin` is permanently set to `false` for that session.

**Fix:**
```kotlin
// Derive reactively from a Clerk state flow, or use derivedStateOf pattern:
val isAdmin by remember {
    derivedStateOf { Clerk.organizationMembership?.role == "org:admin" }
}
```
Alternatively, observe `Clerk.auth.events` for `SessionChanged` events to trigger recomposition.

---

### WR-03: iOS `loadMemberships` silently ignores errors — multi-org user loses org picker

**File:** `iosApp/iosApp/ContentView.swift:116-119`
**Issue:**
```swift
} catch {
    // Non-fatal: membership fetch failure defaults to single-org path (memberships = []).
}
```
When the membership fetch fails (e.g., no network on first load), `memberships` stays empty. The condition `memberships.count > 1` is false, so a user who belongs to multiple organizations is silently routed to the post-auth stub with no active org instead of the org picker. All subsequent API calls requiring `ActiveOrganizationID` will return 403. There is no error indicator shown to the user and no retry path.

**Fix:** Surface a UI error or loading-retry state when the fetch fails, similar to `OrgPickerView.swift`'s `loadError`/pull-to-refresh pattern:
```swift
} catch {
    loadError = "Couldn't load your account. Pull down to try again."
    memberships = []
}
isLoading = false
```
Add a corresponding error view and `.refreshable` modifier to the `PostAuthRouter` body.

---

### WR-04: iOS `InviteCaregiverView` email validation too weak — `@.` passes

**File:** `iosApp/iosApp/Settings/InviteCaregiverView.swift:119`
**Issue:**
```swift
guard trimmedEmail.contains("@") && trimmedEmail.contains(".") else {
    inlineError = "Enter a valid email address."
    return
}
```
This check passes for strings like `@.`, `a@`, `@b.`, etc. The Android implementation uses `android.util.Patterns.EMAIL_ADDRESS.matcher(...).matches()` which performs proper RFC-5321 regex validation. The iOS validator will make unnecessary Clerk API calls for malformed addresses and shows no local error, degrading UX. The inconsistency also means the same user action can behave differently between platforms.

**Fix:** Use a tighter regex or a reusable validator consistent with Android:
```swift
private func isValidEmail(_ email: String) -> Bool {
    let pattern = #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#
    return email.range(of: pattern, options: .regularExpression) != nil
}
```
Replace the `contains` check with `isValidEmail(trimmedEmail)`.

---

### WR-05: `CLERK_AUTHORIZED_PARTY` unset means `azp` claim is never verified

**File:** `backend/cmd/server/main.go:40-43`
**Issue:**
```go
authMiddleware := clerkhttp.WithHeaderAuthorization(
    clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY")),
    ...
)
```
When `CLERK_AUTHORIZED_PARTY` is empty (comment: "when the env var is empty, `AuthorizedPartyMatches("")` is a no-op"), the `azp` claim in incoming JWTs is never checked. Any JWT signed by the same Clerk instance — regardless of which client application it was issued to — will pass authentication. In a Clerk account with multiple configured applications (web dashboard, admin panel, etc.), a token from one app can call the OneStepTwo backend API. The env var must be set to the mobile app bundle ID before production launch.

**Fix:** Fail fast at startup if the var is absent in non-development environments:
```go
authorizedParty := os.Getenv("CLERK_AUTHORIZED_PARTY")
if authorizedParty == "" {
    log.Println("WARNING: CLERK_AUTHORIZED_PARTY is not set — azp claim unverified")
}
authMiddleware := clerkhttp.WithHeaderAuthorization(
    clerkhttp.AuthorizedPartyMatches(authorizedParty),
    clerkhttp.Leeway(5*time.Second),
)
```
For production, reject startup if the env var is missing.

---

### WR-06: `clerk-sdk-go/v2` marked `// indirect` despite direct imports in two files

**File:** `backend/go.mod:16`
**Issue:**
```
github.com/clerk/clerk-sdk-go/v2 v2.7.0 // indirect
```
`main.go` imports `github.com/clerk/clerk-sdk-go/v2` and `github.com/clerk/clerk-sdk-go/v2/http` directly. `server.go` imports `github.com/clerk/clerk-sdk-go/v2`. The `// indirect` annotation is incorrect — it signals a transitive dependency, not a direct one. `go mod tidy` would move this to the direct-dependency block. As-is, automated dependency tooling may handle version updates incorrectly, and the module graph is misleading.

**Fix:** Run `go mod tidy` to reclassify the dependency, or manually move it:
```
require (
    github.com/clerk/clerk-sdk-go/v2 v2.7.0
    github.com/google/uuid v1.6.0
    github.com/lib/pq v1.12.3
    ...
)
```

---

### WR-07: No `db.Ping()` after `sql.Open` — server starts with an unreachable database

**File:** `backend/cmd/server/main.go:24-30`
**Issue:** `sql.Open("postgres", ...)` validates the driver name and DSN syntax but does not open a connection. If `DATABASE_URL` is wrong or the database is unreachable, the server starts and logs "starting server on :8080" without error. The first actual request that uses the DB will fail with a 500 and log a connection error at that point rather than at startup. This makes deployment failures silent until traffic hits the server.

**Fix:**
```go
db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
if err != nil {
    log.Fatal(err)
}
if err := db.PingContext(context.Background()); err != nil {
    log.Fatalf("database unreachable: %v", err)
}
```

---

### WR-08: Bearer token allowlist includes `localhost` and `10.0.2.2` unconditionally

**File:** `shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt:53-54`
**Issue:**
```kotlin
host == "localhost" ||   // local dev
host == "10.0.2.2"      // Android emulator loopback
```
These entries in `sendWithoutRequest` are not guarded by a debug/release build flag. Production release builds will proactively attach the Clerk bearer token to any request made to `localhost` or the Android emulator loopback. While production apps are unlikely to make such requests in the field, a development proxy, debugging tool, or SSRF-like redirect to `localhost` would receive the token. The token should be scoped as tightly as possible in production.

**Fix:** Make the dev allowlist conditional:
```kotlin
sendWithoutRequest { request ->
    val host = request.url.host
    host.endsWith("onesteptwo.com") ||
    host == "onesteptwo-staging.up.railway.app" ||
    (BuildConfig.DEBUG && (host == "localhost" || host == "10.0.2.2"))
}
```
iOS should apply the same conditional using a debug compile flag.

---

### WR-09: `navigateAfterAuth` ignores `setActive` return value — silent failure possible

**File:** `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt:224`
**Issue:** Even after wrapping `setActive` in try/catch (see CR-01), the call discards the return value. If `Clerk.auth.setActive` returns a failure result without throwing (possible depending on SDK version), navigation to "postauth" still proceeds but the JWT will not carry `org_id`. Every backend API call requiring `ActiveOrganizationID` will return 403 for the session. The `OrgPickerScreen.kt` correctly checks the result inside the try block by observing `Clerk.organizationMembership` indirectly.

**Fix:** After the `setActive` call, log or check that `Clerk.activeSession != null` and `Clerk.organizationMembership != null` before navigating:
```kotlin
Clerk.auth.setActive(sessionId, orgId)
Timber.d("navigateAfterAuth: post-setActive activeOrg=${Clerk.organizationMembership?.organization?.id}")
```
And/or verify the result type if the SDK returns a `ClerkResult`.

---

## Info

### IN-01: `android:allowBackup="true"` without explicit backup exclusion rules

**File:** `androidApp/src/main/AndroidManifest.xml:7`
**Issue:** `android:allowBackup="true"` enables Android Auto Backup (API 23+), which by default backs up `SharedPreferences`, internal files, and SQLite databases to Google Drive. The Clerk Android SDK persists session tokens to internal storage. Without an explicit `android:fullBackupContent` or `android:dataExtractionRules` attribute excluding Clerk's credential files, session tokens may be backed up and later restored to a new device — potentially extending a session the user considers revoked (e.g., after logout on a lost phone).

**Fix:** Add a backup rules file that excludes Clerk SDK data, or set `android:allowBackup="false"` until backup exclusion rules are authored:
```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

---

### IN-02: IDOR unit test does not cover the ownership check — integration test gap

**File:** `backend/internal/api/auth_test.go:179-204`
**Issue:** `TestDeleteV1ChildrenId_CrossOrgDelete_Returns403` is named to suggest IDOR coverage but the test body explicitly tests only the caregiver role gate (which fires before the DB). The comment acknowledges the IDOR ownership check (line 184: "Full IDOR coverage is in integration tests") but no integration test for it appears in the reviewed files. The ownership check at `server.go:211` (`claims.ActiveOrganizationID != childClerkOrgID`) is the only defense against a cross-org delete. If a future refactor moves this check or changes the condition, there is no fast-running unit test to catch the regression.

**Fix:** Add a table-driven unit test that supplies a mock DB response returning a `childClerkOrgID` different from the claims' `ActiveOrganizationID`, and asserts a 403 response. This requires a `DB` interface abstraction, but it would close the coverage gap without needing a full integration environment.

---

_Reviewed: 2026-06-29_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
