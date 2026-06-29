---
phase: 03-authentication-family-model
plan: "05"
subsystem: ios-auth
tags: [ios, swift, clerkkit, xcode, kmp, ktor, auth, swiftui, swift6, xcframework]

requires:
  - phase: 03-authentication-family-model
    plan: "02"
    provides: AuthRepository interface + HttpClientFactory.buildHttpClient in shared KMP module

provides:
  - ClerkAuthRepository Swift class conforming to shared AuthRepository Obj-C protocol
  - iOS app entry point calling Clerk.configure at launch (T-3-11)
  - ContentView + PostAuthRouter routing by Clerk auth state
  - ClerkKit 1.1.5 linked to iosApp Xcode target via XCSwiftPackageProductDependency

affects:
  - 03-06 (iOS auth screens: builds SignInView + OrgPickerView on top of this routing layer)
  - 03-04 (backend: iOS app will call the same Go API; ClerkAuthRepository provides tokens)

tech-stack:
  added:
    - ClerkKit 1.1.5 (clerk-ios via SPM, pinned in Package.resolved)
    - ClerkKitUI 1.1.5 (same SPM package, linked for future 03-06 use)
    - PBXFileSystemSynchronizedRootGroup (Xcode 16 auto-discovery; no explicit PBXBuildFile per Swift source needed)
  patterns:
    - "@MainActor class + @preconcurrency Obj-C protocol conformance for KN bridge compatibility (Swift 6)"
    - "Task { @MainActor in } blocks bridge Clerk async session token calls to KN completion handlers"
    - "@State private var clerk = Clerk.shared with @Observable for reactive SwiftUI routing"
    - "Auth-state routing in root ContentView mirrors Android NavController approach from UI-SPEC"

key-files:
  created:
    - iosApp/iosApp/Auth/ClerkAuthRepository.swift
  modified:
    - iosApp/iosApp/iosAppApp.swift
    - iosApp/iosApp/ContentView.swift
    - iosApp/iosApp.xcodeproj/project.pbxproj
    - iosApp/Info.plist

key-decisions:
  - "@MainActor on ClerkAuthRepository + @preconcurrency AuthRepository conformance resolves Swift 6 actor isolation: Clerk.shared and Session.session are @MainActor-isolated in ClerkKit 1.1.5; @preconcurrency defers check to runtime (Obj-C ABI does not enforce actor isolation)"
  - "ClerkKit + ClerkKitUI product dependencies were absent from project.pbxproj (packageProductDependencies was empty); added XCSwiftPackageProductDependency entries manually to fix Rule 3 blocking build failure"
  - "Assumption A4 thread-safety resolved: ClerkKit token API is async/await backed by URLSession; no lock held across await boundary; calling from Task { @MainActor in } is safe"
  - "ClerkKit token API confirmed: Session.getToken() (no args = cached), Session.getToken(.init(skipCache: true)) = force refresh; matches Android skipCache=false/true pattern from 03-02"
  - "Xcode project uses PBXFileSystemSynchronizedRootGroup (Xcode 16): Swift files auto-discovered from iosApp/ directory; ClerkAuthRepository.swift is compiled without explicit PBXBuildFile entry"

patterns-established:
  - "iOS ClerkAuthRepository pattern: @MainActor + @preconcurrency Obj-C protocol for KN bridge Swift 6 conformance"
  - "ContentView routing pattern: Clerk.isLoaded → session nil → PostAuthRouter (mirrors Android auth gate)"

requirements-completed: [REQ-015]

duration: 8min
completed: "2026-06-29"
---

# Phase 03 Plan 05: iOS Foundation Summary

**ClerkKit 1.1.5 integrated into iOS Xcode project; Swift @MainActor ClerkAuthRepository bridges session tokens to KMP shared HttpClient; ContentView routes by Clerk auth state with sign-in and org-picker placeholders.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-29T01:23:26Z
- **Completed:** 2026-06-29T01:31:24Z
- **Tasks:** 3 (Task 1 was human-action checkpoint, approved before this run)
- **Files modified:** 5

## Accomplishments

- `ClerkAuthRepository.swift` created as `@MainActor final class` with `@preconcurrency AuthRepository` conformance; resolves Swift 6 strict concurrency against ClerkKit 1.1.5's `@MainActor`-isolated `Clerk.shared` and `Session.session` properties
- `iosAppApp.swift` updated to call `Clerk.configure(publishableKey:)` in `init()` reading `ClerkPublishableKey` from Info.plist (T-3-11: SDK configured before any view renders)
- `ContentView.swift` provides auth-state routing using `@State private var clerk = Clerk.shared` (`@Observable`): spinner → sign-in placeholder → PostAuthRouter (org picker or stub)
- ClerkKit + ClerkKitUI `XCSwiftPackageProductDependency` entries added to `project.pbxproj` (were missing — Rule 3 blocking fix); `BUILD SUCCEEDED` verified via `xcodebuild`

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Xcode project + SPM + framework** — approved human action (no commit)
2. **Task 2: Swift ClerkAuthRepository** — `43adac5` (feat, inner repo `83b32a1`)
3. **Task 3: Clerk.configure + auth-routing ContentView** — `56ebad5` (feat, inner repo `93f7ed2`)

## Files Created/Modified

- `iosApp/iosApp/Auth/ClerkAuthRepository.swift` — `@MainActor @preconcurrency AuthRepository`; `getToken`/`refreshToken` via `Task { @MainActor in }`; `isSignedIn()` snapshot; `buildClient(baseUrl:)` factory
- `iosApp/iosApp/iosAppApp.swift` — `@main struct` with `init()` calling `Clerk.configure(publishableKey:)` from Info.plist
- `iosApp/iosApp/ContentView.swift` — `ContentView` (isLoaded/session routing) + `PostAuthRouter` (org membership count → picker or stub); `loadMemberships()` uses `getOrganizationMemberships()`
- `iosApp/iosApp.xcodeproj/project.pbxproj` — Added `XCSwiftPackageProductDependency` for ClerkKit and ClerkKitUI; wired to `PBXFrameworksBuildPhase` and `packageProductDependencies`
- `iosApp/Info.plist` — `ClerkPublishableKey` entry (`$(CLERK_PUBLISHABLE_KEY)` build variable)

## Decisions Made

- **`@MainActor` + `@preconcurrency` for Swift 6 KN bridge:** ClerkKit 1.1.5 marks `Clerk.shared` and `Session.session` as `@MainActor`. The class is `@MainActor` so all method bodies run on main actor. Obj-C conformance is `@preconcurrency AuthRepository` which defers isolation checking to runtime — safe because Obj-C ABI does not enforce Swift actor isolation.
- **Token API method names confirmed (Assumption A4 / A3 iOS analog):** `Session.getToken()` (no args) returns cached token; `Session.getToken(.init(skipCache: true))` forces network refresh. Same semantic as Android `GetTokenOptions(skipCache = false/true)`.
- **XCSwiftPackageProductDependency was missing (Rule 3 fix):** `packageProductDependencies` in the iosApp PBXNativeTarget was empty even though the `XCRemoteSwiftPackageReference "clerk-ios"` was declared. Added `XCSwiftPackageProductDependency` entries for ClerkKit and ClerkKitUI manually to unblock the build.
- **`iosAppApp.swift` retained as-is** (plan said `OneStepTwoApp.swift` but the existing file was `iosAppApp.swift` — updated in place per continuation notes).
- **`PBXFileSystemSynchronizedRootGroup`:** This Xcode 16 feature auto-discovers Swift files from the `iosApp/` directory. No per-file `PBXBuildFile` entry is needed in Sources build phase. New subdirectories (e.g., `Auth/`) are also auto-discovered.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ClerkKit product dependencies missing from project.pbxproj**
- **Found during:** Task 2 (first build attempt)
- **Issue:** `packageProductDependencies = ()` was empty in iosApp PBXNativeTarget. `XCRemoteSwiftPackageReference "clerk-ios"` was declared at the project level but no `XCSwiftPackageProductDependency` linked ClerkKit/ClerkKitUI to the app target. Build failed: `error: no such module 'ClerkKit'`
- **Fix:** Added `XCSwiftPackageProductDependency` section with ClerkKit and ClerkKitUI entries; added `PBXBuildFile` entries; wired to `PBXFrameworksBuildPhase` and `packageProductDependencies`
- **Files modified:** `iosApp/iosApp.xcodeproj/project.pbxproj`
- **Verification:** `BUILD SUCCEEDED` after fix
- **Committed in:** `43adac5`

**2. [Rule 1 - Bug] Swift 6 actor isolation: `@MainActor` class needs `@preconcurrency` conformance**
- **Found during:** Task 2 (second build attempt after Rule 3 fix)
- **Issue:** Adding `@MainActor` to the class made all methods main-actor-isolated, but `AuthRepository` is an Obj-C protocol with nonisolated requirements. Swift 6 error: `main actor-isolated instance method 'getToken(completionHandler:)' cannot be used to satisfy nonisolated requirement from protocol 'AuthRepository'`
- **Fix:** Added `@preconcurrency` to the `AuthRepository` conformance: `NSObject, @preconcurrency AuthRepository`. Defers isolation checking to runtime; correct because Obj-C ABI does not enforce Swift actor isolation.
- **Files modified:** `iosApp/iosApp/Auth/ClerkAuthRepository.swift`
- **Verification:** `BUILD SUCCEEDED` with no errors
- **Committed in:** `43adac5`

---

**Total deviations:** 2 auto-fixed (1 Rule 3 blocking, 1 Rule 1 bug)
**Impact on plan:** Both fixes necessary to achieve a buildable iOS target. No scope creep.

## Threat Model Coverage

| Threat | Disposition | Implementation |
|--------|-------------|----------------|
| T-3-11 Spoofing (unconfigured Clerk SDK) | Mitigated | `Clerk.configure` in `iosAppApp.init()` before any auth call |
| T-3-16 DoS (ClerkKit deadlock) | Mitigated | Assumption A4 resolved: ClerkKit token API is URLSession async; no lock across await |
| T-3-07 Token leakage to wrong host | Mitigated | Inherited from shared HttpClientFactory sendWithoutRequest allowlist (03-02) |
| T-3-SC SPM package integrity | Accepted | Package.resolved pins clerk-ios at commit `13f3c92` (version 1.1.5) |

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `Text("Sign in placeholder")` | ContentView.swift:30 | Real SignInView/SignUpView built in 03-06 |
| `Text("Org picker placeholder")` | ContentView.swift:56 | Real OrgPickerView built in 03-06 |
| `Text("Post-auth stub")` | ContentView.swift:65 | Full app shell is Phase 5; 03-06 wires navigation |

These stubs are intentional and gated — 03-06 replaces them with real auth screens.

## Issues Encountered

- Xcode CLI tools (not Xcode) was the active developer directory (`/Library/Developer/CommandLineTools`); `sudo xcode-select` not available without a terminal. Resolved by running xcodebuild with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` prefix.
- Nested `.git` at `iosApp/iosApp/` (created by user as part of Task 1 Xcode project setup): the main repo treats this as a gitlink (submodule-like at `160000` mode). Commits to Swift source files were staged in the inner repo first, then the gitlink was updated in the main repo. No `.gitmodules` file exists; the main repo records a bare gitlink.

## Next Phase Readiness

- 03-06 can build `SignInView`, `SignUpView`, `OrgPickerView` directly on top of the `ContentView` placeholder slots
- `ClerkAuthRepository` is instantiated on-demand; for production use, 03-06 or Phase 5 should inject a singleton instance into the shared `HttpClient` via `buildClient(baseUrl:)` using the staging/production API URL from STATE.md
- Apple code-signing (DEVELOPMENT_TEAM) is empty — user deferred this to pre-launch per continuation notes; no blocker for simulator builds

## Self-Check

- `iosApp/iosApp/Auth/ClerkAuthRepository.swift`: FOUND
- `iosApp/iosApp/iosAppApp.swift` (contains `Clerk.configure`): FOUND
- `iosApp/iosApp/ContentView.swift` (contains `struct ContentView`): FOUND
- Commit `43adac5` (Task 2): FOUND
- Commit `56ebad5` (Task 3): FOUND
- BUILD SUCCEEDED: CONFIRMED

## Self-Check: PASSED

---
*Phase: 03-authentication-family-model*
*Completed: 2026-06-29*
