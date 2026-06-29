---
phase: 03-authentication-family-model
plan: "04"
subsystem: auth
tags: [clerk, android, jetpack-compose, org-picker, invitation, roles, multi-org]

# Dependency graph
requires:
  - phase: 03-03
    provides: AppNavigation with orgpicker placeholder route, PostAuthStub with onInvite callback, ClerkAuthRepository
provides:
  - OrgPickerScreen listing Clerk org memberships and activating selected org via setActive
  - InviteCaregiverScreen sending org:caregiver invitation via createInvitation
  - AppNavigation updated with working orgpicker route and admin-gated invite route
  - Clerk Dashboard custom roles org:admin and org:caregiver confirmed in dev instance
affects: [03-06, phase-05-core-event-logging]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "refreshTrigger pattern for LaunchedEffect re-execution (retry on error) in OrgPickerScreen"
    - "Client-side email validation via android.util.Patterns.EMAIL_ADDRESS before Clerk API call"
    - "Positional Clerk.auth.setActive(sessionId, organizationId) call — R8 strips named param metadata"
    - "Double-gated invite route: PostAuthStub onInvite checks org:admin before navigate; composable also checks role and pops if not admin (T-3-05 defence-in-depth)"

key-files:
  created:
    - androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/OrgPickerScreen.kt
    - androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/InviteCaregiverScreen.kt
  modified:
    - androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt

key-decisions:
  - "OrgPickerScreen uses refreshTrigger pattern for LaunchedEffect re-execution (retry on error); avoids nested coroutine launch from LaunchedEffect"
  - "InviteCaregiverScreen: client-side email validation via android.util.Patterns.EMAIL_ADDRESS before API call; avoids round-trip for obviously invalid input"
  - "createInvitation is a top-level Kotlin extension function in com.clerk.api.organizations; Clerk.organization = organizationMembership?.organization"
  - "invite route double-gated: PostAuthStub onInvite checks org:admin before navigate('invite'); composable also checks role and pops back if not admin (T-3-05 defence-in-depth)"

patterns-established:
  - "Clerk org activation: Clerk.auth.setActive called positionally (sessionId, organizationId) — R8 obfuscation strips named param metadata; named param call fails at compile time"
  - "Role-gated route: both the navigator (PostAuthStub callback) and the composable destination check the role — two independent enforcement points"

requirements-completed: [REQ-016, REQ-017, REQ-018]

# Metrics
duration: ~20min
completed: 2026-06-29
---

# Phase 03 Plan 04: Android Org Picker + Invite Caregiver Summary

**OrgPickerScreen (multi-org family switcher) and InviteCaregiverScreen (admin-only Clerk invitation) delivered on Android with org:admin/org:caregiver custom roles confirmed in Clerk Dashboard and end-to-end flow verified on device**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-06-29T18:00:00Z
- **Completed:** 2026-06-29T18:20:00Z
- **Tasks:** 3 (1 human-action checkpoint, 1 auto, 1 human-verify checkpoint)
- **Files modified:** 3

## Accomplishments

- Created OrgPickerScreen: loads org memberships via `getOrganizationMemberships()`, renders "Choose a family" UI with spinner/empty/error states, calls `Clerk.auth.setActive(sessionId, orgId)` positionally (R8 constraint) to reissue JWT with org context
- Created InviteCaregiverScreen: admin-only screen that calls `organization.createInvitation(emailAddress, role = "org:caregiver")` with client-side email validation, success snackbar, and three error states per UI-SPEC
- Updated AppNavigation: orgpicker route now renders OrgPickerScreen; invite route added and double-gated on org:admin role (PostAuthStub callback check + composable check)
- Confirmed org:admin and org:caregiver custom roles exist in Clerk Dashboard dev instance; user to replicate on production instance
- End-to-end device verification passed: multi-org picker appeared and activated org, caregiver invitation email delivered, caregiver signed in without seeing Invite action

## Task Commits

Each task was committed atomically:

1. **Task 1: Create org:admin and org:caregiver custom roles in Clerk Dashboard** - No commit (human-action checkpoint — external Clerk Dashboard configuration)
2. **Task 2: Org Picker and Invite Caregiver screens, wired into AppNavigation** - `afa43ea` (feat)
3. **Task 3: Verify multi-org switching and invitation flow end-to-end** - No commit (human-verify checkpoint — device verification)

## Files Created/Modified

- `androidApp/src/main/kotlin/com/onesteptwo/android/ui/auth/OrgPickerScreen.kt` — Multi-org family picker composable; loads memberships, activates selected org via setActive, handles loading/empty/error states
- `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/InviteCaregiverScreen.kt` — Admin-only invitation screen; sends org:caregiver invitation via createInvitation, shows success snackbar and inline confirmation
- `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/AppNavigation.kt` — Wired OrgPickerScreen into orgpicker route; added admin-gated invite route rendering InviteCaregiverScreen

## Clerk SDK Methods Used

| Method | Screen | Purpose |
|--------|--------|---------|
| `Clerk.user?.getOrganizationMemberships()` | OrgPickerScreen | Load all org memberships for the signed-in user |
| `Clerk.auth.setActive(sessionId, organizationId)` | OrgPickerScreen | Reissue JWT with chosen org_id as active context |
| `organization.createInvitation(emailAddress, role)` | InviteCaregiverScreen | Send Clerk invitation email with `org:caregiver` role |

## Decisions Made

- **Positional setActive call**: `Clerk.auth.setActive(sessionId, organizationId)` called positionally — R8 obfuscation strips named parameter metadata, causing a compile-time failure with named params. Positional is the only reliable form.
- **refreshTrigger pattern**: OrgPickerScreen uses an integer `refreshTrigger` state variable incremented on retry tap; passed as a `LaunchedEffect` key so the coroutine re-executes cleanly without nesting.
- **Client-side email validation**: `android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()` gates the Clerk API call in InviteCaregiverScreen — avoids a network round-trip for obviously invalid input.
- **Double-gated invite route**: T-3-05 defence-in-depth — PostAuthStub `onInvite` callback checks `org:admin` before calling `navigate("invite")`; the InviteCaregiverScreen composable independently checks the role and pops back if not admin. One check fails gracefully if the other is bypassed.
- **Production Clerk roles**: User confirmed dev instance roles; production instance role creation deferred to user (noted in Task 1 approval).

## Deviations from Plan

### Deferred Verification Step

**Go API 403 cross-check (Task 3, step 5) — deferred to Phase 5**

- **Found during:** Task 3 (end-to-end device verification)
- **Issue:** Task 3 step 5 required verifying that the caregiver receives a 403 from the Go API on child create/delete. No API calls are wired in the Android app yet — there are no network calls to exercise the Go middleware from the mobile client.
- **Disposition:** Accepted deferral; the Go middleware's HasRole enforcement was unit-tested in 03-01. Full end-to-end API 403 verification will be confirmed in Phase 5 when the Android app makes real API calls.
- **User confirmation:** User approved with this deferral acknowledged.

---

**Total deviations:** 1 (deferred verification step — not a code deviation)
**Impact on plan:** No code scope change. The deferred step is a verification cross-check, not a new implementation; 03-01's auth tests cover the server-side enforcement.

## Issues Encountered

None during implementation. The R8 named-parameter compile issue with `setActive` was anticipated from 03-03 SUMMARY decisions and applied proactively.

## User Setup Required

**Clerk Dashboard — Production instance roles:**

The dev instance now has `org:admin` (key: `org:admin`) and `org:caregiver` (key: `org:caregiver`) custom roles. The user must manually replicate these two roles in the Clerk **production** instance before Phase 3 production deployment:

1. Clerk Dashboard → switch to Production instance → Organizations → Roles
2. Create `admin` role with key `org:admin`
3. Create `caregiver` role with key `org:caregiver` (limited permissions: log events / view only)

## Next Phase Readiness

- 03-06 (iOS auth screens) can now use the same Clerk SDK method names and role key strings confirmed here (`org:admin`, `org:caregiver`, `setActive`, `createInvitation`)
- Both custom role keys are confirmed: `org:admin` and `org:caregiver` — iOS implementation can reference them directly
- Org-switching and invitation flow are proven on Android; iOS implementation follows the same patterns with ClerkKit equivalents
- Phase 5 (Core Event Logging) will wire the first real Go API calls from Android — that is when the Go 403 cross-check (deferred from Task 3) will be fully verified

---
*Phase: 03-authentication-family-model*
*Completed: 2026-06-29*
