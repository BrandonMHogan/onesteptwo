# Phase 3: Authentication & Family Model - Pattern Map

**Mapped:** 2026-06-27
**Files analyzed:** 23 new/modified files across Go, KMP shared, Android, and iOS tiers
**Analogs found:** 8 / 23 (remaining 15 are net-new with no codebase analog — use RESEARCH.md patterns)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/cmd/server/main.go` | config/wiring | request-response | itself (current state) | self-modify |
| `backend/internal/api/server.go` | controller | request-response | itself (3 handlers with TODO stubs) | self-modify |
| `backend/internal/api/auth_test.go` | test | request-response | `backend/internal/api/children_handler_test.go` | role-match |
| `backend/go.mod` | config | — | itself | self-modify |
| `shared/build.gradle.kts` | config | — | itself (current state) | self-modify |
| `shared/src/commonMain/kotlin/com/onesteptwo/auth/AuthRepository.kt` | service interface | request-response | none | no analog |
| `shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt` | utility/factory | request-response | none | no analog |
| `shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt` | service impl | request-response | none | no analog |
| `shared/src/iosMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt` | service impl | request-response | none | no analog |
| `androidApp/build.gradle.kts` | config | — | itself (current state) | self-modify |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt` | provider/application | — | `androidApp/.../MainActivity.kt` | partial-match |
| `androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt` | component | request-response | itself (current stub) | self-modify |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignInScreen.kt` | component | request-response | none | no analog |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/SignUpScreen.kt` | component | request-response | none | no analog |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/OrgPickerScreen.kt` | component | request-response | none | no analog |
| `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/InviteCaregiverScreen.kt` | component | request-response | none | no analog |
| `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt` | router | request-response | none | no analog |
| `iosApp/iosApp/OneStepTwoApp.swift` | provider/application | — | none | no analog |
| `iosApp/iosApp/ContentView.swift` | component | request-response | none | no analog |
| `iosApp/iosApp/Auth/SignInView.swift` | component | request-response | none | no analog |
| `iosApp/iosApp/Auth/SignUpView.swift` | component | request-response | none | no analog |
| `iosApp/iosApp/Auth/OrgPickerView.swift` | component | request-response | none | no analog |
| `iosApp/iosApp/Settings/InviteCaregiverView.swift` | component | request-response | none | no analog |

---

## Pattern Assignments

### `backend/cmd/server/main.go` (config/wiring, request-response)

**Analog:** itself — current state at lines 1-37

**Current imports pattern** (lines 1-12):
```go
package main

import (
    "database/sql"
    "log"
    "net/http"
    "os"

    _ "github.com/lib/pq"

    "github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)
```

**Add to imports** — new lines to splice in after `"os"`:
```go
    "time"

    "github.com/clerk/clerk-sdk-go/v2"
    clerkhttp "github.com/clerk/clerk-sdk-go/v2/http"
```

**Current server wiring pattern** (lines 28-34) — the section to modify:
```go
srv := &api.Server{DB: db}
srv.Clerk = api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))
mux := http.NewServeMux()
api.HandlerWithOptions(srv, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: api.ProblemErrorHandler})

log.Printf("starting server on :%s", port)
if err := http.ListenAndServe(":"+port, mux); err != nil {
    log.Fatal(err)
}
```

**Replace with** (adds `clerk.SetKey` + `authMiddleware` wrapping the mux):
```go
clerk.SetKey(os.Getenv("CLERK_SECRET_KEY"))

authMiddleware := clerkhttp.WithHeaderAuthorization(
    clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY")),
    clerkhttp.Leeway(5*time.Second),
)

srv := &api.Server{DB: db}
srv.Clerk = api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))
mux := http.NewServeMux()
api.HandlerWithOptions(srv, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: api.ProblemErrorHandler})

log.Printf("starting server on :%s", port)
if err := http.ListenAndServe(":"+port, authMiddleware(mux)); err != nil {
    log.Fatal(err)
}
```

**CRITICAL:** `WithHeaderAuthorization` not `RequireHeaderAuthorization` — the latter blocks `/healthz` which has no Authorization header (see RESEARCH.md Pattern 1).

---

### `backend/internal/api/server.go` (controller, request-response — 3 handler modifications)

**Analog:** itself — current state. Three handlers each contain `// TODO Phase 3` stubs.

**Current auth stub pattern** — appears at top of `PostV1Children` (lines 48-54) and `DeleteV1Account` (lines 259-263):
```go
// TODO Phase 3: replace with JWT claim extraction
clerkUserID := r.Header.Get("X-Clerk-User-Id")
clerkOrgID := r.Header.Get("X-Clerk-Org-Id")
if clerkUserID == "" || clerkOrgID == "" {
    WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing identity headers")
    return
}
```

**Variant for `DeleteV1ChildrenId`** (lines 151-156 — only user ID, no org ID):
```go
// TODO Phase 3: replace with JWT claim extraction
clerkUserID := r.Header.Get("X-Clerk-User-Id")
if clerkUserID == "" {
    WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing identity header")
    return
}
```

**Replace ALL THREE stubs with this JWT claims block** (add `"github.com/clerk/clerk-sdk-go/v2"` to imports):
```go
// Extract JWT claims populated by WithHeaderAuthorization middleware.
claims, ok := clerk.SessionClaimsFromContext(r.Context())
if !ok || claims == nil {
    WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
    return
}
// Require an active organization in the session (REQ-027).
if claims.ActiveOrganizationID == "" {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
    return
}
// Require admin role for write operations (REQ-016). CRITICAL: prefix must be "org:" not "admin".
if !claims.HasRole("org:admin") {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "admin role required")
    return
}
clerkUserID := claims.Subject
clerkOrgID  := claims.ActiveOrganizationID
```

**IDOR fix for `DeleteV1ChildrenId`** — the existing SELECT at line 176 reads only `consent_event_id`; extend it to also read `clerk_org_id`, then add the ownership check after the `ErrNoRows` guard:
```go
// Existing SELECT — extend to also capture clerk_org_id:
var consentEventID, childClerkOrgID string
err = tx.QueryRowContext(ctx,
    `SELECT consent_event_id, clerk_org_id FROM children WHERE id = $1`, childID,
).Scan(&consentEventID, &childClerkOrgID)
if err == sql.ErrNoRows {
    WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
    return
}
if err != nil {
    WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not look up child")
    return
}
// REQ-015 / T-2-02: child must belong to requester's active org (IDOR gap closure).
if claims.ActiveOrganizationID != childClerkOrgID {
    WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "access denied")
    return
}
```

**`WriteProblem` call signature** (from `backend/internal/api/problem.go` lines 15-24 — copy exactly):
```go
WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "detail message here")
```

---

### `backend/internal/api/auth_test.go` (test, request-response)

**Analog:** `backend/internal/api/children_handler_test.go`

**Test file structure pattern** (lines 1-18 of analog):
```go
package api_test

import (
    "bytes"
    "encoding/json"
    "net/http"
    "net/http/httptest"
    "testing"

    "github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)
```

**Add to imports for auth tests:**
```go
    "github.com/clerk/clerk-sdk-go/v2"
```

**Test server + mux wiring pattern** (lines 17-19 of analog — copy exactly for each test):
```go
srv := &api.Server{} // nil DB is safe: handler returns before DB use on auth error
mux := http.NewServeMux()
api.HandlerFromMux(srv, mux)
```

**Test request pattern** (lines 20-26 of analog):
```go
req := httptest.NewRequest(http.MethodPost, "/v1/children", someBody())
rec := httptest.NewRecorder()
mux.ServeHTTP(rec, req)
if rec.Code != http.StatusForbidden {
    t.Errorf("expected 403, got %d", rec.Code)
}
```

**Test helper to inject fake JWT claims** — this is net-new (Assumption A2 in RESEARCH.md; verify exact function name against clerk-sdk-go/v2 source before using):
```go
// withFakeClaims injects clerk.SessionClaims into a request context for unit testing.
// Does not exercise JWT signature verification — handler auth logic only.
func withFakeClaims(r *http.Request, userID, orgID, role string) *http.Request {
    claims := &clerk.SessionClaims{}
    claims.Subject = userID
    claims.ActiveOrganizationID = orgID
    claims.ActiveOrganizationRole = role
    ctx := clerk.ContextWithSessionClaims(r.Context(), claims)
    return r.WithContext(ctx)
}
```

**403 test pattern** (no claims → 401; empty org → 403; role mismatch → 403):
```go
func TestPostV1Children_NoActiveOrg_Returns403(t *testing.T) {
    srv := &api.Server{}
    mux := http.NewServeMux()
    api.HandlerFromMux(srv, mux)

    req := httptest.NewRequest(http.MethodPost, "/v1/children", validChildBody())
    req = withFakeClaims(req, "user_123", "", "org:admin") // empty org
    rec := httptest.NewRecorder()
    mux.ServeHTTP(rec, req)

    if rec.Code != http.StatusForbidden {
        t.Errorf("expected 403, got %d", rec.Code)
    }
}
```

**Content-Type check pattern** (lines 31-34 of analog — apply to every test):
```go
ct := rec.Header().Get("Content-Type")
if ct != "application/problem+json" {
    t.Errorf("expected Content-Type application/problem+json, got %q", ct)
}
```

---

### `shared/build.gradle.kts` (config — modify)

**Analog:** itself — current state at lines 1-45

**Current `sourceSets` block** (lines 14-23) — the section to extend:
```kotlin
sourceSets {
    commonMain.dependencies {
        implementation(libs.sqldelight.runtime)
    }
    androidMain.dependencies {
        implementation(libs.sqldelight.android.driver)
    }
    iosMain.dependencies {
        implementation(libs.sqldelight.native.driver)
    }
}
```

**Add Ktor dependencies alongside SQLDelight** (keep existing lines, add):
```kotlin
sourceSets {
    commonMain.dependencies {
        implementation(libs.sqldelight.runtime)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.auth)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
    }
    androidMain.dependencies {
        implementation(libs.sqldelight.android.driver)
        implementation(libs.ktor.client.okhttp)
    }
    iosMain.dependencies {
        implementation(libs.sqldelight.native.driver)
        implementation(libs.ktor.client.darwin)
    }
}
```

Note: the version catalog (`libs.versions.toml`) must also have entries for these keys at version 3.5.1. Check existing catalog for SQLDelight entry format to match the alias naming convention.

---

### `androidApp/build.gradle.kts` (config — modify)

**Analog:** itself — current state at lines 1-49

**Current `dependencies` block** (lines 42-49):
```kotlin
dependencies {
    implementation(project(":shared"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
```

**Add Clerk Android SDK** (append inside existing `dependencies` block):
```kotlin
    implementation("com.clerk:clerk-android-api:1.0.31")
```

---

### `androidApp/src/main/kotlin/com/onesteptwo/android/ClerkApp.kt` (provider/application)

**Analog:** `androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt` (same package, same import conventions)

**Package and import pattern** (from analog lines 1-8):
```kotlin
package com.onesteptwo.android

import android.app.Application
import com.clerk.android.Clerk
```

**Core Application subclass pattern** (net-new; modeled after RESEARCH.md Pattern 7 and Pitfall 7):
```kotlin
class ClerkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(
            application = this,
            publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        )
    }
}
```

After creating this class, add `android:name=".ClerkApp"` to the `<application>` element in `AndroidManifest.xml`.

---

### `androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt` (component — modify)

**Analog:** itself — current state at lines 1-21

**Current `onCreate`/`setContent` pattern** (lines 11-20):
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("OneStepTwo")
                }
            }
        }
    }
}
```

**Modify to host the nav graph instead of a stub `Text`:**
```kotlin
setContent {
    MaterialTheme {
        Surface {
            AppNavigation()  // defined in navigation/AppNavigation.kt
        }
    }
}
```

---

### `shared/src/commonMain/kotlin/com/onesteptwo/auth/AuthRepository.kt` (service interface)

**No codebase analog.** Use RESEARCH.md Pattern 6 directly:

```kotlin
package com.onesteptwo.auth

interface AuthRepository {
    suspend fun getToken(): String?
    suspend fun refreshToken(): String?
    fun isSignedIn(): Boolean
}
```

---

### `shared/src/commonMain/kotlin/com/onesteptwo/auth/HttpClientFactory.kt` (utility/factory)

**No codebase analog.** Use RESEARCH.md Pattern 5 directly. Verify `sendWithoutRequest` host list matches Railway deployment URL before shipping.

---

### `shared/src/androidMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt` (service impl — Android)

**No codebase analog.** Use RESEARCH.md Pattern 7. Flag Assumption A3: verify whether `Clerk.auth.getToken()` returns a fresh token on every call or requires a `skipCache` equivalent.

---

### `shared/src/iosMain/kotlin/com/onesteptwo/auth/ClerkAuthRepository.kt` (service impl — iOS)

**No codebase analog.** iOS impl calls Swift ClerkKit via SKIE or `expect/actual`. Flag Assumption A4 re: thread-safety when called from Ktor's refresh coroutine.

---

### Android UI screens: `SignInScreen.kt`, `SignUpScreen.kt`, `OrgPickerScreen.kt`, `InviteCaregiverScreen.kt`, `AppNavigation.kt`

**No codebase analog** — only a stub `MainActivity.kt` with no Compose composables. Use RESEARCH.md Patterns 8, 9, 10 for SDK call shapes. Apply Jetpack Compose `@Composable` function conventions matching the existing `MaterialTheme`/`Surface`/`ComponentActivity` scaffold seen in `MainActivity.kt` lines 1-8.

---

### iOS files: `OneStepTwoApp.swift`, `ContentView.swift`, `Auth/*.swift`, `Settings/*.swift`

**No codebase analog** — `iosApp/` contains only `.gitkeep`. Use RESEARCH.md Patterns 11–14 for ClerkKit call shapes. All iOS files require manual Xcode project creation (cannot be scripted via CI — see RESEARCH.md Open Question 3).

---

## Shared Patterns

### Error Response (Go)
**Source:** `backend/internal/api/problem.go` lines 15-24
**Apply to:** All Go handler modifications
```go
WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
WriteProblem(w, http.StatusForbidden,    "about:blank", "Forbidden",    "no active organization in session")
WriteProblem(w, http.StatusForbidden,    "about:blank", "Forbidden",    "admin role required")
WriteProblem(w, http.StatusForbidden,    "about:blank", "Forbidden",    "access denied")
```
Never pass `err.Error()` or JWT payload as the `detail` argument.

### Test Server Setup (Go)
**Source:** `backend/internal/api/children_handler_test.go` lines 17-19
**Apply to:** `auth_test.go` and any new handler tests
```go
srv := &api.Server{} // nil DB is safe: handler returns before DB use on auth/validation error
mux := http.NewServeMux()
api.HandlerFromMux(srv, mux)
```

### Test Content-Type Assertion (Go)
**Source:** `backend/internal/api/children_handler_test.go` lines 31-34
**Apply to:** Every test function in `auth_test.go`
```go
ct := rec.Header().Get("Content-Type")
if ct != "application/problem+json" {
    t.Errorf("expected Content-Type application/problem+json, got %q", ct)
}
```

### Transaction Pattern (Go handlers)
**Source:** `backend/internal/api/server.go` lines 88-93
**Apply to:** Any new handlers that touch DB; do not change the existing pattern
```go
tx, err := s.DB.BeginTx(r.Context(), nil)
if err != nil {
    WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not begin transaction")
    return
}
defer tx.Rollback() //nolint:errcheck // safe no-op after Commit
```

### Compose entry pattern (Android)
**Source:** `androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt` lines 1-21
**Apply to:** All new Android screen files (`SignInScreen.kt`, etc.)
```kotlin
package com.onesteptwo.android.ui.auth  // adjust sub-package per file

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
```

---

## No Analog Found

Files with no close match in the codebase — planner must use RESEARCH.md patterns exclusively:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `shared/.../auth/AuthRepository.kt` | service interface | request-response | No KMP interfaces exist; first shared module code beyond SQLDelight |
| `shared/.../auth/HttpClientFactory.kt` | utility | request-response | No Ktor client code anywhere in codebase |
| `shared/.../androidMain/.../ClerkAuthRepository.kt` | service impl | request-response | No Android auth code exists |
| `shared/.../iosMain/.../ClerkAuthRepository.kt` | service impl | request-response | No iOS Kotlin code exists |
| `androidApp/.../ClerkApp.kt` | provider | — | No Application subclass exists |
| `androidApp/.../ui/auth/SignInScreen.kt` | component | request-response | No Compose screens exist |
| `androidApp/.../ui/auth/SignUpScreen.kt` | component | request-response | No Compose screens exist |
| `androidApp/.../ui/auth/OrgPickerScreen.kt` | component | request-response | No Compose screens exist |
| `androidApp/.../ui/settings/InviteCaregiverScreen.kt` | component | request-response | No Compose screens exist |
| `androidApp/.../navigation/AppNavigation.kt` | router | request-response | No nav graph exists |
| `iosApp/iosApp/OneStepTwoApp.swift` | provider | — | iosApp/ is empty |
| `iosApp/iosApp/ContentView.swift` | component | request-response | iosApp/ is empty |
| `iosApp/iosApp/Auth/SignInView.swift` | component | request-response | iosApp/ is empty |
| `iosApp/iosApp/Auth/SignUpView.swift` | component | request-response | iosApp/ is empty |
| `iosApp/iosApp/Auth/OrgPickerView.swift` | component | request-response | iosApp/ is empty |
| `iosApp/iosApp/Settings/InviteCaregiverView.swift` | component | request-response | iosApp/ is empty |

---

## Metadata

**Analog search scope:** `backend/`, `shared/`, `androidApp/`, `iosApp/`
**Files scanned:** 19 source files read directly; directory listing confirmed iosApp is empty
**Pattern extraction date:** 2026-06-27

### Assumptions requiring verification before execution
- **A2** (`clerk.ContextWithSessionClaims`): Verify this function name exists in clerk-sdk-go/v2 source before writing `withFakeClaims` helper in `auth_test.go`.
- **A3** (`Clerk.auth.getToken()` on Android): Verify whether a `skipCache` parameter is needed in `refreshTokens {}` block.
- **A4** (iOS thread safety): Verify ClerkKit `getToken()` is safe to call from Ktor's refresh coroutine context.
- **A6** (`azp` value): Set `CLERK_AUTHORIZED_PARTY` only after empirically inspecting the JWT from a native Android login — value is unknown at pattern-mapping time (see RESEARCH.md Pitfall 6).
