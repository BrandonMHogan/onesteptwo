---
phase: 03-authentication-family-model
verified: 2026-06-29T00:00:00Z
status: human_needed
score: 28/29 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Android end-to-end auth: install debug APK, create account, sign in, confirm landing on post-auth stub. Capture the native-app JWT from Logcat or Clerk Dashboard session inspector, base64-decode payload, read azp claim. If azp present, set CLERK_AUTHORIZED_PARTY in Railway staging + production. If absent, confirm the code comment already documents no-op behavior (REQ-026)."
    expected: "Sign-up and sign-in succeed; single-org user lands on PostAuthStub; CLERK_AUTHORIZED_PARTY is set in Railway or absence documented."
    why_human: "03-03 Task 3 (checkpoint:human-verify) was never approved per 03-03-SUMMARY.md — azp value is still listed as NOT YET CAPTURED."
  - test: "iOS end-to-end auth — VERIFIED via UAT 2026-06-29 (03-UAT.md, 7/7 tests passed)"
    expected: "All screens function correctly via ClerkKit; org switching reissues JWT; caregiver cannot see invite action."
    resolved: true
    resolved_at: "2026-06-29T23:50:00Z"
    resolved_by: "UAT session — 03-UAT.md"
---

# Phase 03: Authentication + Family Model Verification Report

**Phase Goal:** Implement Clerk JWT authentication across all three platforms (Go API, Android, iOS) with a family-model org layer — admins invite caregivers, multi-org users pick a family, role gates enforce org:admin vs org:caregiver access.
**Verified:** 2026-06-29
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | No session claims → 401 application/problem+json (all /v1 handlers) | VERIFIED | `clerk.SessionClaimsFromContext` + `!ok \|\| claims == nil` path in server.go lines 50, 164, 283 |
| 2 | Empty ActiveOrganizationID → 403 (all /v1 handlers) | VERIFIED | `claims.ActiveOrganizationID == ""` check in server.go lines 56, 170, 289 |
| 3 | org:caregiver role → 403 on POST /v1/children and DELETE /v1/children/{id} and DELETE /v1/account | VERIFIED | `!claims.HasRole("org:admin")` in server.go lines 61, 175, 294 |
| 4 | IDOR: admin in org A deleting child owned by org B → 403 (T-2-02 closed) | VERIFIED* | SELECT reads `clerk_org_id`; `claims.ActiveOrganizationID != childClerkOrgID` → 403 at server.go line 211. *Unit test exercises caregiver-gate path, not the DB-level ownership path — integration test noted as deferred |
| 5 | GET /healthz → 200 with no Authorization header | VERIFIED | `WithHeaderAuthorization` (not `RequireHeaderAuthorization`) wraps mux; /healthz bypasses auth by design |
| 6 | Go binary imports clerk-sdk-go/v2, not deprecated v1 | VERIFIED | go.mod line 6: `github.com/clerk/clerk-sdk-go/v2 v2.7.0`; `clerkinc` absent |
| 7 | Shared module exposes AuthRepository interface (Android + iOS) | VERIFIED | `interface AuthRepository` in AuthRepository.kt; Obj-C exported via SKIE for Swift |
| 8 | Shared HttpClient installs Ktor bearer provider with nonCancellableRefresh = true | VERIFIED | HttpClientFactory.kt line 48: `nonCancellableRefresh = true` |
| 9 | Bearer tokens sent only to OneStepTwo API hosts | VERIFIED | `sendWithoutRequest` allowlist in HttpClientFactory.kt lines 55-60: `onesteptwo.com`, staging host, localhost, 10.0.2.2 |
| 10 | Android ClerkAuthRepository retrieves token via Clerk Android SDK | VERIFIED | ClerkAuthRepository.kt: `Clerk.auth.getToken(GetTokenOptions(skipCache = false/true))` |
| 11 | :shared module compiles for Android target | VERIFIED | per 03-02-SUMMARY.md build confirmation |
| 12 | Unauthenticated Android app shows Sign In screen | VERIFIED | AppNavigation.kt routes to `signin` when no active session; ClerkApp.kt initializes SDK in Application.onCreate |
| 13 | Android user can sign in with email/password via Clerk SDK (runtime) | HUMAN NEEDED | Code: `Clerk.auth.signInWithPassword` in SignInScreen.kt line 90. 03-03 Task 3 (human-verify) not yet approved. |
| 14 | JWT injected into shared HttpClient requests to the API | VERIFIED | ClerkAuthRepository wires into `buildHttpClient`; bearer block calls `getToken()` |
| 15 | CLERK_AUTHORIZED_PARTY set to captured azp value or absence documented | HUMAN NEEDED | Code wired: `AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY"))` in main.go line 54. Per 03-03-SUMMARY: "azp value: NOT YET CAPTURED (pending Task 3 user action)." Env var may be empty in Railway. |
| 16 | 2+ org Android user sees org picker and can activate chosen org | VERIFIED | OrgPickerScreen.kt: "Choose a family", `Clerk.auth.setActive(sessionId, orgId)` at line 208; end-to-end device verified per 03-04-SUMMARY Task 3 approval |
| 17 | Activating org reissues JWT with org_id | VERIFIED | `setActive` call confirmed in OrgPickerScreen and AppNavigation; per 03-04 device verification |
| 18 | Admin can send caregiver invitation on Android | VERIFIED | InviteCaregiverScreen.kt: `org.createInvitation(emailAddress, role = "org:caregiver")` line 97-99; invitation email delivery confirmed per 03-04 Task 3 |
| 19 | Invite action reachable only for org:admin on Android | VERIFIED | Double-gated: PostAuthStub checks `Clerk.organizationMembership?.role == "org:admin"` before navigate; InviteCaregiverScreen composable pops back if not admin |
| 20 | Caregiver can sign in and reach app without invite action on Android | VERIFIED | per 03-04 Task 3 device verification |
| 21 | Xcode project exists linking shared KMP framework + ClerkKit SPM | VERIFIED | iosApp.xcodeproj/project.pbxproj has XCSwiftPackageProductDependency for ClerkKit/ClerkKitUI; ClerkAuthRepository.swift imports shared framework |
| 22 | iOS app configures Clerk with publishable key at launch | VERIFIED | iosAppApp.swift line 25: `Clerk.configure(publishableKey: key)` in `init()` reading Info.plist ClerkPublishableKey |
| 23 | Swift type implements shared AuthRepository protocol | VERIFIED | ClerkAuthRepository.swift: `final class ClerkAuthRepository: NSObject, @preconcurrency AuthRepository` |
| 24 | App routes by auth state in simulator | VERIFIED | ContentView.swift: no session → SignInView; active session → PostAuthRouter (OrgPickerView or stub); BUILD SUCCEEDED per 03-05-SUMMARY |
| 25 | iOS user can sign in with email/password via ClerkKit (runtime) | VERIFIED | UAT 2026-06-29 tests 1 & 2: email+password sign-in and needsClientTrust OTP flow both confirmed in Simulator. |
| 26 | 2+ org iOS user sees org picker and activates chosen family (runtime) | VERIFIED | UAT 2026-06-29 test 4: OrgPickerView shown for multi-org account; selecting family + Continue activates org. |
| 27 | Admin can send caregiver invitation from iOS (runtime) | VERIFIED | UAT 2026-06-29 test 5: InviteCaregiverView opened from post-auth stub; invite sent successfully. |
| 28 | Invite action hidden from caregivers on iOS | VERIFIED | ContentView.swift lines 60-62: `clerk.organizationMembership?.role == "org:admin"` gates InviteCaregiverView; confirmed code-level |
| 29 | Clerk custom roles org:admin / org:caregiver exist in Clerk Dashboard (dev) | VERIFIED | per 03-04-SUMMARY Task 1 approval; production instance roles deferred to user |

**Score: 25/29 truths verified** (4 require human runtime testing per pending checkpoints)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/cmd/server/main.go` | Clerk middleware wiring | VERIFIED | WithHeaderAuthorization + Leeway + authMiddleware(mux) |
| `backend/internal/api/server.go` | JWT claims auth in 3 handlers + IDOR | VERIFIED | SessionClaimsFromContext, ActiveOrganizationID, HasRole("org:admin"), clerk_org_id ownership check |
| `backend/internal/api/auth_test.go` | withFakeClaims + 8 auth tests | VERIFIED | withFakeClaims at line 22; 8 test functions present |
| `shared/src/commonMain/kotlin/com/onesteptwo/auth/AuthRepository.kt` | AuthRepository interface | VERIFIED | `interface AuthRepository` with getToken/refreshToken/isSignedIn |
| `shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt` | Ktor bearer + nonCancellableRefresh | VERIFIED | nonCancellableRefresh = true; sendWithoutRequest allowlist |
| `shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt` | Android Clerk-backed AuthRepository | VERIFIED | class ClerkAuthRepository : AuthRepository; skipCache=true for refresh |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt` | Application subclass with Clerk.initialize | VERIFIED | ClerkApp : Application; Clerk.initialize(this, BuildConfig.CLERK_PUBLISHABLE_KEY) |
| `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt` | Auth-gated nav graph | VERIFIED | signin/signup/postauth/orgpicker/invite routes; org:admin gate |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignInScreen.kt` | UI-SPEC sign-in screen | VERIFIED | "Sign in", "Create one", signInWithPassword, Show password toggle |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignUpScreen.kt` | UI-SPEC sign-up screen | VERIFIED | "Create account", client validation |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/OrgPickerScreen.kt` | Multi-org picker | VERIFIED | "Choose a family", setActive, getOrganizationMemberships |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/InviteCaregiverScreen.kt` | Admin invite screen | VERIFIED | createInvitation, role = "org:caregiver" |
| `iosApp/iosApp/iosAppApp.swift` | @main + Clerk.configure | VERIFIED | Clerk.configure(publishableKey:) in init() |
| `iosApp/iosApp/Auth/ClerkAuthRepository.swift` | Swift AuthRepository implementation | VERIFIED | @preconcurrency AuthRepository, getToken/refreshToken/isSignedIn, buildClient helper |
| `iosApp/iosApp/ContentView.swift` | Auth-state routing | VERIFIED | SignInView / OrgPickerView / PostAuthRouter; org:admin gate |
| `iosApp/iosApp/Auth/SignInView.swift` | iOS sign-in screen | VERIFIED | "Create one", signInWithPassword, "Show password" toggle |
| `iosApp/iosApp/Auth/SignUpView.swift` | iOS sign-up screen | VERIFIED | "Create account", client validation |
| `iosApp/iosApp/Auth/OrgPickerView.swift` | iOS org picker | VERIFIED | "Choose a family", setActive, getOrganizationMemberships |
| `iosApp/iosApp/Settings/InviteCaregiverView.swift` | iOS admin invite | VERIFIED | inviteMember role:"org:caregiver" |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `main.go` | clerk-sdk-go/v2/http | `WithHeaderAuthorization(AuthorizedPartyMatches, Leeway)(mux)` | WIRED | Lines 53-64 |
| `server.go` | clerk-sdk-go/v2 | `clerk.SessionClaimsFromContext(r.Context())` | WIRED | Lines 50, 164, 283 |
| `server.go` | `children.clerk_org_id` | `SELECT consent_event_id, clerk_org_id FROM children WHERE id = $1` | WIRED | Line 200, ownership check line 211 |
| `HttpClientFactory.kt` | `AuthRepository` | `loadTokens { authRepository.getToken() }` | WIRED | HttpClientFactory.kt line 35 |
| `ClerkAuthRepository.kt` | Clerk Android SDK | `Clerk.auth.getToken(GetTokenOptions(skipCache = ...))` | WIRED | ClerkAuthRepository.kt lines 30-31 |
| `AndroidManifest.xml` | ClerkApp | `android:name=".ClerkApp"` | WIRED | Manifest line 5 |
| `MainActivity.kt` | AppNavigation | `AppNavigation()` inside setContent | WIRED | per 03-03 SUMMARY |
| `AppNavigation.kt` | OrgPickerScreen | `orgpicker` route renders OrgPickerScreen | WIRED | AppNavigation.kt line 153 |
| `AppNavigation.kt` | InviteCaregiverScreen | `invite` route, double-gated org:admin | WIRED | AppNavigation.kt line 164-168 |
| `OrgPickerScreen.kt` | `Clerk.auth.setActive` | `Clerk.auth.setActive(sessionId, orgId)` positional | WIRED | OrgPickerScreen.kt line 208 |
| `InviteCaregiverScreen.kt` | `organization.createInvitation` | `org.createInvitation(emailAddress, role = "org:caregiver")` | WIRED | Line 97-99 |
| `iosAppApp.swift` | ClerkKit | `Clerk.configure(publishableKey:)` | WIRED | iosAppApp.swift line 25 |
| `ClerkAuthRepository.swift` | shared AuthRepository | `@preconcurrency AuthRepository` conformance | WIRED | ClerkAuthRepository.swift line 42 |
| `OrgPickerView.swift` | `clerk.auth.setActive` | `Clerk.shared.auth.setActive(sessionId:organizationId:)` | WIRED | OrgPickerView.swift line 173 |
| `InviteCaregiverView.swift` | `organization.inviteMember` | `organization.inviteMember(emailAddress:role:"org:caregiver")` | WIRED | InviteCaregiverView.swift line 144 |
| `ContentView.swift` | InviteCaregiverView | `clerk.organizationMembership?.role == "org:admin"` gate | WIRED | ContentView.swift lines 60-62, 97-100 |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| REQ-015 | 03-01, 03-03 | Org membership enforced; child.clerk_org_id checked in every handler | SATISFIED | server.go ownership check; AppNavigation uses active org from session |
| REQ-016 | 03-01, 03-03, 03-04, 03-05, 03-06 | org:admin full access; org:caregiver log-only; `org:` prefix in all checks | SATISFIED | `HasRole("org:admin")` in server.go; invite gate on both platforms |
| REQ-017 | 03-04, 03-06 | Admin invites caregiver via Clerk invitation API with org:caregiver role | SATISFIED | createInvitation (Android) + inviteMember (iOS) confirmed |
| REQ-018 | 03-04, 03-06 | Multi-org picker; selected org activated in JWT | SATISFIED | OrgPickerScreen + OrgPickerView + setActive wired on both platforms |
| REQ-019 | 03-02 | Ktor bearer auth with nonCancellableRefresh = true | SATISFIED | HttpClientFactory.kt line 48 |
| REQ-026 | 03-01, 03-03 | AuthorizedPartyMatches set for azp validation | PARTIAL | Code wired: `AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY"))`. Env var value NOT captured — 03-03 Task 3 not completed. Empty env var = no-op (documented in code comment). |
| REQ-027 | 03-01 | Handler-level ActiveOrganizationID check; 403 if empty | SATISFIED | server.go lines 56, 170, 289 |
| REQ-NF-006 | 03-02 | nonCancellableRefresh = true prevents KTOR-7852 | SATISFIED | HttpClientFactory.kt line 48 |
| REQ-NF-007 | 03-01 | JWK caching; automatic in Clerk SDK | SATISFIED | WithHeaderAuthorization handles JWK caching internally (1hr TTL, sync.RWMutex per SDK docs) |
| REQ-NF-010 | 03-01 | Clerk SDK v2 only; v1 absent | SATISFIED | go.mod: `clerk-sdk-go/v2 v2.7.0`; `clerkinc` absent |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `auth_test.go` line 184 | `TestDeleteV1ChildrenId_CrossOrgDelete_Returns403` exercises caregiver gate path, not DB-level IDOR ownership path — test comment acknowledges integration test needed | WARNING | The server.go IDOR ownership check (lines 200-211) is correctly implemented but has no direct unit test path. Integration test deferred. Not a code defect. |
| `ContentView.swift` | `Text("Signed in successfully")` / `Text("Welcome")` in PostAuthRouter | INFO | Intentional stub — full 4-tab app shell is Phase 5. Does not affect auth verification. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Go build with clerk-sdk-go/v2 | `cd backend && go build ./...` (per 03-01 SUMMARY) | EXIT 0 | PASS (SUMMARY confirmed) |
| Go test suite green under -race | `cd backend && go test ./... -count=1 -race` (per 03-01 SUMMARY) | 28 tests PASS | PASS (SUMMARY confirmed) |
| clerk-sdk-go/v2 in go.mod | `grep clerk/clerk-sdk-go/v2 go.mod` | Found line 6 | PASS |
| clerkinc absent from go.mod | `grep clerkinc go.mod` | Not found | PASS |
| WithHeaderAuthorization in main.go | `grep WithHeaderAuthorization cmd/server/main.go` | Found line 53 | PASS |
| nonCancellableRefresh in HttpClientFactory | `grep nonCancellableRefresh HttpClientFactory.kt` | Found line 48 | PASS |
| org:admin in server.go | `grep org:admin internal/api/server.go` | Found lines 61, 175, 294 | PASS |
| Android APK builds | `./gradlew :androidApp:assembleDebug` (per 03-03 SUMMARY) | debug APK found | PASS (SUMMARY confirmed) |
| iOS BUILD SUCCEEDED | xcodebuild (per 03-05/03-06 SUMMARY) | BUILD SUCCEEDED | PASS (SUMMARY confirmed) |

### Human Verification Required

#### 1. Android End-to-End Auth + azp Capture (REQ-026)

**Test:** Install the debug APK (`./gradlew :androidApp:installDebug`). Launch the app — confirm it opens on the Sign In screen. Tap "Create one", create a test account, confirm user appears in Clerk dev dashboard. Sign in, confirm landing on post-auth stub (single org). Capture the session JWT from Logcat or Clerk Dashboard session inspector, base64-decode the payload, and read the `azp` claim. If `azp` is present, set `CLERK_AUTHORIZED_PARTY` to that value in Railway staging AND production. If absent, confirm the code comment in main.go already documents the no-op behavior.
**Expected:** Sign-up and sign-in succeed; single-org user lands on PostAuthStub; CLERK_AUTHORIZED_PARTY is resolved in Railway (or absence formally documented).
**Why human:** 03-03 Task 3 (checkpoint:human-verify) was never approved — 03-03-SUMMARY explicitly states "azp value: NOT YET CAPTURED (pending Task 3 user action)." Without this, REQ-026 (azp validation) is wired in code but potentially inactive in production.

#### 2. iOS End-to-End Auth Flow in Simulator

**Test:** Run the iOS app in the Simulator. Confirm it opens on SignInView. Tap "Create one" (NavigationLink), create a test account. Sign in — with a single org, confirm post-auth stub. With a multi-org user, confirm OrgPickerView ("Choose a family") appears, lists families, and selecting + Continue activates the org. As an admin, navigate to InviteCaregiverView, send to a caregiver email, confirm "Invitation sent". Sign in as a caregiver; confirm the InviteCaregiverView is NOT accessible (role gate).
**Expected:** All iOS auth screens function per UI-SPEC; org switching reissues JWT; caregiver cannot reach invite screen.
**Why human:** 03-06 Task 3 (checkpoint:human-verify) was not approved — 03-06-SUMMARY ends "Task 3 checkpoint pending." No runtime verification of iOS ClerkKit sign-in/sign-up/invite flows has been performed.

---

## Gaps Summary

No blocking gaps — all code artifacts exist and are substantively implemented and wired. Two human verification checkpoints remain from the execution phase:

1. **03-03 Task 3 (Android e2e + azp):** The checkpoint:human-verify was never approved. The azp capture step (REQ-026) is the most consequential item — without it, `CLERK_AUTHORIZED_PARTY` may be unset in Railway, leaving the azp validation as a no-op in production.

2. **03-06 Task 3 (iOS e2e):** The checkpoint:human-verify was not approved before the SUMMARY was filed. iOS runtime auth behavior is unverified.

Both items have correct code backing them. The phase goal is code-complete; it needs runtime verification to be fully confirmed.

---

_Verified: 2026-06-29_
_Verifier: Claude (gsd-verifier)_
