---
phase: 03-authentication-family-model
plan: "03"
subsystem: android-auth-ui
tags: [clerk-android, compose-navigation, auth, android, headless-sdk, compose]
dependency_graph:
  requires:
    - 03-01 (Go API Clerk middleware — CLERK_AUTHORIZED_PARTY env var consumer)
    - 03-02 (KMP shared HTTP auth layer — ClerkAuthRepository, HttpClientFactory)
  provides:
    - ClerkApp : Application (Clerk.initialize in Application subclass)
    - AppNavigation (auth-gated nav graph: signin/signup/postauth/orgpicker routes)
    - SignInScreen (email/password sign-in per UI-SPEC)
    - SignUpScreen (email/password sign-up per UI-SPEC)
    - PostAuthStub (role-gated placeholder; invite action for org:admin)
  affects:
    - androidApp/build.gradle.kts
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt
    - gradle/libs.versions.toml
tech_stack:
  added:
    - androidx.navigation:navigation-compose:2.8.3
    - androidx.compose.material:material-icons-extended (from compose-bom)
  patterns:
    - Clerk headless Android SDK email/password sign-in (signInWithPassword builder)
    - Clerk headless Android SDK account creation (signUp builder)
    - Clerk.auth.setActive(sessionId, organizationId) positional call (R8 strips named params)
    - ClerkResult.Success.value.data for paginated org memberships
    - WindowCompat.setDecorFitsSystemWindows(false) + Modifier.imePadding() for keyboard scrolling
    - FocusRequester for IME Next/Done field advancement
key_files:
  created:
    - androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt
    - androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt
    - androidApp/src/main/kotlin/com/onesteptwo/android/ui/PostAuthStub.kt
    - androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignInScreen.kt
    - androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignUpScreen.kt
  modified:
    - gradle/libs.versions.toml
    - androidApp/build.gradle.kts
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt
decisions:
  - navigation-compose:2.8.3 added as explicit dep (not in compose-bom)
  - androidx.browser forced to 1.8.0 — clerk-android-api:1.0.31 pulls in browser:1.10.0 which requires AGP 8.9.1; headless email/password auth does not use browser
  - setActive called positionally (setActive(sessionId, organizationId)) — R8 obfuscation strips Kotlin parameter names; named param call fails at compile time
  - Research pattern (result.data.data) incorrect — bytecode confirmed: ClerkResult.Success<T>.value gives T; for org memberships T=ClerkPaginatedResponse<OrganizationMembership>; access via result.value.data
  - azp value: PENDING — to be captured in Task 3 (checkpoint) via Logcat JWT inspection on device
metrics:
  duration: "~10 minutes"
  completed: "2026-06-27"
  tasks: 2 of 3 (Task 3 is checkpoint:human-verify — awaiting user action)
  files: 9
---

# Phase 03 Plan 03: Android Auth UI Summary

**One-liner:** Clerk Android SDK email/password sign-in/sign-up with auth-gated Compose navigation (navigation-compose:2.8.3), role-gated post-auth stub, and UI-SPEC-compliant screens using headless ClerkResult API with bytecode-verified method signatures.

## What Was Built

This plan delivered the Android authentication UI that connects the Clerk SDK (03-02) to the UI layer, implementing the full sign-in/sign-up path that ultimately drives JWT injection via the KMP shared HttpClient into the Go API (03-01).

### Task 1: Clerk SDK initialization plumbing and auth-gated navigation scaffold

**ClerkApp.kt** — `Application` subclass calling `Clerk.initialize(this, BuildConfig.CLERK_PUBLISHABLE_KEY)` in `onCreate()`. Registered via `android:name=".ClerkApp"` in `AndroidManifest.xml`. Pitfall 7 prevention: SDK must initialize in Application, not Activity.

**androidApp/build.gradle.kts** changes:
- Added `implementation(libs.clerk.android.api)` (com.clerk:clerk-android-api:1.0.31)
- Added `implementation(libs.navigation.compose)` (androidx.navigation:navigation-compose:2.8.3)
- Added `implementation(libs.compose.icons.extended)` (for show/hide password toggle icons)
- Enabled `buildFeatures { buildConfig = true }`
- Added `buildConfigField("String", "CLERK_PUBLISHABLE_KEY", ...)` sourced from `local.properties` or `CLERK_PUBLISHABLE_KEY` env var (not committed)
- Force `androidx.browser:1.8.0` to resolve AGP 8.7.3 / browser:1.10.0 incompatibility
- Added packaging exclusion for `META-INF/versions/9/OSGI-INF/**` (okhttp3 vs jspecify conflict)

**MainActivity.kt** — Updated to call `WindowCompat.setDecorFitsSystemWindows(window, false)` before `setContent { MaterialTheme { Surface { AppNavigation() } } }` (enables keyboard-aware scrolling via `imePadding()` on auth forms).

**AppNavigation.kt** — `@Composable fun AppNavigation()` with four routes:
- `signin` → `SignInScreen(onSignedIn, onNavigateToSignUp)`
- `signup` → `SignUpScreen(onSignedIn, onNavigateToSignIn)`
- `postauth` → `PostAuthStub(onInvite)`
- `orgpicker` → placeholder composable (full implementation in 03-04)

Auth-branching after sign-in/sign-up: calls `user.getOrganizationMemberships()`, then:
- 0 orgs → navigate to `postauth` (user not yet in a family; org creation is Phase 5)
- 1 org → `Clerk.auth.setActive(sessionId, orgId)` then navigate to `postauth`
- 2+ orgs → navigate to `orgpicker`

Back stack cleared on post-auth navigation (`popUpTo(0) { inclusive = true }`).

**PostAuthStub.kt** — Minimal placeholder. Reads `Clerk.organizationMembership?.role` to show "Invite" button for `org:admin` users; caregiver users see no invite action (UI-SPEC role gate).

### Task 2: Sign In and Sign Up screens per UI-SPEC

**SignInScreen.kt** — Full UI-SPEC implementation:
- Title "Sign in" (`typography.headlineMedium`)
- Email `OutlinedTextField` with `KeyboardType.Email`, autocap/autocorrect off, IME Next
- Password `OutlinedTextField` with `PasswordVisualTransformation`, IME Done submits
- 48dp show/hide password toggle (`Icons.Filled.Visibility`/`VisibilityOff`), `contentDescription` toggles "Show password"/"Hide password"
- Primary CTA "Sign in", disabled while any field empty; `CircularProgressIndicator` while loading
- Secondary link "Don't have an account? Create one" in `colorScheme.primary`
- Calls `Clerk.auth.signInWithPassword { identifier = email; password = password }`
- Error mapping above CTA (screen-level, Polite `liveRegion`): wrong credentials / network / locked

**SignUpScreen.kt** — Full UI-SPEC implementation:
- Title "Create account"
- Three fields: email, password, confirm password; each with IME Next/Done
- Both password fields have independent show/hide toggles
- Client-side validation: `< 8 chars` → field-level "Password must be at least 8 characters."; mismatch → field-level "Passwords don't match."
- Calls `Clerk.auth.signUp { email = email; password = password }`
- Server errors: email-taken → "An account with this email already exists. Sign in instead."; network → "Couldn't create your account."
- All colors via `MaterialTheme.colorScheme` slots — no hardcoded hex

### Task 3: PENDING — checkpoint:human-verify

The native-app `azp` claim value in Clerk JWTs is not known until a real sign-in occurs on an Android device/emulator. Task 3 requires:
1. Install debug APK and run through the sign-in flow
2. Capture the JWT from Logcat / Clerk dashboard session inspector
3. Decode the payload and read the `azp` claim (may be absent for native apps)
4. Set `CLERK_AUTHORIZED_PARTY` in Railway staging and production (or document absence)

**azp value: NOT YET CAPTURED** (pending Task 3 user action)

## Verified Clerk Android SDK API Surface (from bytecode inspection)

| Method | Package | Signature |
|--------|---------|-----------|
| `Clerk.initialize` | `com.clerk.api` | `initialize(Context, String)` |
| `Clerk.isSignedIn` | `com.clerk.api` | `Boolean` property (getter `isSignedIn()`) |
| `Clerk.user` | `com.clerk.api` | `User` (platform type, use `?.`) |
| `Clerk.session` | `com.clerk.api` | `Session` (platform type, use `?.`) |
| `Clerk.organizationMembership` | `com.clerk.api` | `OrganizationMembership?` |
| `Clerk.auth.signInWithPassword` | `com.clerk.api.auth` | `(SignInWithPasswordBuilder → Unit) → ClerkResult<SignIn, ClerkErrorResponse>` |
| `Clerk.auth.signUp` | `com.clerk.api.auth` | `(SignUpBuilder → Unit) → ClerkResult<SignUp, ClerkErrorResponse>` |
| `Clerk.auth.setActive` | `com.clerk.api.auth` | `(String, String) → ClerkResult<Session, ClerkErrorResponse>` (positional: sessionId, organizationId) |
| `User.getOrganizationMemberships()` | `com.clerk.api.user` (extension in `UserKt`) | `() → ClerkResult<ClerkPaginatedResponse<OrganizationMembership>, ClerkErrorResponse>` |
| `ClerkResult.Success.value` | `com.clerk.api.network.serialization` | `T` |
| `ClerkPaginatedResponse.data` | `com.clerk.api.network` | `List<T>` |
| `OrganizationMembership.role` | `com.clerk.api.organizations` | `String` (e.g., "org:admin") |
| `OrganizationMembership.organization.id` | `com.clerk.api.organizations` | `String` |

## Compose Navigation Version

`androidx.navigation:navigation-compose:2.8.3` — added to `gradle/libs.versions.toml` as `navigation = "2.8.3"` and `navigation-compose` library entry.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] androidx.browser:1.10.0 requires AGP 8.9.1 (project uses 8.7.3)**
- **Found during:** Task 1, first compileDebugKotlin attempt
- **Issue:** `clerk-android-api:1.0.31` transitively pulls in `androidx.browser:1.10.0` which declares `minAgpVersion=8.9.1` in its AAR metadata. Project uses AGP 8.7.3.
- **Fix:** Added `configurations.all { resolutionStrategy { force("androidx.browser:browser:1.8.0") } }` in `androidApp/build.gradle.kts`. The headless email/password auth path does not use the browser component (browser is for OAuth flows).
- **Files modified:** `androidApp/build.gradle.kts`
- **Commit:** 7b7f128

**2. [Rule 1 - Bug] okhttp3 vs jspecify META-INF resource conflict**
- **Found during:** Task 2, first assembleDebug attempt
- **Issue:** `okhttp3:logging-interceptor:5.4.0` and `org.jspecify:jspecify:1.0.0` both include `META-INF/versions/9/OSGI-INF/MANIFEST.MF`, causing `mergeDebugJavaResource` to fail.
- **Fix:** Added `packaging { resources.excludes += "META-INF/versions/9/OSGI-INF/**" }` to `androidApp { }` block.
- **Files modified:** `androidApp/build.gradle.kts`
- **Commit:** 38edc5a

**3. [Rule 1 - Bug] Research Pattern 9 `result.data.data` incorrect — correct is `result.value.data`**
- **Found during:** Task 1, AppNavigation.kt compilation
- **Issue:** RESEARCH.md Pattern 9 (LOW confidence, from staging docs) shows `result.data.data` to access org memberships list. Bytecode inspection confirms `ClerkResult.Success<T>` has `.value: T` (not `.data`), and `ClerkPaginatedResponse<T>` has `.data: List<T>`. The correct pattern is `result.value.data`.
- **Fix:** Used `result.value.data` in `navigateAfterAuth` with explicit cast `(result.value as? ClerkPaginatedResponse<OrganizationMembership>)?.data`.
- **Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt`
- **Commit:** 7b7f128

**4. [Rule 1 - Bug] `Clerk.auth.setActive(organizationId = orgId)` fails — positional call required**
- **Found during:** Task 1, AppNavigation.kt compilation
- **Issue:** R8 obfuscation strips Kotlin metadata from clerk-android-api JAR. Named parameter calls to `setActive` fail because the Kotlin compiler cannot resolve `organizationId` as a named param from the obfuscated bytecode. Error: "No value passed for parameter 'sessionId'".
- **Fix:** Used positional call `Clerk.auth.setActive(Clerk.session?.id ?: "", orgId)`. iOS research (Pattern 13) confirms the two-parameter ordering: `(sessionId, organizationId)`.
- **Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt`
- **Commit:** 7b7f128

**5. [Rule 2 - Auto-add] Created SignInScreen/SignUpScreen stubs in Task 1**
- **Found during:** Task 1, AppNavigation.kt compilation
- **Issue:** AppNavigation imports SignInScreen and SignUpScreen (Task 2 files). For `compileDebugKotlin` to pass in Task 1, minimal stubs are required.
- **Fix:** Created minimal stub composable functions in Task 1 commit; replaced with full UI-SPEC implementations in Task 2 commit.
- **Files modified:** `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignInScreen.kt`, `SignUpScreen.kt`
- **Commit:** 7b7f128 (stubs), 38edc5a (full implementation)

## Known Stubs

| Stub | File | Line | Reason |
|------|------|------|--------|
| PostAuthStub placeholder | `android/ui/PostAuthStub.kt` | 43 | Intentional — full 4-tab shell is Phase 5 per plan spec |
| orgpicker route placeholder | `android/navigation/AppNavigation.kt` | 89 | Intentional — OrgPickerScreen built in plan 03-04 |

These stubs do not block the plan's goal (Clerk auth scaffold) — they represent deferred Phase 5 and 03-04 work that is explicitly documented in the plan spec.

## Threat Model Coverage

| Threat | Disposition | Implementation |
|--------|-------------|----------------|
| T-3-02 Elevation of Privilege — azp validation | Pending | azp value must be captured in Task 3; CLERK_AUTHORIZED_PARTY set in Railway after capture |
| T-3-10 Information Disclosure — JWT logging | Not needed | No debug token logging added; JWT inspection via Clerk dashboard session inspector in Task 3 |
| T-3-11 Spoofing — uninitialized Clerk SDK | Mitigated | Clerk.initialize() called in ClerkApp.onCreate() (Application subclass, before any Activity) |
| T-3-12 Information Disclosure — publishable key | Accepted | Only pk_ key embedded in BuildConfig; sourced from local.properties/env, not committed |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt | FOUND |
| androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt | FOUND |
| androidApp/src/main/kotlin/com/onesteptwo/android/ui/PostAuthStub.kt | FOUND |
| androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignInScreen.kt | FOUND |
| androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignUpScreen.kt | FOUND |
| Commit 7b7f128 (Task 1) | FOUND |
| Commit 38edc5a (Task 2) | FOUND |
| androidApp/build/outputs/apk/debug/androidApp-debug.apk | FOUND |
