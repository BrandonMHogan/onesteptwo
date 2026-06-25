# Authentication & Family Model

> Last updated: 2026-06-25

## Provider: Clerk

Clerk handles all identity. Our database stores only `clerk_user_id` and `clerk_org_id` as foreign keys — no passwords, no sessions, no PII.

## The Family Model

A Clerk **Organization** maps 1:1 to a family unit. Every child profile belongs to a Clerk org. Every user who can view or log events for a child must be a member of that org.

```
Clerk Organization ("The Hogan Family")
  ├── Member: mom@email.com  [role: admin]
  ├── Member: dad@email.com  [role: admin]
  └── Member: daycare@email.com  [role: caregiver]

OneStepTwo Database
  └── children.clerk_org_id → "org_abc123"
       └── potty_events.logged_by → clerk_user_id of whoever tapped
```

## Roles

Start with two roles in Clerk:

| Role | Clerk token value | Can do |
|---|---|---|
| `admin` | `org:admin` | Full access — add/remove members, create/delete child profiles, view and log all events |
| `caregiver` | `org:caregiver` | Log events and view history — cannot manage the family or other members |

Parents are admins. Daycare workers, grandparents, and other caregivers get the caregiver role. Roles are managed inside Clerk — no role logic to build in our backend beyond checking org membership on every request.

**Important:** Clerk v2 tokens prefix all roles with `org:`. Always check `"org:admin"` and `"org:caregiver"` — not `"admin"` or `"caregiver"`. Hardcoding without the prefix causes silent failures on every role check.

## Invitation Flow

This is a core product feature — the mechanism by which a parent brings a caregiver into the family.

1. Admin parent taps "Invite caregiver" in the app
2. App calls Clerk's invitation API with the caregiver's email and the `caregiver` role
3. Clerk sends an invitation email with a magic link
4. Caregiver taps the link, downloads the app (App Store / Play Store links in the email), creates their account, and is automatically added to the org
5. They immediately have access to the child's data — no additional setup

No custom invitation logic to build. Clerk handles token generation, email delivery, and org membership on acceptance.

## SDK Integration with KMP

Both mobile SDKs are used in headless mode — no pre-built login UI, just token management.

**Android (`clerk-android-api`):**
- Handles sign-in, sign-up, token storage, and refresh internally
- App provides its own Jetpack Compose login screens
- After sign-in, retrieve the session JWT and pass it to the KMP shared module

**iOS (`ClerkKit`):**
- Same pattern — no browser redirect, no pre-built UI
- App provides its own SwiftUI login screens
- Session token retrieved from `Clerk.shared.session` and passed to the KMP shared module

**KMP shared module:**
- `AuthRepository` interface with platform implementations
- Ktor Client uses the built-in `Auth` plugin with `bearerTokens { }` — attaches `Authorization: Bearer <token>` to every request and handles 401 refresh automatically
- `nonCancellableRefresh = true` is required — prevents a cancelled caller from rolling back a completed refresh and causing stuck requests (KTOR-7852)
- Concurrent 401s are safe: Ktor's `AuthTokenHolder` has an internal `Mutex` that ensures only one refresh fires; Clerk's Android SDK independently deduplicates via a `Deferred` cache. No custom mutex needed.

```kotlin
install(Auth) {
    bearer {
        loadTokens {
            clerk.session?.getToken()?.jwt?.let { BearerTokens(it, "") }
        }
        refreshTokens {
            clerk.session?.getToken(GetTokenOptions(skipCache = true))?.jwt
                ?.let { BearerTokens(it, "") }
        }
        nonCancellableRefresh = true
    }
}
```

## Go API — JWT Validation

**Library:** `github.com/clerk/clerk-sdk-go/v2` — Clerk's official Go SDK (v2.7.0+). Do not use the deprecated `github.com/clerkinc/clerk-sdk-go` (v1, EOL April 2025) or roll your own JWKS validation.

### Middleware Setup

```go
clerk.SetKey(os.Getenv("CLERK_SECRET_KEY"))

authMiddleware := clerkhttp.RequireHeaderAuthorization(
    clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY")),
    clerkhttp.Leeway(5*time.Second),
)
```

`CLERK_AUTHORIZED_PARTY` is your frontend origin (e.g. the mobile app's identifier or `https://app.onesteptwo.com`). This must be set — the SDK does not validate `azp` by default, meaning any valid Clerk token from any frontend can call the API if this is omitted.

### Claims Validation

| Claim | Validated by | Notes |
|---|---|---|
| `iss` | SDK automatically | Weak prefix check — `azp` is the real defence |
| `exp` / `nbf` | SDK automatically | 5s leeway configured above |
| `sub` | Available via `claims.Subject` | Clerk user ID — never trust from request body |
| `azp` | **Must configure explicitly** | Set `AuthorizedPartyMatches` — not validated by default |
| `org_id` | **Check in each handler** | `claims.ActiveOrganizationID` — empty string when no active org |
| `org_role` | **Check in each handler** | Format: `"org:admin"`, `"org:caregiver"` |

### Org Enforcement Pattern

The middleware passes tokens with no active org — it does not reject them. Enforce org context at the handler level:

```go
claims, _ := clerk.SessionClaimsFromContext(r.Context())

if claims.ActiveOrganizationID == "" {
    http.Error(w, "no active organization", http.StatusForbidden)
    return
}
if claims.ActiveOrganizationID != child.ClerkOrgID {
    http.Error(w, "access denied", http.StatusForbidden)
    return
}
```

### JWK Caching

The HTTP middleware caches JWKs in memory (keyed by `kid`, 1-hour TTL, `sync.RWMutex`-protected). This is automatic when using the middleware. If `jwt.Verify` is called directly outside the middleware (e.g. in a background worker), there is no caching — fetch and cache the JWK manually or pass it via `VerifyParams.JWK`.

## Multi-Org Users

A caregiver may belong to multiple family orgs (e.g. a nanny working for two families). Clerk's session JWT carries only one active org at a time.

**Backend rule:** `claims.ActiveOrganizationID` must match the `clerk_org_id` of the child being accessed. If it doesn't, return 403. No special backend logic required.

**Client responsibility:** The app checks after login whether the user belongs to multiple orgs. If so, an org-picker screen is shown before entering the main app. The selected org is activated in the Clerk session, and all subsequent JWTs carry that org's `org_id`. Switching families requires re-activating a different org via the Clerk SDK, which issues a new JWT.

## SvelteKit (Marketing Site)

The v1 marketing site has no auth. When a web dashboard is added in a future version, `clerk-sveltekit` (markjaquith community adapter) handles SSR session management via SvelteKit `locals`.
