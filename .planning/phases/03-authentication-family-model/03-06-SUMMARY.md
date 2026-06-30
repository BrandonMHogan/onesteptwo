---
phase: 03-authentication-family-model
plan: "06"
subsystem: ios-auth-screens
tags: [ios, swift, swiftui, clerkkit, auth, org-picker, invite, role-gate]

requires:
  - phase: 03-authentication-family-model
    plan: "05"
    provides: ContentView routing layer + ClerkAuthRepository + ClerkKit linked

provides:
  - SignInView: iOS Sign In screen with ClerkKit signInWithPassword
  - SignUpView: iOS Sign Up screen with ClerkKit signUp(emailAddress:password:)
  - OrgPickerView: multi-org picker calling getOrganizationMemberships + setActive
  - InviteCaregiverView: admin invitation screen calling inviteMember(role:"org:caregiver")
  - ContentView updated to render all real screens (no more placeholders)

affects:
  - Phase 5 (app shell): PostAuthRouter stub replaced by full 4-tab navigation

tech-stack:
  added: []
  patterns:
    - "SignInView/SignUpView: @FocusState IME Next/Done; if showPassword { TextField } else { SecureField } toggle"
    - "OrgPickerView: .refreshable {} pull-to-refresh on load error; List selection + Continue CTA"
    - "InviteCaregiverView: Task.sleep(for:.seconds(4)) auto-dismiss success; role string must be org:caregiver"
    - "PostAuthRouter: Clerk.shared.organizationMembership?.role == org:admin for invite gate (REQ-016)"

key-files:
  created:
    - iosApp/iosApp/Auth/SignInView.swift
    - iosApp/iosApp/Auth/SignUpView.swift
    - iosApp/iosApp/Auth/OrgPickerView.swift
    - iosApp/iosApp/Settings/InviteCaregiverView.swift
  modified:
    - iosApp/iosApp/ContentView.swift

key-decisions:
  - "signUp uses Auth.signUp(emailAddress:password:) — confirmed 1.1.5 signature (not 1.2.0 Strategy enum)"
  - "inviteMember(emailAddress:role:) is on Organization (Clerk.shared.organization); not on Auth"
  - "setActive(sessionId:organizationId:) requires non-nil sessionId — uses Clerk.shared.session?.id ?? empty"
  - "Role gate uses Clerk.shared.organizationMembership?.role (convenience property on Clerk, backed by user.organizationMemberships filtered by lastActiveOrganizationId)"
  - "InviteCaregiverView success auto-dismiss via Task.sleep(for: .seconds(4)) — avoids Timer dependency"
  - "Settings/ directory auto-discovered by Xcode 16 PBXFileSystemSynchronizedRootGroup — no pbxproj change needed"

requirements-completed: [REQ-015, REQ-016, REQ-017, REQ-018]

duration: ~4 min
completed: "2026-06-29"
---

# Phase 03 Plan 06: iOS Auth Screens Summary

**SignInView, SignUpView, OrgPickerView, and InviteCaregiverView created per UI-SPEC and wired into ContentView routing; ClerkKit 1.1.5 signInWithPassword / signUp / getOrganizationMemberships / setActive / inviteMember calls in place; role gate (org:admin) enforces T-3-05; BUILD SUCCEEDED.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-06-29T01:44:31Z
- **Completed:** 2026-06-29T01:48:56Z
- **Tasks:** 2 auto + 1 checkpoint:human-verify (pausing at Task 3)
- **Files modified:** 5

## Accomplishments

- `SignInView.swift`: email + SecureField/TextField show-hide toggle, `@FocusState` IME Next/Done flow, `Clerk.shared.auth.signInWithPassword(identifier:password:)`, error mapping for wrong credentials / too-many-attempts / network per UI-SPEC, 3 exact error strings, NavigationLink to SignUpView, `.ignoresSafeArea(.keyboard)`, semantic colors only
- `SignUpView.swift`: email + password + confirm-password, client validation (8-char + match with exact UI-SPEC copy), `Clerk.shared.auth.signUp(emailAddress:password:)`, server error mapping (email-exists + network), dismiss via `@Environment(\.dismiss)` for "Already have an account? Sign in" link
- `OrgPickerView.swift`: `getOrganizationMemberships()` with full-screen ProgressView; selectable List with `.title2` family names and checkmark; `setActive(sessionId:organizationId:)` on Continue; pull-to-refresh (`.refreshable`) for load error; empty state "No families yet" + body copy per UI-SPEC
- `InviteCaregiverView.swift`: email field + `organization.inviteMember(emailAddress:role:"org:caregiver")`; inline success auto-dismiss 4s via `Task.sleep`; 3 error strings per UI-SPEC; T-3-05 mitigated (view only rendered when role == "org:admin")
- `ContentView.swift` updated: sign-in placeholder → `NavigationStack { SignInView() }`; org-picker placeholder → `NavigationStack { OrgPickerView() }`; post-auth stub → NavigationStack with `NavigationLink { InviteCaregiverView() }` gated behind `clerk.organizationMembership?.role == "org:admin"`

## ClerkKit Method Names Used

| Operation | Method | Location |
|-----------|--------|----------|
| Sign in | `Clerk.shared.auth.signInWithPassword(identifier:password:)` | SignInView |
| Sign up | `Clerk.shared.auth.signUp(emailAddress:password:)` | SignUpView |
| List orgs | `Clerk.shared.user?.getOrganizationMemberships()` | OrgPickerView |
| Switch org | `Clerk.shared.auth.setActive(sessionId:organizationId:)` | OrgPickerView |
| Invite | `Clerk.shared.organization?.inviteMember(emailAddress:role:)` | InviteCaregiverView |
| Role check | `Clerk.shared.organizationMembership?.role` | ContentView PostAuthRouter |

## Task Commits

Tasks committed atomically to inner repo (`iosApp/iosApp/`), then gitlink updated in outer repo:

| Task | Inner commit | Outer commit | Files |
|------|-------------|-------------|-------|
| Task 1: SignInView + SignUpView + ContentView | `6eb05dd` | `f297d7e` | 3 |
| Task 2: OrgPickerView + InviteCaregiverView + ContentView | `7273864` | `cc98a3a` | 3 |

## Files Created/Modified

- `iosApp/iosApp/Auth/SignInView.swift` — sign-in screen; signInWithPassword; 3 error strings; NavigationLink to SignUpView
- `iosApp/iosApp/Auth/SignUpView.swift` — sign-up screen; client validation; signUp(emailAddress:password:); 4 error/validation strings; dismiss on success
- `iosApp/iosApp/Auth/OrgPickerView.swift` — org picker; getOrganizationMemberships + setActive; pull-to-refresh; empty + error states
- `iosApp/iosApp/Settings/InviteCaregiverView.swift` — admin invite; inviteMember role:"org:caregiver"; success auto-dismiss 4s; 3 error strings
- `iosApp/iosApp/ContentView.swift` — all placeholders replaced; NavigationStack routing; org:admin role gate

## Decisions Made

- **`signUp` signature (1.1.5 vs 1.2.0):** ClerkKit 1.1.5 uses `Auth.signUp(emailAddress:password:)` — individual parameters, not the 1.2.0 `Strategy` enum. Verified from DerivedData source.
- **`inviteMember` on Organization:** `Clerk.shared.organization?.inviteMember(emailAddress:role:)` — not on `Auth`. `Clerk.shared.organization` is a computed property that returns the active org from `organizationMembership?.organization`.
- **Role gate via `Clerk.shared.organizationMembership`:** Convenience property on `Clerk` that finds the active org's membership by `session.lastActiveOrganizationId`. Returns `nil` if no active org — safe: non-nil required for invite visibility.
- **`Settings/` directory:** Created with `mkdir -p`; Xcode 16 `PBXFileSystemSynchronizedRootGroup` auto-discovers it. No `project.pbxproj` changes needed.
- **Success auto-dismiss:** Used `Task.sleep(for: .seconds(4))` for 4-second auto-dismiss on InviteCaregiverView — avoids Timer and is cancellable with task lifecycle.

## Deviations from Plan

None — plan executed exactly as written. All ClerkKit 1.1.5 API signatures verified against DerivedData source before use.

## Threat Model Coverage

| Threat | Disposition | Implementation |
|--------|-------------|----------------|
| T-3-05 Elevation of Privilege: invite visible to caregivers | Mitigated | InviteCaregiverView only rendered when `Clerk.shared.organizationMembership?.role == "org:admin"` |
| T-3-13 Tampering: wrong role in invitation | Mitigated | Exact `"org:caregiver"` string hardcoded; Clerk validates against Dashboard-configured roles |
| T-3-14 Spoofing: stale org context after switching | Mitigated | `setActive(sessionId:organizationId:)` reissues JWT with new org_id in every session token |
| T-3-15 Info Disclosure: invitation errors leaking PII | Accepted | Fixed UI strings; raw `ClerkAPIError.message` never surfaced to UI |

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `Text("Signed in successfully")` / `Text("Welcome")` in PostAuthRouter | ContentView.swift | Full 4-tab app shell is Phase 5; Phase 3 uses minimal stub |

The stub does not prevent Phase 3's auth goals — sign-up, sign-in, org switching, and invitation all work. Phase 5 replaces the stub with the full navigation shell.

## Checkpoint: Task 3 Pending

Task 3 is a `checkpoint:human-verify` requiring the user to test the full iOS auth flow in the Simulator. Stopping here per plan.

## Self-Check

- `iosApp/iosApp/Auth/SignInView.swift`: FOUND
- `iosApp/iosApp/Auth/SignUpView.swift`: FOUND
- `iosApp/iosApp/Auth/OrgPickerView.swift`: FOUND
- `iosApp/iosApp/Settings/InviteCaregiverView.swift`: FOUND
- `iosApp/iosApp/ContentView.swift` (contains NavigationStack + OrgPickerView + org:admin): FOUND
- Inner commit `6eb05dd` (Task 1): FOUND
- Inner commit `7273864` (Task 2): FOUND
- Outer commit `f297d7e` (Task 1): FOUND
- Outer commit `cc98a3a` (Task 2): FOUND
- BUILD SUCCEEDED: CONFIRMED

## Self-Check: PASSED

---
*Phase: 03-authentication-family-model*
*Completed: 2026-06-29 (Tasks 1-2; Task 3 checkpoint pending)*
