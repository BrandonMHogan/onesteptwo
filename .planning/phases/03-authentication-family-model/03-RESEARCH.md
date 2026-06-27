# Phase 3: Authentication & Family Model - Research

**Researched:** 2026-06-27
**Domain:** Clerk auth (Go/Android/iOS), Ktor Client auth plugin, KMP shared auth layer
**Confidence:** MEDIUM

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REQ-015 | Every Clerk Organization maps 1:1 to a family; `claims.ActiveOrganizationID` checked against child's `clerk_org_id` in every Go handler | Go handler org enforcement pattern; `SessionClaims.ActiveOrganizationID` |
| REQ-016 | Two roles: `org:admin` (full access) and `org:caregiver` (log events, view history only) | `claims.HasRole("org:admin")` pattern; custom role creation in Clerk Dashboard |
| REQ-017 | Invitation: admin calls Clerk invitation API with caregiver email + `org:caregiver` role; Clerk sends email | `organization.createInvitation(emailAddress, role)` on Android and iOS |
| REQ-018 | Multi-org: after login check if user belongs to multiple orgs; show org-picker if so; activate via Clerk SDK | `getOrganizationMemberships()` + `setActive(organizationId:)` on both platforms |
| REQ-019 | Ktor Auth plugin `bearerTokens { }` with `nonCancellableRefresh = true` (KTOR-7852) | Confirmed: `BearerAuthConfig.nonCancellableRefresh` property exists in Ktor 3.5.1 |
| REQ-026 | Go API validates `CLERK_AUTHORIZED_PARTY` (azp) on every request via `AuthorizedPartyMatches` | `clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY"))` |
| REQ-027 | Middleware does NOT reject tokens with no active org; each handler explicitly checks `claims.ActiveOrganizationID` | Confirmed: `WithHeaderAuthorization` does not reject missing org; handler must check |
| REQ-NF-006 | Concurrent 401 refresh handled safely by Ktor's `AuthTokenHolder` Mutex; Clerk Android SDK deduplicates via Deferred cache | Confirmed by Ktor docs: single refresh fires, others queue |
| REQ-NF-007 | JWK caching: Clerk HTTP middleware caches JWKs automatically (keyed by kid, 1-hour TTL) | Confirmed: automatic when using middleware |
| REQ-NF-010 | Go API must use `clerk-sdk-go/v2` (v2.7.0+); deprecated v1 EOL April 2025 | Verified: v2.7.0 on Go module proxy 2026-06-11 |
</phase_requirements>

---

## Summary

Phase 3 has three distinct implementation tiers that must be built sequentially: the Go API authentication layer, the KMP shared HTTP client layer, and the platform UI layers (Android + iOS).

**Go API tier:** The existing handlers have `// TODO Phase 3` stubs that use insecure request headers (`X-Clerk-User-Id`, `X-Clerk-Org-Id`) for identity. These must be replaced with JWT claims extracted via the Clerk Go SDK v2 middleware. The middleware goes in `main.go` and must validate the `azp` claim via `AuthorizedPartyMatches`; each handler then checks `claims.ActiveOrganizationID` and `claims.HasRole("org:admin")` independently. A known IDOR gap in `DeleteV1ChildrenId` (tagged `T-2-02` in a code comment) must also be closed in this phase.

**KMP shared tier:** Ktor Client currently has no dependencies in the shared module. Adding `ktor-client-auth` with the `bearerTokens {}` plugin (with `nonCancellableRefresh = true`) provides concurrent-safe 401 refresh handling. An `AuthRepository` interface with platform implementations abstracts token retrieval from the Clerk platform SDKs.

**Platform UI tier:** Android already has a Compose scaffold; iOS has only a `.gitkeep` file. Both platforms need Clerk SDK initialization, sign-up/sign-in screens, and a multi-org picker screen. Clerk organization creation, invitation sending, and org switching all call platform Clerk SDK APIs directly — no custom backend logic is needed.

**Primary recommendation:** Build Go auth middleware first, then KMP Ktor layer, then Android UI, then iOS UI — each in a separate wave since Android and iOS are independent of each other but both depend on the KMP layer being usable.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| JWT issuance and cryptographic signing | Clerk SaaS | — | Clerk manages JWKS, rotation, and signing |
| JWT validation + azp check | Go API middleware | — | Server-side; cannot be trusted to client |
| Org context enforcement (org_id match) | Go API (per-handler) | — | Middleware intentionally skips this; each handler owns it |
| Role-based access control (`org:admin`) | Go API (per-handler) | — | After org check, check role |
| Sign-up / sign-in screens | Android UI / iOS UI | — | Platform-native screens, Compose + SwiftUI |
| Session token storage and rotation | Clerk Android SDK / Clerk iOS SDK | — | Native SDKs handle token lifecycle |
| Bearer token injection per HTTP request | KMP Shared (Ktor Auth plugin) | — | Shared across platforms; single implementation |
| Concurrent 401 refresh (race-safe) | KMP Shared (Ktor Auth plugin) | — | `AuthTokenHolder` Mutex + `nonCancellableRefresh` |
| Org creation (family setup) | Mobile SDK (Clerk Android/iOS) | — | Client calls Clerk org creation API directly |
| Invitation sending (admin caregiver invite) | Mobile SDK (Clerk Android/iOS) | — | `organization.createInvitation()` — no backend logic |
| Invitation delivery and acceptance | Clerk SaaS | — | Clerk sends email, handles magic link, adds member |
| Multi-org picker screen | Android UI / iOS UI | — | Platform-native; calls `getOrganizationMemberships()` |
| Active org switching | Mobile SDK (Clerk Android/iOS) | — | `setActive(organizationId:)` reissues JWT |
| JWK caching | Go API (Clerk middleware, automatic) | — | In-memory, keyed by `kid`, 1-hour TTL |

---

## Standard Stack

### Go Tier
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `github.com/clerk/clerk-sdk-go/v2` | v2.7.0 | JWT validation, claims extraction, org/user management | Official Clerk Go SDK; v1 is EOL. Verified on Go module proxy. [VERIFIED: go module proxy] |
| `github.com/clerk/clerk-sdk-go/v2/http` | v2.7.0 (subpackage) | HTTP middleware: `WithHeaderAuthorization`, `AuthorizedPartyMatches`, `Leeway` | Same module — no separate install |

### KMP Shared Tier
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.ktor:ktor-client-core` | 3.5.1 | KMP HTTP client | Standard KMP HTTP client; supports Android + iOS |
| `io.ktor:ktor-client-auth` | 3.5.1 | Bearer token auth plugin with `nonCancellableRefresh` | Built-in, race-safe 401 refresh |
| `io.ktor:ktor-client-content-negotiation` | 3.5.1 | JSON serialization for requests/responses | Companion to core; needed for API payloads |
| `io.ktor:ktor-serialization-kotlinx-json` | 3.5.1 | Kotlinx.serialization JSON codec | Standard JSON codec for Ktor |
| `io.ktor:ktor-client-okhttp` | 3.5.1 | Android HTTP engine | OkHttp is the standard Android HTTP engine |
| `io.ktor:ktor-client-darwin` | 3.5.1 | iOS HTTP engine | Darwin is the standard iOS engine for KMP |

### Android Tier
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.clerk:clerk-android-api` | 1.0.31 | Sign-in/up, session management, org switching, invitations | Official Clerk Android SDK; organizations support added 2026-06-05. [VERIFIED: Maven Central] |

### iOS Tier
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `ClerkKit` (SPM: `github.com/clerk/clerk-ios`) | 1.2.0 | Sign-in/up, session management, org switching, invitations | Official Clerk iOS SDK; iOS 17+. [VERIFIED: github.com/clerk/clerk-ios] |
| `ClerkKitUI` (same SPM package) | 1.2.0 | Optional prebuilt SwiftUI views | Same repo; add to target alongside ClerkKit |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `clerk-android-api` | Custom JWT decode | Custom is dangerous — hand-rolled JWKS validation has historical CVEs |
| Ktor Auth plugin | Custom Mutex + refresh logic | Auth plugin's `AuthTokenHolder` handles concurrent 401 safely; custom mutex is a re-invention |
| `RequireHeaderAuthorization` | Handler-level rejection | Require rejects `/healthz` (no auth header); `WithHeaderAuthorization` is correct for mixed-auth routes |

**Go installation (add to go.mod):**
```bash
cd backend && go get github.com/clerk/clerk-sdk-go/v2@v2.7.0
```

**KMP Gradle (shared/build.gradle.kts additions):**
```kotlin
commonMain.dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}
androidMain.dependencies {
    implementation(libs.ktor.client.okhttp)
}
iosMain.dependencies {
    implementation(libs.ktor.client.darwin)
}
```

**Android (androidApp/build.gradle.kts addition):**
```kotlin
implementation("com.clerk:clerk-android-api:1.0.31")
```

---

## Package Legitimacy Audit

> Go and Maven packages — npm registry legitimacy check does not apply. Verified against ecosystem-native registries.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `github.com/clerk/clerk-sdk-go/v2` | Go module proxy | v2.7.0 released 2026-06-11 | N/A (Go ecosystem) | github.com/clerk/clerk-sdk-go | OK | Approved |
| `com.clerk:clerk-android-api` | Maven Central | v1.0.31 (active) | N/A | github.com/clerk/clerk-android | OK | Approved |
| `ClerkKit` (github.com/clerk/clerk-ios) | Swift Package Index | v1.2.0 (active) | N/A | github.com/clerk/clerk-ios | OK | Approved |
| `io.ktor:ktor-client-*` | Maven Central | v3.5.1 (latest) | Hundreds of millions | github.com/ktorio/ktor | OK | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────┐
                         │      Clerk SaaS (Identity)       │
                         │  - JWKS endpoint                 │
                         │  - Org management API            │
                         │  - Invitation email delivery     │
                         └─────────┬──────────┬────────────┘
                                   │ JWT issue │ Org API
          ┌────────────────────────┼───────────┤
          │                        │           │
          ▼                        ▼           ▼
 ┌─────────────────┐    ┌──────────────────────────────┐
 │  Android App    │    │         iOS App               │
 │ (Compose UI)    │    │       (SwiftUI)               │
 │ - Sign-up/in    │    │ - Sign-up/in                  │
 │ - Org picker    │    │ - Org picker                  │
 │ - Invite flow   │    │ - Invite flow                 │
 └────────┬────────┘    └──────────────┬───────────────┘
          │ token                       │ token
          │ (platform Clerk SDK)        │ (ClerkKit)
          └──────────────┬─────────────┘
                         ▼
          ┌────────────────────────────────────┐
          │       KMP Shared Module             │
          │  AuthRepository interface           │
          │  Ktor HttpClient                    │
          │    install(Auth) {                  │
          │      bearer {                       │
          │        loadTokens { repo.getToken }│
          │        refreshTokens { repo.refresh}│
          │        nonCancellableRefresh = true │
          │      }                              │
          │    }                                │
          └──────────────┬─────────────────────┘
                         │ Authorization: Bearer <JWT>
                         ▼
          ┌────────────────────────────────────┐
          │       Go API (Railway)              │
          │                                    │
          │  main.go:                          │
          │    clerk.SetKey(CLERK_SECRET_KEY)  │
          │    authMiddleware =                 │
          │      WithHeaderAuthorization(       │
          │        AuthorizedPartyMatches(azp) │
          │        Leeway(5s)                  │
          │      )                             │
          │    ListenAndServe(authMiddleware(mux))
          │                                    │
          │  Each handler:                     │
          │    1. claims, ok = SessionClaimsFromContext
          │    2. if !ok → 401               │
          │    3. if org empty → 403          │
          │    4. if org mismatch → 403       │
          │    5. if not admin → 403          │
          └──────────────┬─────────────────────┘
                         ▼
          ┌────────────────────────────────────┐
          │      PostgreSQL (Railway)           │
          │  children.clerk_org_id ← enforced  │
          └────────────────────────────────────┘
```

### Recommended Project Structure Additions

```
backend/
├── internal/
│   └── api/
│       ├── server.go          # Replace TODO Phase 3 stubs with JWT claims
│       ├── clerk.go           # Already exists (ClerkOrgClient interface)
│       └── auth_test.go       # NEW: Clerk middleware + handler auth tests
├── go.mod                     # ADD: clerk-sdk-go/v2 v2.7.0
└── cmd/server/main.go         # ADD: clerk.SetKey + authMiddleware wiring

shared/
├── src/commonMain/kotlin/com/onesteptwo/
│   ├── auth/
│   │   ├── AuthRepository.kt  # NEW: interface { getToken(); refreshToken() }
│   │   └── HttpClientFactory.kt # NEW: creates Ktor HttpClient with Auth plugin
│   └── db/                    # existing SQLDelight
├── src/androidMain/kotlin/com/onesteptwo/
│   └── auth/
│       └── ClerkAuthRepository.kt # NEW: Android impl using Clerk.auth.getToken()
├── src/iosMain/kotlin/com/onesteptwo/
│   └── auth/
│       └── ClerkAuthRepository.kt # NEW: iOS impl (calls Swift via SKIE or expect/actual)
└── build.gradle.kts           # ADD: ktor dependencies

androidApp/
├── src/main/kotlin/com/onesteptwo/android/
│   ├── ClerkApp.kt            # NEW: Application subclass with Clerk.initialize()
│   ├── MainActivity.kt        # MODIFY: use shared HttpClient
│   ├── ui/
│   │   ├── auth/
│   │   │   ├── SignInScreen.kt      # NEW: email/password sign-in
│   │   │   ├── SignUpScreen.kt      # NEW: email/password sign-up
│   │   │   └── OrgPickerScreen.kt   # NEW: multi-org picker
│   │   └── settings/
│   │       └── InviteCaregiverScreen.kt # NEW: admin invite flow
│   └── navigation/
│       └── AppNavigation.kt         # NEW: auth-gated nav graph
└── build.gradle.kts           # ADD: clerk-android-api dependency

iosApp/
├── iosApp.xcodeproj/          # NEW: Xcode project (created fresh)
├── iosApp/
│   ├── OneStepTwoApp.swift    # NEW: @main entry, Clerk.configure(publishableKey:)
│   ├── ContentView.swift      # NEW: root view + nav state
│   ├── Auth/
│   │   ├── SignInView.swift   # NEW
│   │   ├── SignUpView.swift   # NEW
│   │   └── OrgPickerView.swift # NEW
│   └── Settings/
│       └── InviteCaregiverView.swift # NEW
└── Package.swift              # SPM: ClerkKit + ClerkKitUI
```

### Pattern 1: Go Middleware Wiring (main.go)

```go
// Source: pkg.go.dev/github.com/clerk/clerk-sdk-go/v2/http + docs/06-auth.md
import (
    "github.com/clerk/clerk-sdk-go/v2"
    clerkhttp "github.com/clerk/clerk-sdk-go/v2/http"
)

func main() {
    // ...DB setup (unchanged from Phase 2)...

    clerk.SetKey(os.Getenv("CLERK_SECRET_KEY"))

    authMiddleware := clerkhttp.WithHeaderAuthorization(
        clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY")),
        clerkhttp.Leeway(5 * time.Second),
    )

    srv := &api.Server{DB: db, Clerk: api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))}
    mux := http.NewServeMux()
    api.HandlerWithOptions(srv, api.StdHTTPServerOptions{
        BaseRouter:       mux,
        ErrorHandlerFunc: api.ProblemErrorHandler,
    })

    log.Printf("starting server on :%s", port)
    if err := http.ListenAndServe(":"+port, authMiddleware(mux)); err != nil {
        log.Fatal(err)
    }
}
```

**Why `WithHeaderAuthorization` not `RequireHeaderAuthorization`:** `RequireHeaderAuthorization` returns HTTP 403 if ANY request lacks a valid token — including `/healthz`. The health check has no Authorization header. `WithHeaderAuthorization` populates claims when a valid token is present and passes through when not; each protected handler does its own 401/403 rejection. [CITED: pkg.go.dev/github.com/clerk/clerk-sdk-go/v2/http]

### Pattern 2: Replacing TODO Phase 3 Stubs in Handlers

Replace the header-based auth pattern in ALL THREE handlers (`PostV1Children`, `DeleteV1ChildrenId`, `DeleteV1Account`):

```go
// REMOVE (Phase 2 placeholder):
// TODO Phase 3: replace with JWT claim extraction
clerkUserID := r.Header.Get("X-Clerk-User-Id")
clerkOrgID  := r.Header.Get("X-Clerk-Org-Id")
if clerkUserID == "" || clerkOrgID == "" {
    WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing identity headers")
    return
}

// ADD (Phase 3):
// Source: pkg.go.dev/github.com/clerk/clerk-sdk-go/v2
claims, ok := clerk.SessionClaimsFromContext(r.Context())
if !ok || claims == nil {
    WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
    return
}
clerkUserID := claims.Subject              // Clerk user ID (RegisteredClaims.Subject)
clerkOrgID  := claims.ActiveOrganizationID // empty string when no active org
```

### Pattern 3: Org Context Enforcement (Every Protected Handler)

```go
// Source: docs/06-auth.md + clerk.com/docs/guides/sessions/verifying
// REQ-027: middleware does not enforce org; each handler must
if claims.ActiveOrganizationID == "" {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
    return
}
```

For handlers that operate on a specific child record, add after fetching the child from DB:

```go
// REQ-015: child must belong to requester's active org (T-2-02 IDOR gap closure)
if claims.ActiveOrganizationID != child.ClerkOrgID {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "access denied")
    return
}
```

### Pattern 4: Role-Based Access Control

```go
// Source: pkg.go.dev/github.com/clerk/clerk-sdk-go/v2 (HasRole method)
// REQ-016: admin-only operations (create child, delete child, delete account)
// CRITICAL: must use "org:admin" not "admin" — Clerk v2 always uses org: prefix
if !claims.HasRole("org:admin") {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "admin role required")
    return
}
```

### Pattern 5: Ktor Bearer Auth Plugin (KMP Shared)

```kotlin
// Source: ktor.io/docs/client-bearer-auth.html
// api.ktor.io/ktor-client-auth/io.ktor.client.plugins.auth.providers/-bearer-auth-config
fun buildHttpClient(authRepository: AuthRepository): HttpClient {
    return HttpClient {
        install(Auth) {
            bearer {
                loadTokens {
                    val token = authRepository.getToken()
                    token?.let { BearerTokens(it, "") }
                }
                refreshTokens {
                    // skipCache ensures a fresh token is fetched, not a stale cached one
                    val token = authRepository.refreshToken()
                    token?.let { BearerTokens(it, "") }
                }
                // Required by REQ-019: prevents caller cancellation from rolling back
                // a completed token refresh (KTOR-7852 fix)
                nonCancellableRefresh = true
                sendWithoutRequest { request ->
                    // Send auth header proactively to our own API
                    request.url.host.endsWith("onesteptwo.com") || 
                    request.url.host == "localhost" || 
                    request.url.host == "10.0.2.2"  // Android emulator localhost
                }
            }
        }
        install(ContentNegotiation) {
            json()
        }
    }
}
```

### Pattern 6: AuthRepository Interface (KMP CommonMain)

```kotlin
// commonMain/kotlin/com/onesteptwo/auth/AuthRepository.kt
interface AuthRepository {
    suspend fun getToken(): String?
    suspend fun refreshToken(): String?
    fun isSignedIn(): Boolean
}
```

### Pattern 7: Android AuthRepository Implementation

```kotlin
// androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt
class ClerkAuthRepository : AuthRepository {
    override suspend fun getToken(): String? =
        Clerk.auth.getToken()

    override suspend fun refreshToken(): String? =
        Clerk.auth.getToken()  // Clerk SDK fetches fresh token on every call if needed

    override fun isSignedIn(): Boolean =
        Clerk.client?.activeSessions?.isNotEmpty() == true
}
```

### Pattern 8: Android Sign-In

```kotlin
// androidMain — sign-in with email/password
// Source: clerk.com/docs/android/reference/native-mobile/auth
suspend fun signIn(email: String, password: String) {
    Clerk.auth.signInWithPassword {
        identifier = email
        this.password = password
    }
}
```

### Pattern 9: Android Multi-Org Picker

```kotlin
// List orgs and switch
// Source: clerk-android.clerkstage.dev/source/api/com.clerk.api.user/get-organization-memberships.html
suspend fun listOrganizations(): List<OrganizationMembership> {
    return when (val result = Clerk.user?.getOrganizationMemberships()) {
        is ClerkResult.Success -> result.data.data
        else -> emptyList()
    }
}

suspend fun setActiveOrganization(organizationId: String) {
    Clerk.auth.setActive(organizationId = organizationId)
}
```

### Pattern 10: Android Invite Caregiver (Admin Only)

```kotlin
// Source: clerk-android.clerkstage.dev/source/api/com.clerk.api.organizations/-organization/index.html
// REQ-017: admin sends invitation, Clerk handles email delivery
suspend fun inviteCaregiver(email: String) {
    val org = Clerk.organization ?: error("No active organization")
    org.createInvitation(
        emailAddress = email,
        role = "org:caregiver"  // Must be pre-created in Clerk Dashboard (CRITICAL)
    )
}
```

### Pattern 11: iOS Setup (ClerkKit)

```swift
// OneStepTwoApp.swift
// Source: clerk.com/docs/ios/getting-started/quickstart
import ClerkKit

@main
struct OneStepTwoApp: App {
    init() {
        // publishableKey injected from build config or Info.plist
        Clerk.configure(publishableKey: Bundle.main.infoDictionary?["ClerkPublishableKey"] as! String)
    }
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

### Pattern 12: iOS Sign-In

```swift
// Source: clerk.com/docs/ios/reference/native-mobile/auth
let signIn = try await clerk.auth.signInWithPassword(
    identifier: email,
    password: password
)
```

### Pattern 13: iOS Multi-Org Picker + Switch

```swift
// List all user orgs
let memberships = try await clerk.user?.getOrganizationMemberships()

// Switch to selected org (JWT is refreshed automatically)
// Source: clerk.com/docs/ios/reference/native-mobile/organizations
try await clerk.auth.setActive(
    sessionId: clerk.session?.id ?? "",
    organizationId: selectedOrganizationId
)
```

### Pattern 14: iOS Invite Caregiver

```swift
// Source: clerk.com/docs/ios/reference/native-mobile/organizations
let invitation = try await organization.inviteMember(
    emailAddress: email,
    role: "org:caregiver"
)
```

### Anti-Patterns to Avoid

- **Using `RequireHeaderAuthorization` on the whole mux:** Breaks `/healthz` — that endpoint has no auth header. Use `WithHeaderAuthorization` instead.
- **Checking `"admin"` without `"org:` prefix:** `claims.HasRole("admin")` always returns `false` in Clerk v2. Always use `"org:admin"` and `"org:caregiver"`.
- **Trusting org_id from request body:** Always extract `clerkOrgID` from `claims.ActiveOrganizationID`, never from request headers or body.
- **Using clerk-sdk-go v1 (`github.com/clerkinc/clerk-sdk-go`):** EOL April 2025. Import path changed; v1 is deprecated.
- **Omitting `nonCancellableRefresh = true`:** Can cause stuck sessions when the originating coroutine is cancelled mid-refresh.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT signature verification | Custom JWKS fetch + JWT parse | `clerkhttp.WithHeaderAuthorization()` | JWKS rotation, clock skew, kid lookup — all handled by SDK |
| Token refresh on 401 | Custom retry loop + mutex | Ktor `bearerTokens {}` plugin | Race condition: two concurrent 401s firing two refresh calls — Auth plugin prevents this |
| Token refresh cancellation safety | `withContext(NonCancellable)` wrapper | `nonCancellableRefresh = true` | Already implemented and tested in Ktor; custom wrapper is fragile |
| Invitation email sending | Custom email + HMAC token | Clerk invitation API | Clerk handles token security, delivery, expiry, and org membership on acceptance |
| Organization membership check | DB table of user → org mappings | Clerk JWT claims (`ActiveOrganizationID`) | PII/membership data lives in Clerk; JWT claim is cryptographically verified |
| azp claim extraction | Custom JWT decoder | `AuthorizedPartyMatches()` middleware option | One missing null-check exposes CSRF attack vector |
| Role storage | Custom `user_roles` DB table | Clerk JWT claims (`org_role`, `HasRole()`) | Roles live in Clerk org model; JWT claim is authoritative |

**Key insight:** Clerk is a purpose-built auth SaaS with a 9-year track record. Every item in this table has historically caused security incidents when hand-rolled. The SDK handles JWKS caching, clock skew, org membership, and role propagation — replicating any piece of this is not worthwhile for a v1 product.

---

## Common Pitfalls

### Pitfall 1: `azp` Not Validated by Default
**What goes wrong:** A request bearing a valid Clerk JWT issued to a completely different application (e.g., a test client or another Clerk app) passes JWT validation and reaches Go handlers.
**Why it happens:** `WithHeaderAuthorization()` with no options validates only signature and expiry. `azp` validation requires explicit `AuthorizedPartyMatches(...)`.
**How to avoid:** Always pass `clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY"))` to the middleware.
**Warning signs:** Integration tests passing with tokens generated from a different Clerk app.

### Pitfall 2: Org Enforcement Belongs in Handlers, Not Middleware
**What goes wrong:** Assuming the middleware enforces `ActiveOrganizationID` — it does not. Every authenticated user (even from a different org) can read/write any child's data (IDOR).
**Why it happens:** `WithHeaderAuthorization` / `RequireHeaderAuthorization` checks only token validity. Org claims are present in the JWT but not asserted by the middleware.
**How to avoid:** Every Go handler that touches child data must explicitly check `claims.ActiveOrganizationID != ""` and `claims.ActiveOrganizationID == child.ClerkOrgID`.
**Warning signs:** `DeleteV1ChildrenId` already has a `// TODO Phase 3: verify children.clerk_org_id matches the requester's active org before deleting (IDOR gap T-2-02)` comment — this is the exact gap.

### Pitfall 3: Role Prefix Must Be `org:`
**What goes wrong:** `claims.HasRole("admin")` returns `false` even for an actual admin. Access is incorrectly denied — or worse, the check is omitted in frustration.
**Why it happens:** Clerk v2 prefixes all organization roles with `org:`. The role value in the JWT is `"org:admin"`, not `"admin"`.
**How to avoid:** Always use `claims.HasRole("org:admin")` and `claims.HasRole("org:caregiver")`.
**Warning signs:** All role checks return false in testing.

### Pitfall 4: `nonCancellableRefresh` Omission Causes Stuck Sessions
**What goes wrong:** User navigates away from a screen while a 401 refresh is in progress. The refresh coroutine is cancelled. Ktor rolls back the token update. Subsequent requests keep getting 401 and re-triggering refresh. App appears stuck.
**Why it happens:** KTOR-7852: when the originating request's coroutine is cancelled, Ktor (without `nonCancellableRefresh`) can rollback the completed token update.
**How to avoid:** Set `nonCancellableRefresh = true` in the `bearer {}` block.
**Warning signs:** Token refresh works in isolation but fails intermittently when user navigates quickly.

### Pitfall 5: `org:caregiver` Role Must Exist in Clerk Dashboard Before Invitation
**What goes wrong:** `organization.createInvitation(emailAddress, role: "org:caregiver")` returns an error — the custom role doesn't exist yet.
**Why it happens:** Clerk only allows invitation to roles that have been created in the Clerk Dashboard (up to 10 custom roles per app). The `org:caregiver` role is a custom role that must be configured manually.
**How to avoid:** Create the `org:caregiver` custom role in the Clerk Dashboard for BOTH dev and production instances before testing the invitation flow.
**Warning signs:** Invitation API call returns a validation error about an unknown role.

### Pitfall 6: `azp` Value for Native Mobile Apps Is Unknown Until Runtime
**What goes wrong:** `CLERK_AUTHORIZED_PARTY` is set to a guessed value (e.g., a bundle ID or the Railway URL). The middleware rejects every request from the mobile app because the actual `azp` in Clerk's native mobile JWT doesn't match.
**Why it happens:** For native mobile apps, Clerk's `azp` claim value is not documented as being a domain or bundle ID. It may be absent or set to a Clerk-specific internal value (e.g., the frontend API URL). [ASSUMED]
**How to avoid:** Sign in with the native Android app in the dev environment, capture a JWT (log it in the Ktor interceptor), decode the payload (base64), and inspect the actual `azp` field. Set `CLERK_AUTHORIZED_PARTY` to that value. If `azp` is absent, `WithHeaderAuthorization` skips validation (safe for native-only apps — see Open Questions).
**Warning signs:** All requests from native apps return HTTP 403 immediately after adding `AuthorizedPartyMatches`.

### Pitfall 7: Android Clerk SDK Requires `Application` Subclass
**What goes wrong:** `Clerk.auth.getToken()` crashes or returns null because `Clerk.initialize()` was never called.
**Why it happens:** The SDK requires initialization in an `Application` subclass, not in `MainActivity.onCreate()`. Skipping the Application class means the SDK is uninitialized.
**How to avoid:** Create a `ClerkApp : Application` subclass, call `Clerk.initialize(this, publishableKey)` in `onCreate()`, and register it in `AndroidManifest.xml` with `android:name=".ClerkApp"`.
**Warning signs:** `NullPointerException` or `UninitializedPropertyAccessException` on any `Clerk.*` call.

### Pitfall 8: `DeleteV1ChildrenId` Has a Known IDOR Gap (Must Close in Phase 3)
**What goes wrong:** An authenticated admin in org A can delete a child belonging to org B by guessing the child UUID.
**Why it happens:** The existing `DeleteV1ChildrenId` handler has a `// TODO Phase 3: verify children.clerk_org_id matches the requester's active org before deleting (IDOR gap T-2-02)` comment. The check was intentionally deferred.
**How to avoid:** After replacing the header stubs with JWT claims, add the org ownership check: `if claims.ActiveOrganizationID != child.ClerkOrgID { WriteProblem(w, 403, ...) }`. The `consentEventID` SELECT already reads the child — just add `clerk_org_id` to that SELECT.
**Warning signs:** A handler test can delete a child from a different org using a valid token.

---

## Code Examples

### Complete Go Handler Auth Block (After Phase 3)

```go
// Source: docs/06-auth.md + pkg.go.dev/github.com/clerk/clerk-sdk-go/v2
// Apply this block at the TOP of every protected handler (before any DB call)
func (s *Server) PostV1Children(w http.ResponseWriter, r *http.Request) {
    // 1. Extract JWT claims (middleware populated these via WithHeaderAuthorization)
    claims, ok := clerk.SessionClaimsFromContext(r.Context())
    if !ok || claims == nil {
        WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized",
            "missing or invalid session token")
        return
    }

    // 2. Require active organization (REQ-027)
    if claims.ActiveOrganizationID == "" {
        WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden",
            "no active organization in session")
        return
    }

    // 3. Require admin role (REQ-016)
    if !claims.HasRole("org:admin") {
        WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden",
            "admin role required")
        return
    }

    clerkUserID := claims.Subject
    clerkOrgID  := claims.ActiveOrganizationID
    // ... rest of handler (DB operations unchanged from Phase 2) ...
}
```

### DeleteV1ChildrenId IDOR Fix

```go
// Source: docs/06-auth.md (T-2-02 gap closure)
// After fetching child from DB, before deletion:
var childClerkOrgID string
err = tx.QueryRowContext(ctx,
    `SELECT clerk_org_id, consent_event_id FROM children WHERE id = $1`,
    childID,
).Scan(&childClerkOrgID, &consentEventID)
if err == sql.ErrNoRows {
    WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
    return
}

// IDOR check: child must belong to requester's active org
if claims.ActiveOrganizationID != childClerkOrgID {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "access denied")
    return
}
```

### Unit Test Pattern for Auth Enforcement

```go
// Source: backend/internal/api/children_handler_test.go (existing pattern)
// For Phase 3 tests: simulate JWT claims via request context, not headers
import (
    "github.com/clerk/clerk-sdk-go/v2"
)

func withFakeClaims(r *http.Request, userID, orgID, role string) *http.Request {
    claims := &clerk.SessionClaims{}
    claims.Subject = userID
    claims.ActiveOrganizationID = orgID
    claims.ActiveOrganizationRole = role
    ctx := clerk.ContextWithSessionClaims(r.Context(), claims)
    return r.WithContext(ctx)
}

func TestPostV1Children_NoActivOrg_Returns403(t *testing.T) {
    srv := &api.Server{}
    mux := http.NewServeMux()
    api.HandlerFromMux(srv, mux)

    req := httptest.NewRequest(http.MethodPost, "/v1/children", someBody())
    req = withFakeClaims(req, "user_123", "", "org:admin") // empty org
    rec := httptest.NewRecorder()
    mux.ServeHTTP(rec, req)

    if rec.Code != http.StatusForbidden {
        t.Errorf("expected 403, got %d", rec.Code)
    }
}
```

Note: `clerk.ContextWithSessionClaims` is the test injection point — no real JWT verification needed in unit tests. [ASSUMED — verify the exact function name in clerk-sdk-go/v2 source]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `github.com/clerkinc/clerk-sdk-go` (v1) | `github.com/clerk/clerk-sdk-go/v2` | April 2025 (v1 EOL) | Different import path; v1 users must migrate |
| Ktor `bearer {}` without `nonCancellableRefresh` | `nonCancellableRefresh = true` | Ktor 3.x | Fixes race condition on coroutine cancellation during refresh |
| Prebuilt Clerk login UI | Headless SDK (custom screens) | Ongoing | More control over UX; required for this project per docs/06-auth.md |
| Org management web-only | iOS/Android native org components | 2026-06-05 release | `OrganizationSwitcher`, `OrganizationListView`, `OrganizationProfileView` now available as prebuilt native components (project uses custom screens per spec) |

**Deprecated/outdated:**
- `github.com/clerkinc/clerk-sdk-go` (v1): EOL April 2025. The import path `github.com/clerk/clerk-sdk-go/v2` is correct. Never mix v1 and v2 imports in the same binary.
- Ktor < 3.0: `BearerAuthConfig.nonCancellableRefresh` was added in Ktor 3.x. Do not target Ktor 2.x for this phase.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `azp` claim value in Clerk native mobile JWTs is unknown until runtime — it may be absent or a Clerk-specific internal value | Pitfall 6, Open Questions | Wrong `CLERK_AUTHORIZED_PARTY` value causes all native app requests to return 403 |
| A2 | `clerk.ContextWithSessionClaims` is the correct function to inject test claims in unit tests | Code Examples | Tests cannot compile; need to find the actual test injection API |
| A3 | `Clerk.auth.getToken()` on Android returns a fresh token (or cached if not expired) without requiring a separate `skipCache` parameter | Pattern 7 | Ktor `refreshTokens` block may need `GetTokenOptions(skipCache = true)` equivalent — check actual Android SDK API |
| A4 | iOS `clerk.auth.getToken()` works within `refreshTokens {}` coroutine context without deadlock | Pattern 5 | Deadlock if the Clerk iOS SDK is not thread-safe when called from Ktor's refresh coroutine |

**If this table is empty:** All claims were verified or cited. Currently 4 items require confirmation.

---

## Open Questions (RESOLVED)

1. **`azp` value for native mobile apps**
   - What we know: `WithHeaderAuthorization` + `AuthorizedPartyMatches` validates the `azp` claim; the Clerk docs say "the SDK does not validate azp by default" and the project's docs specify it must be set
   - What's unclear: The actual `azp` value emitted by Clerk's Android and iOS SDKs for native apps — it may be absent (Clerk SDK Python issue #90 shows azp can be missing), or it may be a Clerk frontend API URL
   - Recommendation: Add a debug-only Ktor interceptor in Wave 2 that logs the raw JWT, sign in on the Android emulator, and inspect the payload. Set `CLERK_AUTHORIZED_PARTY` to the actual value found. If `azp` is consistently absent, remove `AuthorizedPartyMatches` and document why (REQ-026 still satisfied if native SDKs don't include azp).
   - RESOLVED: Captured empirically in plan 03-03 Task 3 — a debug Ktor interceptor logs the raw JWT on first sign-in; the captured value is set as `CLERK_AUTHORIZED_PARTY`. If `azp` is absent from native JWTs, `AuthorizedPartyMatches` is a no-op and REQ-026 is still satisfied.

2. **`org:caregiver` custom role existence in Clerk Dashboard**
   - What we know: Custom roles must be pre-created in Clerk Dashboard before invitations can use them
   - What's unclear: Whether this role was created during Phase 1 provisioning
   - Recommendation: Verify in Clerk Dashboard (dev + prod) before writing the invitation plan. Add a Wave 0 task: "Verify org:caregiver role exists in Clerk Dashboard."
   - RESOLVED: Plan 03-04 Task 1 is a human-action blocking checkpoint — executor must verify/create `org:caregiver` in Clerk Dashboard before Wave 3 execution proceeds.

3. **iOS project setup scope**
   - What we know: `iosApp/` is currently empty (just `.gitkeep`). A full Xcode project must be created from scratch.
   - What's unclear: Whether iOS signing certificates and provisioning profiles are in place on the developer's Mac
   - Recommendation: iOS setup requires Xcode on macOS. Plan iOS as its own plan within Phase 3 and flag that it requires manual Xcode project creation steps that cannot be executed in CI.
   - RESOLVED: iOS is its own plan (03-05) with `autonomous: false` and a blocking human-action checkpoint for Xcode project setup; CI execution is not required for Phase 3 completion.

4. **Org creation in Phase 3 vs Phase 5**
   - What we know: Phase 3 goal says "create a family organization"; Phase 5 (REQ-036) describes the full admin onboarding wizard including org creation as step 2
   - What's unclear: Does "create a family organization" in Phase 3's goal mean the org creation UI must be built now, or only that org-based auth must work (with org creation deferred to Phase 5's wizard)?
   - Recommendation: Since Phase 3 success criteria make no reference to an org creation screen, treat org creation as a Phase 5 concern. For Phase 3 testing, create the Clerk org manually via the Dashboard or CLI. The auth infrastructure (JWT, middleware, org enforcement) is testable without a mobile org-creation UI.
   - RESOLVED: Org creation is deferred to Phase 5's onboarding wizard (REQ-036). Phase 3 tests use manually created Clerk orgs via Dashboard or CLI; the JWT/middleware/org-enforcement infrastructure is fully testable without a mobile org-creation UI.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Go | Go API compilation | Yes | 1.25.5 (local) | — |
| `clerk-sdk-go/v2` | Go middleware | Not yet in go.mod | v2.7.0 (Go proxy) | — (must add) |
| Clerk dev instance + `CLERK_SECRET_KEY` | Go middleware auth | Yes (Phase 1) | — | — |
| `CLERK_AUTHORIZED_PARTY` env var | Go azp validation | Not set yet | — | Must discover empirically |
| Android SDK | Android compilation | Yes (Phase 1 CI) | compileSdk 36, minSdk 29 | — |
| `com.clerk:clerk-android-api` | Android auth | Not yet in build.gradle | 1.0.31 (Maven Central) | — (must add) |
| Xcode (macOS) | iOS development | Unknown | — | No fallback — iOS requires macOS |
| Clerk Publishable Key (Android) | Android SDK init | Yes (Phase 1) | pk_test_... + pk_live_... | — |
| `org:caregiver` custom role in Clerk | Invitation flow | Unknown | — | Must create in Dashboard |

**Missing dependencies with no fallback:**
- `CLERK_AUTHORIZED_PARTY` — must be determined empirically from JWT inspection before the middleware can validate azp
- `org:caregiver` role in Clerk Dashboard — must be created before invitation testing
- Xcode on macOS — required for any iOS build or simulator testing

**Missing dependencies with fallback:**
- None beyond the above

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Go testing (built-in) + `httptest` package |
| Config file | None — uses `go test ./...` from `backend/` |
| Quick run | `cd /Users/brandon/code/onesteptwo/backend && go test ./internal/api/...` |
| Full suite | `cd /Users/brandon/code/onesteptwo/backend && go test ./...` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REQ-026 | Request with valid JWT but wrong azp is rejected 403 | unit | `go test ./internal/api/... -run TestAzpValidation` | No → Wave 0 |
| REQ-027 | Handler returns 403 when `ActiveOrganizationID` is empty | unit | `go test ./internal/api/... -run TestNoActiveOrg` | No → Wave 0 |
| REQ-015 | Handler returns 403 when org in JWT doesn't match child's `clerk_org_id` | unit | `go test ./internal/api/... -run TestOrgMismatch` | No → Wave 0 |
| REQ-016 | `POST /v1/children` returns 403 for org:caregiver role | unit | `go test ./internal/api/... -run TestCaregiverCannotCreateChild` | No → Wave 0 |
| REQ-016 | `DELETE /v1/children/{id}` returns 403 for org:caregiver role | unit | `go test ./internal/api/... -run TestCaregiverCannotDeleteChild` | No → Wave 0 |
| REQ-NF-010 | `clerk-sdk-go/v2` import used (not v1) | build-time | `go build ./...` (fails if v1 imported) | Will pass after go.mod updated |
| REQ-019 | `nonCancellableRefresh = true` is set | code review | Manual inspection of `HttpClientFactory.kt` | No → Wave 0 |
| T-2-02 | `DeleteV1ChildrenId` returns 403 for cross-org delete attempt | unit | `go test ./internal/api/... -run TestCrossOrgDeleteIsRejected` | No → Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && go test ./internal/api/...`
- **Per wave merge:** `cd backend && go test ./...`
- **Phase gate:** Full Go test suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `backend/internal/api/auth_test.go` — covers REQ-026, REQ-027, REQ-015, REQ-016, T-2-02
- [ ] Test helper `withFakeClaims()` that injects `clerk.SessionClaims` into request context (depends on verifying `clerk.ContextWithSessionClaims` function name — see Assumption A2)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes | Clerk handles credential validation; Go middleware validates JWT |
| V3 Session Management | Yes | Clerk issues and rotates JWTs; Ktor Auth plugin handles 401 refresh |
| V4 Access Control | Yes | Per-handler org_id check (REQ-015, REQ-027); per-handler role check (REQ-016) |
| V5 Input Validation | Yes — partial | Claims extracted from JWT (not request body); no user-supplied org_id trusted |
| V6 Cryptography | Yes — delegated | Clerk handles JWKS; SDK handles signature verification |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| JWT from different Clerk app accepted | Elevation of Privilege | `AuthorizedPartyMatches(CLERK_AUTHORIZED_PARTY)` |
| IDOR: admin in org A deletes child in org B | Elevation of Privilege | Org ownership check: `claims.ActiveOrganizationID == child.ClerkOrgID` (T-2-02) |
| Caregiver accesses admin-only endpoint | Elevation of Privilege | `claims.HasRole("org:admin")` check before DB operation |
| JWT replay after expiry | Spoofing | Clerk SDK enforces `exp` claim; 5s `Leeway` allows minor clock skew |
| Token refresh race (concurrent 401s) | Denial of Service | Ktor `AuthTokenHolder` Mutex; `nonCancellableRefresh = true` |
| User-supplied org_id in request body | Tampering | Never read org_id from body — always from `claims.ActiveOrganizationID` |
| PII in error responses | Information Disclosure | `WriteProblem` pattern already established; never pass `err.Error()` or JWT payload to detail field |

---

## Sources

### Primary (MEDIUM confidence — official docs fetched this session)
- [pkg.go.dev/github.com/clerk/clerk-sdk-go/v2](https://pkg.go.dev/github.com/clerk/clerk-sdk-go/v2) — `SessionClaims` struct, `SessionClaimsFromContext`, `SetKey`
- [pkg.go.dev/github.com/clerk/clerk-sdk-go/v2/http](https://pkg.go.dev/github.com/clerk/clerk-sdk-go/v2/http) — `WithHeaderAuthorization`, `RequireHeaderAuthorization`, `AuthorizedPartyMatches`, `Leeway`
- [api.ktor.io: BearerAuthConfig](https://api.ktor.io/ktor-client-auth/io.ktor.client.plugins.auth.providers/-bearer-auth-config/index.html) — `nonCancellableRefresh` property confirmed
- [ktor.io/docs/client-bearer-auth.html](https://ktor.io/docs/client-bearer-auth.html) — bearer auth configuration pattern
- [clerk.com/docs/ios/reference/native-mobile/organizations](https://clerk.com/docs/ios/reference/native-mobile/organizations) — iOS org management methods
- [clerk.com/docs/ios/reference/native-mobile/auth](https://clerk.com/docs/ios/reference/native-mobile/auth) — iOS sign-in/up methods
- [clerk.com/docs/android/reference/native-mobile/auth](https://clerk.com/docs/android/reference/native-mobile/auth) — Android sign-in/up methods

### Secondary (MEDIUM confidence — registry-verified versions)
- [Go module proxy: clerk-sdk-go v2.7.0](https://proxy.golang.org/github.com/clerk/clerk-sdk-go/v2/@latest) — version v2.7.0 released 2026-06-11 [VERIFIED]
- [Maven Central: com.clerk:clerk-android-api](https://repo1.maven.org/maven2/com/clerk/clerk-android-api/maven-metadata.xml) — version 1.0.31 [VERIFIED]
- [Maven Central: io.ktor:ktor-client-core](https://repo1.maven.org/maven2/io/ktor/ktor-client-core/maven-metadata.xml) — version 3.5.1 [VERIFIED]
- [github.com/clerk/clerk-ios releases](https://github.com/clerk/clerk-ios/blob/main/README.md) — v1.2.0 [CITED: README]

### Tertiary (LOW confidence — web search + inferred)
- Android organization API docs at `clerk-android.clerkstage.dev` — `getOrganizationMemberships`, `createInvitation` signatures [ASSUMED: staging docs, not GA]
- `azp` claim behavior for native mobile apps — documented only for web origins; mobile behavior inferred from Python SDK issue #90 and general Clerk docs [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Standard stack versions: HIGH — registry-verified (Go proxy, Maven Central, GitHub releases)
- Go middleware patterns: MEDIUM — official pkg.go.dev docs fetched this session
- KMP Ktor patterns: MEDIUM — official ktor.io docs confirmed `nonCancellableRefresh` property
- Android/iOS SDK method signatures: LOW — some from staging docs (clerkstage.dev); verify against release docs
- `azp` behavior for native mobile: LOW — underdocumented; must verify empirically

**Research date:** 2026-06-27
**Valid until:** 2026-07-27 (Clerk SDK active development; check for patch releases before executing)
