---
phase: 01-foundation-infrastructure
plan: "02"
subsystem: mobile-build
tags: [gradle, kmp, sqldelight, android, compose, toolchain]
dependency_graph:
  requires: []
  provides:
    - gradle-wrapper-8.10.2
    - version-catalog-libs-versions-toml
    - shared-kmp-module
    - sqldelight-1sqm-migration
    - android-app-module
    - debug-apk
  affects:
    - all future gradle modules
    - sqldelight schema migrations (phase 2+)
    - ios-xcframework (phase 3+)
tech_stack:
  added:
    - Kotlin Multiplatform 2.0.21 (shared module)
    - Android Gradle Plugin 8.7.3
    - SQLDelight 2.3.2 (with verifyMigrations)
    - SKIE 0.10.12
    - Jetpack Compose (BOM 2024.09.00)
    - Gradle 8.10.2 wrapper
    - JDK 21 Temurin
    - Android SDK 36 (compileSdk/targetSdk), minSdk 29
  patterns:
    - Version catalog (libs.versions.toml) as single source of truth for all dependency versions (D-07)
    - Gradle modules :shared (KMP) + :androidApp only; iosApp is Xcode-only (D-08)
    - SQLDelight verifyMigrations enforcing REQ-025 from day one
    - google-services.json per build-type-source-directory (debug committed, release gitignored)
key_files:
  created:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - shared/build.gradle.kts
    - shared/src/commonMain/sqldelight/migrations/1.sqm
    - androidApp/build.gradle.kts
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt
    - androidApp/src/debug/google-services.json
    - .gitignore
    - gradle.properties
    - gradle/wrapper/gradle-wrapper.properties
    - gradle/wrapper/gradle-wrapper.jar
    - gradlew
    - gradlew.bat
  modified: []
decisions:
  - "Added android-library plugin to catalog and root build (com.android.library required for android{} DSL in KMP shared module)"
  - "Added kotlin-compose plugin (org.jetbrains.kotlin.plugin.compose) — mandatory from Kotlin 2.0 when compose=true"
  - "Committed debug google-services.json placeholder (structurally valid; real staging config from Plan 01-04)"
metrics:
  duration: "~30 minutes"
  completed: "2026-06-26"
  tasks_completed: 3
  tasks_total: 3
  files_created: 15
  files_modified: 2
---

# Phase 01 Plan 02: KMP Monorepo Scaffold and Android App Assembly Summary

**One-liner:** Gradle 8.10.2 KMP monorepo with SQLDelight verifyMigrations and a Compose debug APK from a clean machine.

## What Was Built

The mobile build skeleton for OneStepTwo: JDK 21 Temurin + Android SDK 36 toolchain, a pinned Gradle 8.10.2 wrapper, a unified `libs.versions.toml` version catalog, the root + `:shared` (KMP) + `:androidApp` (Compose) build files, the day-one SQLDelight `1.sqm` placeholder migration, and a minimal `MainActivity` so `assembleDebug` produces a real APK.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Toolchain installation and repo hygiene | dbd3f99 | .gitignore, gradle.properties, gradlew, gradle-wrapper.properties |
| 2 | Version catalog, root + :shared KMP build, SQLDelight 1.sqm | 0287b65 | gradle/libs.versions.toml, settings.gradle.kts, build.gradle.kts, shared/build.gradle.kts, 1.sqm |
| 3 | :androidApp Compose module, MainActivity, debug Firebase placeholder | cb9f895 | androidApp/build.gradle.kts, AndroidManifest.xml, MainActivity.kt, google-services.json |

## Verification Results

- `./gradlew --version` → Gradle 8.10.2 on JVM 21.0.11 Temurin
- `./gradlew :shared:generateCommonMainOneStepTwoDatabaseInterface --no-daemon` → BUILD SUCCESSFUL (REQ-025 1.sqm valid)
- `./gradlew :androidApp:assembleDebug --no-daemon` → BUILD SUCCESSFUL; `androidApp-debug.apk` produced
- `git check-ignore androidApp/src/release/google-services.json local.properties` → both listed (T-02-01 mitigated)
- `git check-ignore backend/internal/api/generated.go` → not listed (D-03 compliance confirmed)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added com.android.library plugin to shared/build.gradle.kts**
- **Found during:** Task 2
- **Issue:** The `android { ... }` DSL block in `shared/build.gradle.kts` requires the `com.android.library` Gradle plugin to be applied. The PATTERNS.md example omits this plugin from the shared module. Without it, Kotlin script compilation fails with "Unresolved reference: android".
- **Fix:** Added `android-library` plugin alias to `gradle/libs.versions.toml` and `build.gradle.kts` (root, apply false), then applied it in `shared/build.gradle.kts`.
- **Files modified:** `gradle/libs.versions.toml`, `build.gradle.kts`, `shared/build.gradle.kts`
- **Commit:** 0287b65 (included in Task 2 commit)

**2. [Rule 1 - Bug] Added kotlin-compose plugin (required by Kotlin 2.0 when compose=true)**
- **Found during:** Task 3
- **Issue:** Starting from Kotlin 2.0, the Compose Compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) must be applied explicitly when `buildFeatures { compose = true }` is set. Without it, `assembleDebug` fails with "Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required".
- **Fix:** Added `kotlin-compose` alias to `gradle/libs.versions.toml`, declared it in root `build.gradle.kts` with `apply false`, and applied it in `androidApp/build.gradle.kts`.
- **Files modified:** `gradle/libs.versions.toml`, `build.gradle.kts`, `androidApp/build.gradle.kts`
- **Commit:** cb9f895 (included in Task 3 commit)

## Known Stubs

- `androidApp/src/debug/google-services.json`: Structurally valid placeholder with project_id "onesteptwo-staging" and package_name "com.onesteptwo.android". The `current_key` contains the string "placeholder-replace-with-real-staging-key-in-plan-01-04". Plan 01-04 (Firebase provisioning) replaces this file with the real staging project config.

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced beyond what the threat model covers.

| Flag | File | Description |
|------|------|-------------|
| T-02-01 mitigated | .gitignore | `androidApp/src/release/google-services.json` gitignored; confirmed via `git check-ignore` |
| T-02-02 mitigated | gradle/wrapper/gradle-wrapper.properties | Pinned to gradle-8.10.2-bin.zip; wrapper jar committed |

## Self-Check: PASSED

All 9 key files confirmed present on disk. All 3 task commits (dbd3f99, 0287b65, cb9f895) confirmed in git log.
