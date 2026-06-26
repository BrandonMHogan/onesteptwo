---
phase: 01-foundation-infrastructure
reviewed: 2026-06-26T00:00:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - .github/workflows/ci.yml
  - .gitignore
  - androidApp/build.gradle.kts
  - androidApp/src/main/AndroidManifest.xml
  - androidApp/src/main/kotlin/com/onesteptwo/android/MainActivity.kt
  - api/codegen.yaml
  - api/openapi.yaml
  - backend/Makefile
  - backend/cmd/migrate/main.go
  - backend/cmd/server/main.go
  - backend/db/migrations/00001_init.sql
  - backend/go.mod
  - backend/go.sum
  - backend/internal/api/generated.go
  - backend/internal/api/problem.go
  - backend/internal/api/server.go
  - backend/internal/api/server_test.go
  - backend/railway.json
  - build.gradle.kts
  - gradle.properties
  - gradle/libs.versions.toml
  - gradle/wrapper/gradle-wrapper.properties
  - gradlew
  - gradlew.bat
  - settings.gradle.kts
  - shared/build.gradle.kts
  - shared/src/commonMain/sqldelight/migrations/1.sqm
findings:
  critical: 2
  warning: 8
  info: 2
  total: 12
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-06-26
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

This review covers the Phase 1 foundation infrastructure: Go backend (oapi-codegen, goose migrations, Railway deployment), Kotlin Multiplatform shared module (SQLDelight), Android application scaffold, and GitHub Actions CI pipeline.

The Go backend server and API layer are structurally sound. The KMP build graph is correctly wired. The CI pipeline covers the right surface area for Go and Android. Two blockers require fixes before this infrastructure can be used for subsequent phases without silent failures: Railway will never run database migrations, and a release Android build will fail on a missing ProGuard file. Eight warnings represent correctness and security gaps that will compound as real features land on top of this scaffold.

---

## Critical Issues

### CR-01: Railway deployment never runs database migrations

**File:** `backend/railway.json:4-6`

**Issue:** The `buildCommand` builds only the server binary (`./cmd/server`). The `startCommand` launches the server directly with no pre-migration step. The `cmd/migrate` binary is never built in the Railway pipeline, and goose migrations are never applied. On a fresh Railway environment — or after any schema-changing migration in Phase 2+ — the server will start against an un-migrated database. Phase 1's empty migration masks the problem; Phase 2 schema additions will cause runtime failures.

**Fix:** Either (a) build both binaries and chain migration in `startCommand`:
```json
{
  "build": {
    "buildCommand": "go build -o /app/server ./cmd/server && go build -o /app/migrate ./cmd/migrate"
  },
  "deploy": {
    "startCommand": "/app/migrate && /app/server"
  }
}
```
Or (b) embed migrations in the server binary and auto-migrate on startup using `goose.Up` with an `embed.FS`, which removes the need for a separate migrate binary entirely. Option (b) is preferred for Railway's single-container model.

---

### CR-02: Missing `proguard-rules.pro` blocks all release builds

**File:** `androidApp/build.gradle.kts:24`

**Issue:** The release build configuration references `"proguard-rules.pro"`:
```kotlin
proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
```
This file does not exist anywhere in the repository (`git ls-files` confirms absence). AGP 8.x treats all listed ProGuard files as required task inputs. `assembleRelease` (and `bundleRelease`) will fail with a `FileNotFoundException`. The CI only runs `assembleDebug` (minification disabled), so this failure is invisible until a release artifact is attempted.

**Fix:** Create a minimal `androidApp/proguard-rules.pro`:
```
# Add project-specific ProGuard rules here.
# For now, keep all public APIs and suppress warnings on known-missing Play Services classes.
-keepattributes *Annotation*
```
The file must exist even if empty; the default AGP rules handle standard obfuscation.

---

## Warnings

### WR-01: `cmd/migrate/main.go` hardcodes relative migration path

**File:** `backend/cmd/migrate/main.go:28`

**Issue:** `goose.Up(db, "db/migrations")` resolves relative to the process working directory at runtime. When the compiled binary is deployed anywhere other than the `backend/` directory root (e.g., `/app/migrate` in a Railway container, or any CI invocation from a different working directory), goose will not find the migration files and will fail silently or with a confusing "no migrations found" error.

**Fix:** Embed the migrations directory using `embed.FS` and pass it to goose:
```go
import "embed"

//go:embed db/migrations/*.sql
var migrations embed.FS

// Then:
goose.SetBaseFS(migrations)
if err := goose.Up(db, "db/migrations"); err != nil { ... }
```
This makes the binary self-contained regardless of working directory.

---

### WR-02: `android:allowBackup="true"` on an app handling children's PII

**File:** `androidApp/src/main/AndroidManifest.xml:5`

**Issue:** `allowBackup="true"` enables ADB backup and cloud auto-backup of all app-private storage. For an app that will store children's health and behavioural data (potty training events, etc.), this exposes sensitive PII via ADB on any developer machine connected to the device, and via Google's cloud backup if the user's account is compromised.

**Fix:** Set `android:allowBackup="false"` unless a specific, E2E-encrypted backup strategy is designed. If backup is desired in future, implement `BackupAgent` with client-side encryption before re-enabling:
```xml
<application
    android:allowBackup="false"
    ...>
```

---

### WR-03: Google Services plugin applied with no Firebase SDK dependencies

**File:** `androidApp/build.gradle.kts:5, 43-49`

**Issue:** `alias(libs.plugins.google.services)` is applied and `androidApp/src/debug/google-services.json` is committed, so the plugin processes Firebase project configuration at build time. However, no `com.google.firebase:*` libraries are declared in the `dependencies` block. The plugin will run and consume the config, but Firebase will not be initialized at runtime (no `FirebaseApp` bootstrapping), making the plugin overhead entirely wasted and the committed JSON misleading about the app's actual capabilities.

**Fix:** Either add the minimum Firebase dependency needed for the intended feature (e.g., `implementation(platform("com.google.firebase:firebase-bom:..."))`) or remove the `google-services` plugin and committed JSON until Firebase is actually integrated.

---

### WR-04: `sql.Open` without connection verification in `cmd/migrate`

**File:** `backend/cmd/migrate/main.go:18-22`

**Issue:** `sql.Open("postgres", dsn)` validates the DSN format but does not open a network connection. When the database is unreachable (wrong host, firewall, service not yet ready), the failure surfaces inside `goose.Up()` with a generic driver error rather than a clear connectivity message. In Railway's startup sequence where the Postgres add-on may not be ready, this produces confusing output.

**Fix:** Add an explicit ping with a retry window:
```go
db, err := sql.Open("postgres", dsn)
if err != nil {
    log.Fatal(err)
}
defer db.Close()

if err := db.Ping(); err != nil {
    log.Fatalf("database unreachable: %v", err)
}
```

---

### WR-05: CI does not verify generated code is in sync with OpenAPI spec

**File:** `.github/workflows/ci.yml`

**Issue:** `backend/internal/api/generated.go` is committed (correctly, per D-03), but the CI pipeline has no step to verify it matches the current `api/openapi.yaml`. A developer can modify `api/openapi.yaml` without re-running `make generate`, and the pipeline will compile the stale generated code against the new spec without flagging the drift. Over time this silently diverges the contract from the implementation.

**Fix:** Add a CI step after Go build/test:
```yaml
- name: Verify generated API code is up to date
  run: |
    make -C backend generate
    git diff --exit-code backend/internal/api/generated.go
```

---

### WR-06: `healthcheckTimeout: 300` delays failure detection by 5 minutes

**File:** `backend/railway.json:9`

**Issue:** A 300-second (5-minute) health check timeout means Railway will wait up to 5 full minutes before marking a broken deployment as failed and triggering a rollback. A server that panics on startup (e.g., due to missing env vars or un-migrated schema in Phase 2) will hold the deployment in a broken state for 5 minutes per attempt with `restartPolicyMaxRetries: 5` — potentially 25+ minutes of downtime before Railway gives up.

**Fix:** Lower the timeout to a value consistent with expected cold-start time (typically 30–60 seconds for a Go binary):
```json
"healthcheckTimeout": 60
```

---

### WR-07: `WriteProblem` silently discards JSON encoding error

**File:** `backend/internal/api/problem.go:23`

**Issue:**
```go
_ = json.NewEncoder(w).Encode(ProblemDetail{ ... })
```
The blank identifier discards any encoding error. For the current `ProblemDetail` struct (only `string` and `int` fields), encoding will never fail in practice. But the pattern teaches future contributors that it is acceptable to discard errors from `ResponseWriter.Write`-path calls. If the struct gains a field that can fail encoding (e.g., a custom `json.Marshaler`), the error will be silently dropped, and clients will receive a truncated or empty body with the already-committed status code.

**Fix:** Log the error:
```go
if err := json.NewEncoder(w).Encode(ProblemDetail{...}); err != nil {
    // Response header already sent; log for operator visibility.
    log.Printf("WriteProblem: failed to encode response body: %v", err)
}
```

---

### WR-08: CI Android step builds only; no tests run for any Kotlin target

**File:** `.github/workflows/ci.yml:41`

**Issue:** The CI runs `./gradlew :androidApp:assembleDebug` but no test tasks. There are no shared module unit tests or Android unit tests run in the pipeline. This means any Kotlin/KMP logic added to `:shared` or `:androidApp` has no CI enforcement. The Go tests are gated; the Kotlin side is not.

**Fix:** Add test tasks after the build:
```yaml
- name: Run KMP and Android unit tests
  run: ./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest
```
Even if these tasks currently produce no test output (no tests exist yet), wiring them in now ensures coverage gates apply as soon as tests are added.

---

## Info

### IN-01: SKIE plugin declared but never applied to `:shared`

**File:** `build.gradle.kts:7`, `shared/build.gradle.kts`

**Issue:** `alias(libs.plugins.skie) apply false` registers the SKIE plugin in the root build, and the version is pinned in `libs.versions.toml`. However, `shared/build.gradle.kts` never applies SKIE (`alias(libs.plugins.skie)` is absent). SKIE generates Swift-friendly Kotlin/Native API wrappers for iOS — without applying it to `:shared`, iOS targets will get raw Kotlin/Native interfaces. This may be intentional for Phase 1 (no iOS app yet), but the unused plugin declaration creates confusion about intent.

**Fix:** Either apply SKIE to `shared/build.gradle.kts` when iOS development begins, or remove the declaration until it is needed.

---

### IN-02: `retries=0` in Gradle wrapper prevents retry on transient network failure

**File:** `gradle/wrapper/gradle-wrapper.properties:6`

**Issue:** `retries=0` means the Gradle wrapper will make exactly one attempt to download the Gradle distribution. A transient network hiccup on CI (not uncommon with hosted runners) will fail the build immediately with no retry. The default Gradle wrapper value is 3 retries.

**Fix:**
```properties
retries=3
```

---

_Reviewed: 2026-06-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
