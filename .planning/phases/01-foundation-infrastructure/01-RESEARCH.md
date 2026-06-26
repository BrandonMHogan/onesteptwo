# Phase 1: Foundation & Infrastructure - Research

**Researched:** 2026-06-26
**Domain:** Monorepo scaffold · Go backend · Railway deployment · PostgreSQL backup · Clerk/Firebase provisioning · KMP Gradle · SQLDelight migrations · GitHub Actions CI
**Confidence:** MEDIUM (stack is well-established; external service provisioning steps are training-knowledge ASSUMED)

---

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** iOS CI is skipped for Phase 1. iOS build verification is done locally until the iOS codebase has meaningful code (expected Phase 3 or later). GitHub Actions runs on Linux runners only.
- **D-02:** GitHub Actions CI pipeline covers: Go build + test + Android (KMP) build on a single Linux runner job. Migration apply (1.sqm) is included as part of the Go test step.
- **D-03:** `generated.go` (oapi-codegen output) is committed to the repo. CI verifies it compiles — no codegen step runs in CI. Regenerate locally via `make generate`.
- **D-04:** Branch-based Railway auto-deploy: `main` branch → staging Railway service auto-deploys; `production` branch → production Railway service auto-deploys.
- **D-05:** Production releases happen via manual PR from `main` → `production`. No automated promotion — merging the PR is the release action.
- **D-06:** `/api/openapi.yaml` starts with only the `/healthz` endpoint. Each subsequent phase adds its own endpoint definitions as they are implemented.
- **D-07:** Use `gradle/libs.versions.toml` version catalog for centralized dependency version management across all Gradle modules.
- **D-08:** Gradle module structure: `:shared` (KMP — commonMain, androidMain, iosMain source sets) + `:androidApp` (Jetpack Compose app). The iOS app (`iosApp/`) is an Xcode project that consumes the `:shared` KMP XCFramework — it is not a Gradle module.

### Claude's Discretion

- Exact Gradle wrapper version (use latest stable compatible with Kotlin 2.0.21 — see Pitfalls for version constraint)
- Go HTTP router choice (stdlib `net/http` vs `chi` vs `gorilla/mux` — keep it minimal for a healthcheck-only backend)
- Specific Railway `railway.json` / Dockerfile vs Nixpacks auto-detection
- goose migration directory location within the backend (`db/migrations/` is conventional)
- GitHub Actions job naming and structure details

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

---

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REQ-001 | GET /healthz returns 200 OK with no body, no auth required, registered before other endpoints | Go stdlib `net/http` + oapi-codegen generated stub; handler registered via `HandlerFromMux` before any /v1/ routes |
| REQ-002 | All API endpoints prefixed /v1/ | Established at the ServeMux level via route registration pattern in Phase 1; no /v1/ routes exist yet but pattern is set |
| REQ-025 | SQLDelight .sqm migrations start at 1.sqm from day one, no numbering gaps | SQLDelight 2.3.2 `verifyMigrations.set(true)` in Gradle; `1.sqm` file at `shared/src/commonMain/sqldelight/migrations/`; build fails if gap exists |
| REQ-NF-001 | RFC 7807 Problem Details error format (type, title, status, detail) | Hand-rolled `ProblemDetail` struct + `WriteProblem` helper established in Phase 1 for all future phases to use |

</phase_requirements>

---

## Summary

Phase 1 is a pure infrastructure bootstrapping phase — no application logic, no user-facing features. Every task either creates a file from scratch or registers a service in an external dashboard. The Go backend is minimal by design (one endpoint, stdlib only), and the Gradle monorepo follows the standard KMP two-module pattern (`:shared` + `:androidApp`). The most significant complexity comes from external service wiring: Railway branch-based deploys require creating the `production` branch and linking it to a second Railway service; the R2 backup requires a separate Railway cron service with five env vars; Firebase requires two separate project accounts.

The hardest constraint to get right at this phase is the Gradle wrapper version. Kotlin 2.0.21 is officially supported up to Gradle 8.10.x only — Gradle 9.x is not supported and will cause build failures. Use Gradle 8.10.2 wrapper. The second hardest constraint is Java 21, which is not installed on this machine and must be present before any Gradle task runs. Both conditions must be met before the first `./gradlew` invocation.

SQLDelight `1.sqm` must exist the moment the first `.sq` schema file is written — this requirement (REQ-025) is enforced at build time when `verifyMigrations = true`. An empty `1.sqm` file is valid and serves as the placeholder for Phase 1; real schema creation happens in Phase 2.

**Primary recommendation:** Use Go stdlib `net/http` with oapi-codegen `std-http-server` mode; use Gradle 8.10.2 wrapper with Kotlin 2.0.21; validate SQLDelight 1.sqm with `verifyMigrations`; run goose `validate` in CI (no DB needed); use Railway's one-click Postgres-to-R2 backup template.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| GET /healthz | API / Backend (Go) | — | Server readiness is a backend infrastructure concern; Railway pings it |
| RFC 7807 error helper | API / Backend (Go) | — | Response formatting is backend's responsibility; shared by all future Go handlers |
| /v1/ route prefix convention | API / Backend (Go) | — | Established at ServeMux registration level |
| PostgreSQL provisioning | Database / Storage (Railway) | — | Managed PaaS; no schema yet |
| pg_dump → R2 backup | Infrastructure / Railway cron | — | Out-of-band scheduled job; not application code |
| Clerk provisioning | Auth Provider (external) | — | Dashboard-level setup; SDK keys flow into mobile app in Phase 3 |
| Firebase project setup | Push / External | — | Dashboard-level; SDK integration deferred to Phase 8 |
| goose migration init | Database / Storage | API / Backend | Migration runner sits in backend cmd/; schema is DB-tier |
| SQLDelight 1.sqm | Mobile Client | — | On-device schema versioning; compiled into :shared KMP artifact |
| Gradle monorepo scaffold | Build / DevOps | — | Not a runtime tier; determines how code is organized |
| GitHub Actions CI | Build / DevOps | — | Validates both Go and Android tiers compile and pass tests |
| railway.json | Infrastructure | — | Deployment config; tells Railway how to build and run the Go binary |

---

## Standard Stack

### Go Backend

| Package | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `net/http` (stdlib) | Go 1.25.5 | HTTP server + routing for /healthz | Zero deps; oapi-codegen targets it directly; sufficient for Phase 1 |
| `github.com/pressly/goose/v3` | v3.27.1 [VERIFIED: pkg.go.dev] | PostgreSQL schema migration runner | De-facto standard for Go DB migrations; supports `-- +goose Up/Down` SQL |
| `github.com/oapi-codegen/oapi-codegen/v2` | v2.7.1 [VERIFIED: pkg.go.dev] | Generates typed handler stubs from openapi.yaml | Locked by D-03; generates `ServerInterface` for `net/http` stdlib |
| `github.com/oapi-codegen/runtime` | v1.4.2 [VERIFIED: pkg.go.dev] | Runtime support for oapi-codegen generated code | Required runtime dep when using oapi-codegen |

### KMP / Gradle

| Package | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin Multiplatform plugin | 2.0.21 [ASSUMED] | `:shared` module KMP compilation | Pinned in PROJECT.md; do not upgrade without also upgrading SKIE |
| Android Gradle Plugin (AGP) | 8.7.3 [ASSUMED] | `:androidApp` Android build | 8.7.x is stable and verified compatible with Kotlin 2.0.21 |
| `app.cash.sqldelight` (Gradle plugin) | 2.3.2 [ASSUMED] | SQLDelight code generation from .sq files | Latest stable 2.x; `verifyMigrations` enforces REQ-025 |
| `app.cash.sqldelight:runtime` | 2.3.2 [ASSUMED] | commonMain SQLDelight runtime | Required alongside plugin |
| `app.cash.sqldelight:android-driver` | 2.3.2 [ASSUMED] | androidMain SQLite driver | Standard Android SQLite driver for SQLDelight |
| `app.cash.sqldelight:native-driver` | 2.3.2 [ASSUMED] | iosMain SQLite driver | Standard iOS SQLite driver for SQLDelight |
| `co.touchlab.skie` | 0.10.12 [ASSUMED] | Swift interop enhancer for KMP XCFramework | Locked (PROJECT.md); compatible with Kotlin 2.0.21; declared in :shared even though iOS code is Phase 3+ |
| Gradle wrapper | 8.10.2 [VERIFIED: gradle.org] | Build system | Latest stable 8.10.x; required — Kotlin 2.0.21 does NOT support Gradle 9.x |
| Java toolchain | 21 (JDK Temurin) | Kotlin compilation target | Locked (PROJECT.md); must be installed locally |

### Android App (scaffolding only in Phase 1)

| Package | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose BOM | latest [ASSUMED] | Consistent Compose library versions | Required for :androidApp to compile as a Compose project |
| `androidx.activity:activity-compose` | via BOM [ASSUMED] | Compose entry point in Activity | Needed for MainActivity |

**Installation — Go backend:**
```bash
cd backend
go mod init github.com/BrandonMHogan/onesteptwo/backend
go get github.com/pressly/goose/v3@v3.27.1
go get github.com/oapi-codegen/runtime@v1.4.2
# oapi-codegen is a tool, not a runtime dep — install via go run:
# go run github.com/oapi-codegen/oapi-codegen/v2/cmd/oapi-codegen@v2.7.1 ...
```

**Version verification performed:**
```
go list -m -json github.com/pressly/goose/v3@latest  → v3.27.1
go list -m -json github.com/oapi-codegen/oapi-codegen/v2@latest → v2.7.1
go list -m -json github.com/clerk/clerk-sdk-go/v2@latest → v2.7.0
go list -m -json github.com/oapi-codegen/runtime@latest → v1.4.2
Gradle latest 8.10.x → 8.10.2 (verified at gradle.org)
```

---

## Package Legitimacy Audit

This phase installs Go modules and Gradle plugins — not npm packages. The package legitimacy seam targets npm; these ecosystems use different trust mechanisms (Go module proxy + checksum DB; Maven Central / Google Maven). Verification was performed directly against registries via `go list -m` (Go module proxy) and web confirmation.

| Package | Registry | Source | Verdict | Disposition |
|---------|----------|--------|---------|-------------|
| `github.com/pressly/goose/v3` | Go module proxy | Official pressly org; years-established | OK | Approved |
| `github.com/oapi-codegen/oapi-codegen/v2` | Go module proxy | Official oapi-codegen org | OK | Approved |
| `github.com/oapi-codegen/runtime` | Go module proxy | Official oapi-codegen org | OK | Approved |
| `app.cash.sqldelight` Gradle plugin | Maven Central | Cash App (Square subsidiary); years-established | OK | Approved |
| `co.touchlab.skie` Gradle plugin | Maven Central | Touchlab; official SKIE maintainer | OK | Approved |
| `com.clerk:clerk-android-api` | Maven Central | Official Clerk org; v1.0.31 confirmed on GitHub releases | OK | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
git push → main branch
       │
       ├──► GitHub Actions (ubuntu-latest)
       │       ├── Go: go test ./... (includes TestHealthz)
       │       ├── Go: goose validate (no DB needed)
       │       └── Android: ./gradlew :androidApp:assembleDebug
       │
       └──► Railway (auto-deploy on main push)
               └── staging service
                     ├── Nixpacks: go build -o server .
                     ├── start: ./server
                     ├── Healthcheck: GET /healthz → 200
                     └── Private network → staging PostgreSQL

git push → production branch (manual PR from main)
       └──► Railway (auto-deploy on production push)
               └── production service
                     ├── Same build as staging
                     └── Private network → production PostgreSQL

Railway cron service (separate)
       └── pg_dump → gzip → upload → Cloudflare R2 bucket
               ENV: DATABASE_URL, R2_ENDPOINT, R2_ACCESS_KEY,
                    R2_SECRET_KEY, R2_BUCKET_NAME, BACKUP_TIME
```

### Recommended Project Structure

```
/ (repo root)
├── api/
│   └── openapi.yaml              # OpenAPI spec (starts with /healthz only)
├── backend/
│   ├── cmd/
│   │   ├── server/
│   │   │   └── main.go           # Entry: HTTP server, port from PORT env var
│   │   └── migrate/
│   │       └── main.go           # Entry: run goose up / validate
│   ├── db/
│   │   └── migrations/
│   │       └── 00001_init.sql    # First goose migration (empty schema)
│   ├── internal/
│   │   └── api/
│   │       ├── generated.go      # oapi-codegen output — COMMITTED
│   │       ├── problem.go        # RFC 7807 ProblemDetail struct + WriteProblem()
│   │       └── server.go         # Implements oapi-codegen ServerInterface
│   ├── go.mod                    # module: github.com/BrandonMHogan/onesteptwo/backend
│   ├── go.sum
│   └── Makefile                  # generate: oapi-codegen; migrate-up, migrate-down targets
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       └── commonMain/
│           └── sqldelight/
│               ├── com/onesteptwo/db/   # .sq files go here in Phase 2+
│               └── migrations/
│                   └── 1.sqm            # Empty — placeholder for Phase 2 schema
├── androidApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── debug/
│       │   └── google-services.json    # Staging Firebase project
│       └── release/
│           └── google-services.json   # Production Firebase project
├── iosApp/                             # Xcode project — NOT a Gradle module
├── frontend/                           # SvelteKit (Phase 9)
├── docs/
├── gradle/
│   └── libs.versions.toml              # D-07: single version catalog
├── build.gradle.kts                    # Root: applies plugins with `apply false`
├── settings.gradle.kts                 # Includes :shared, :androidApp
├── gradlew                             # Wrapper: Gradle 8.10.2
├── gradlew.bat
├── railway.json                        # Deploy config (backend/ subtree)
└── .github/
    └── workflows/
        └── ci.yml                      # Go + Android CI (Linux only, D-01)
```

### Pattern 1: Go stdlib net/http with oapi-codegen (std-http-server mode)

**What:** oapi-codegen generates a `ServerInterface` and a `HandlerFromMux` registration function. You implement the interface, register the handler, and serve.

**When to use:** Phase 1 and all subsequent Go handler phases (REQ-001, D-06).

**Example — codegen.yaml:**
```yaml
# Source: github.com/oapi-codegen/oapi-codegen README
package: api
generate:
  std-http-server: true
  models: true
output: backend/internal/api/generated.go
output-options:
  skip-prune: false
```

**Example — Makefile target:**
```makefile
# Source: oapi-codegen docs
.PHONY: generate
generate:
	go run github.com/oapi-codegen/oapi-codegen/v2/cmd/oapi-codegen@v2.7.1 \
		-config api/codegen.yaml \
		api/openapi.yaml
```

**Example — server wiring:**
```go
// Source: oapi-codegen std-http-server pattern
package main

import (
    "net/http"
    "os"
    "github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

func main() {
    port := os.Getenv("PORT")
    if port == "" {
        port = "8080"
    }
    srv := &api.Server{}
    mux := http.NewServeMux()
    api.HandlerFromMux(srv, mux)  // generated registration
    http.ListenAndServe(":"+port, mux)
}
```

### Pattern 2: goose SQL migration format

**What:** SQL migration files with `-- +goose Up` / `-- +goose Down` markers. goose manages a version table in the target database.

**When to use:** All PostgreSQL schema changes (Phase 2+). Phase 1 only initializes the empty first migration.

**Example — 00001_init.sql:**
```sql
-- Source: pressly/goose docs
-- +goose Up
-- Phase 1: empty init migration — schema tables added in Phase 2

-- +goose Down
-- no-op
```

**Example — migration runner (cmd/migrate/main.go):**
```go
// Source: pressly/goose docs pattern
package main

import (
    "database/sql"
    "log"
    "os"
    _ "github.com/lib/pq"
    "github.com/pressly/goose/v3"
)

func main() {
    dsn := os.Getenv("DATABASE_URL")
    db, err := sql.Open("postgres", dsn)
    if err != nil {
        log.Fatal(err)
    }
    if err := goose.SetDialect("postgres"); err != nil {
        log.Fatal(err)
    }
    if err := goose.Up(db, "db/migrations"); err != nil {
        log.Fatal(err)
    }
}
```

### Pattern 3: SQLDelight Gradle setup for KMP (Phase 1 — empty migration only)

**What:** SQLDelight plugin declared in `:shared/build.gradle.kts` with `verifyMigrations = true` so the 1.sqm file is validated at build time.

**When to use:** Phase 1 scaffolding; actual `.sq` schema tables are written in Phase 2.

**Example — shared/build.gradle.kts:**
```kotlin
// Source: SQLDelight 2.x multiplatform docs
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "shared" }
    }
    jvmToolchain(21)

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
}

sqldelight {
    databases {
        create("OneStepTwoDatabase") {
            packageName.set("com.onesteptwo.db")
            verifyMigrations.set(true)    // fails build if sqm file is invalid
        }
    }
}
```

### Pattern 4: railway.json deployment config

**What:** Config-as-code file that tells Railway how to build and start the Go binary. Must be at the repository root (or in the service subdirectory if using monorepo path mapping). [ASSUMED: Railway reads `railway.json` from the configured build root]

**When to use:** Phase 1 — sets up Railway to watch the `backend/` subdirectory.

**Example — railway.json:**
```json
{
  "$schema": "https://railway.com/railway.schema.json",
  "build": {
    "builder": "NIXPACKS"
  },
  "deploy": {
    "healthcheckPath": "/healthz",
    "healthcheckTimeout": 300,
    "restartPolicyType": "ALWAYS",
    "restartPolicyMaxRetries": 5
  }
}
```

**Note:** Nixpacks auto-detects Go and runs `go build` to produce the binary. The `startCommand` is inferred as the built binary. `PORT` env var is injected by Railway automatically.

### Pattern 5: libs.versions.toml — version catalog structure

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
sqldelight = "2.3.2"
skie = "0.10.12"
compose-bom = "2024.09.00"

[libraries]
sqldelight-runtime = { group = "app.cash.sqldelight", name = "runtime", version.ref = "sqldelight" }
sqldelight-android-driver = { group = "app.cash.sqldelight", name = "android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { group = "app.cash.sqldelight", name = "native-driver", version.ref = "sqldelight" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
skie = { id = "co.touchlab.skie", version.ref = "skie" }
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
```

### Pattern 6: GitHub Actions CI (Go + Android, Linux-only per D-01)

```yaml
# Source: Kotlin KMP official docs + GitHub Actions standard patterns
name: CI
on:
  push:
    branches: [main, production]
  pull_request:
    branches: [main, production]

env:
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx4096M -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true"

jobs:
  ci:
    name: Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # --- Go ---
      - uses: actions/setup-go@v5
        with:
          go-version-file: backend/go.mod
          cache-dependency-path: backend/go.sum
      - name: Go build
        run: cd backend && go build ./...
      - name: Go test
        run: cd backend && go test ./...
      - name: Validate migrations
        run: cd backend && go run github.com/pressly/goose/v3/cmd/goose@v3.27.1 \
               -dir db/migrations validate

      # --- Android / KMP ---
      - uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"
      - uses: gradle/actions/setup-gradle@v3
      - name: Write local.properties
        run: echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
      - name: Build Android (assembleDebug)
        run: ./gradlew :androidApp:assembleDebug
```

**Note:** `google-services.json` for debug must exist at `androidApp/src/debug/google-services.json` for the `assembleDebug` task to succeed. A placeholder/staging config can be committed for CI; the production config must never be committed.

### Anti-Patterns to Avoid

- **Do not use Gradle 9.x with Kotlin 2.0.21:** Kotlin 2.0.21 officially supports Gradle up to 8.10.x. Using 9.x will produce build failures.
- **Do not hardcode the PORT in the Go binary:** Railway injects `PORT` dynamically. A hardcoded port causes "service unavailable" on Railway.
- **Do not wrap 1.sqm content in BEGIN/END TRANSACTION:** SQLDelight crashes on some drivers when migrations include explicit transactions.
- **Do not put google-services.json at the app module root:** It must be in the build-type-specific source directory (`src/debug/` or `src/release/`) for multi-environment Firebase to work.
- **Do not run oapi-codegen in CI:** D-03 says `generated.go` is committed. CI only compiles it.
- **Do not name migration files out of order:** SQLDelight and goose both validate sequential numbering. A gap (e.g., 1.sqm, 3.sqm) fails the build or migration run.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL migration versioning | Custom migration table + runner | `pressly/goose/v3` | goose handles version table, ordering, rollbacks, transactions, and `-- +goose` annotation parsing |
| OpenAPI type definitions | Manually written handler func signatures | `oapi-codegen` | oapi-codegen generates type-safe stubs; hand-rolled types diverge from the spec silently |
| PostgreSQL backup to R2 | Custom pg_dump bash script + cron | Railway "Postgres to R2 Backup" template | The template handles pg_dump version detection, compression, R2 upload, retention cleanup, and error logging |
| HTTP test server | Spinning up a real server in tests | `net/http/httptest.NewServer` | Go stdlib; no ports, no OS resources, fast, isolated |
| RFC 7807 struct | Third-party library (`moogar0880/problems` etc.) | A 15-line hand-rolled struct | The struct is trivially simple; adding a dependency for 4 fields is unnecessary overhead. Only hand-roll the struct itself — don't hand-roll the JSON marshal logic |

**Key insight:** This phase is infrastructure-first. Every external tool (goose, oapi-codegen, Railway templates) exists precisely to solve the bootstrapping complexity. Using them keeps the plan tasks simple and the output reproducible.

---

## Common Pitfalls

### Pitfall 1: Gradle 9.x Breaks Kotlin 2.0.21

**What goes wrong:** `./gradlew` fails immediately with Kotlin plugin compatibility errors or deprecation exceptions.
**Why it happens:** Kotlin 2.0.21 officially supports Gradle up to 8.10.x. Gradle 9.x (the current latest) introduces breaking API changes that Kotlin 2.0.21's Gradle plugin cannot handle.
**How to avoid:** Generate the Gradle wrapper pinned to `8.10.2`. Run: `gradle wrapper --gradle-version 8.10.2` or manually set `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties`.
**Warning signs:** `Could not resolve plugin ... for version catalog` or `Unresolved reference: withJava` on first `./gradlew` run.

### Pitfall 2: Java 21 Not Installed Locally

**What goes wrong:** `java: command not found` or `JAVA_HOME is not set` when running any Gradle task.
**Why it happens:** Java is not installed on this machine (confirmed by environment check). The Gradle wrapper requires a JVM to bootstrap; the Kotlin toolchain requires Java 21 exactly.
**How to avoid:** Install Temurin JDK 21 before any Gradle task. On macOS: `brew install --cask temurin@21` or download from adoptium.net. CI uses `actions/setup-java@v4` with `java-version: "21"`.
**Warning signs:** `Error: JAVA_HOME is not set` or Gradle daemon startup failure.

### Pitfall 3: SQLDelight 1.sqm Naming Semantics

**What goes wrong:** Developer names the file `1.sqm` expecting it to create version 1, but `verifyMigrations` rejects it.
**Why it happens:** SQLDelight interprets `N.sqm` as "the migration that upgrades FROM version N to version N+1." The initial schema version is 1 (defined by the `.sq` files). A `1.sqm` file means "how to get from v1 to v2." For Phase 1, there are no `.sq` schema tables yet — so `1.sqm` is an empty placeholder that becomes the first migration applied when Phase 2 adds schema.
**How to avoid:** Create `shared/src/commonMain/sqldelight/migrations/1.sqm` with a comment-only body. The file must exist; it does not need any SQL statements.
**Warning signs:** `verifyMigrations failed: migration 1 is missing` build error.

### Pitfall 4: Railway PORT Environment Variable

**What goes wrong:** Go server starts on `0.0.0.0:8080` but Railway routes traffic to a different port, resulting in "Service Unavailable" and failed healthchecks.
**Why it happens:** Railway injects a `PORT` env var (not necessarily 8080). The Go binary must read this var.
**How to avoid:** `port := os.Getenv("PORT"); if port == "" { port = "8080" }`. Hardcoded port causes intermittent failures.
**Warning signs:** Railway logs show `GET /healthz HTTP/1.1 → connection refused` or the healthcheck times out.

### Pitfall 5: Railway Healthcheck Host Filtering

**What goes wrong:** `/healthz` responds with 403 or 404 when Railway performs its healthcheck, even though the endpoint works from curl.
**Why it happens:** Railway's healthcheck origin is `healthcheck.railway.app`. If the Go handler validates the `Host` header or blocks unknown hosts, it will reject Railway's own probe.
**How to avoid:** Do not add Host-header filtering in Phase 1. The `/healthz` endpoint should respond to any host per REQ-001.
**Warning signs:** Deployment stays in "Checking Health" state until timeout; manual `curl /healthz` works.

### Pitfall 6: production Git Branch Missing

**What goes wrong:** Railway production service cannot be configured to watch the `production` branch because it does not exist.
**Why it happens:** The repository was initialized with only `main`. The `production` branch must be created and pushed before Railway can link to it.
**How to avoid:** Create and push the `production` branch from `main` as an explicit plan task: `git checkout -b production && git push origin production`. Then configure Railway production service to track this branch.
**Warning signs:** Railway dashboard shows "Branch not found" when configuring the production service.

### Pitfall 7: oapi-codegen `generate` Key for stdlib

**What goes wrong:** Generated code imports a Gin/Echo/Chi router package that is not in go.mod, causing `go build ./...` to fail.
**Why it happens:** oapi-codegen uses different `generate` keys for different targets. `gin: true` generates Gin-specific code; `echo-server: true` generates Echo code. Using the wrong key adds an unwanted router dependency.
**How to avoid:** Use `std-http-server: true` in codegen.yaml for stdlib net/http. Do not mix router targets in one config.
**Warning signs:** `cannot find package github.com/gin-gonic/gin` on first `go build`.

### Pitfall 8: Google Services JSON at Wrong Path for Firebase

**What goes wrong:** Android build fails with "Missing google-services.json" or both build variants connect to the same Firebase project.
**Why it happens:** When `google-services.json` is at `androidApp/google-services.json` (the root), it applies to all build types. For two-Firebase-project setup, each build type needs its own file.
**How to avoid:** Place staging config at `androidApp/src/debug/google-services.json` and production config at `androidApp/src/release/google-services.json`. Never commit the production file — add to `.gitignore`, inject as Railway/CI env var.
**Warning signs:** Debug and release builds both show the same Firebase project ID in logs.

### Pitfall 9: goose Migration File Naming Convention

**What goes wrong:** `goose up` says "no migrations to run" or files are applied out of order.
**Why it happens:** goose requires sequential numeric prefixes. Files named `init.sql` (no number) or `migration_001.sql` (underscore after letters) may not sort correctly.
**How to avoid:** Name files `00001_init.sql`, `00002_create_children.sql`, etc. Use the `goose create` command: `go run github.com/pressly/goose/v3/cmd/goose@v3.27.1 -dir backend/db/migrations create init sql`.
**Warning signs:** `goose: no migration files found` or goose status shows wrong version.

---

## Code Examples

### RFC 7807 Problem Details helper (backend/internal/api/problem.go)

```go
// Source: REQ-NF-001 spec; hand-rolled per "Don't Hand-Roll" guidance above
package api

import (
    "encoding/json"
    "net/http"
)

// ProblemDetail is the RFC 7807 error response format.
// The `detail` field is for developers only — never shown to end users.
type ProblemDetail struct {
    Type   string `json:"type"`
    Title  string `json:"title"`
    Status int    `json:"status"`
    Detail string `json:"detail"`
}

// WriteProblem writes an RFC 7807 problem response.
// All Go handlers must use this function for error responses (REQ-NF-001).
func WriteProblem(w http.ResponseWriter, status int, typ, title, detail string) {
    w.Header().Set("Content-Type", "application/problem+json")
    w.WriteHeader(status)
    _ = json.NewEncoder(w).Encode(ProblemDetail{
        Type:   typ,
        Title:  title,
        Status: status,
        Detail: detail,
    })
}
```

### /healthz handler implementation

```go
// Source: REQ-001 — must return 200 OK with no body, no auth
package api

import "net/http"

type Server struct{}  // implements generated ServerInterface

// GetHealthz satisfies the oapi-codegen ServerInterface for GET /healthz.
func (s *Server) GetHealthz(w http.ResponseWriter, r *http.Request) {
    w.WriteHeader(http.StatusOK)
}
```

### TestHealthz — Go test

```go
// Source: net/http/httptest standard pattern
package api_test

import (
    "net/http"
    "net/http/httptest"
    "testing"
    "github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

func TestHealthz(t *testing.T) {
    srv := &api.Server{}
    mux := http.NewServeMux()
    api.HandlerFromMux(srv, mux)  // generated by oapi-codegen

    req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
    rec := httptest.NewRecorder()
    mux.ServeHTTP(rec, req)

    if rec.Code != http.StatusOK {
        t.Errorf("expected 200, got %d", rec.Code)
    }
}
```

### Empty SQLDelight 1.sqm migration

```sql
-- shared/src/commonMain/sqldelight/migrations/1.sqm
-- Phase 1: placeholder migration. No schema tables yet.
-- Real schema (children, potty_events, etc.) is created in Phase 2.
-- This file must exist from day one per REQ-025.

-- +goose-style markers do NOT apply to .sqm files (those are goose-only).
-- SQLDelight .sqm files are plain SQL with no transaction wrappers.
```

### Railway cron service setup (environment variables reference)

```
# These env vars are set in the Railway "Postgres to R2 Backup" cron service
# Template: https://railway.com/deploy/postgres-to-r2-backup
#
# DATABASE_URL          = postgresql://... (Railway's private Postgres URL)
# R2_ENDPOINT           = https://<account_id>.r2.cloudflarestorage.com
# R2_ACCESS_KEY         = <R2 access key ID>
# R2_SECRET_KEY         = <R2 secret access key>
# R2_BUCKET_NAME        = onesteptwo-backups-staging  (or -production)
# BACKUP_TIME           = 0 3 * * *  (daily at 03:00 UTC)
#
# Must also create the R2 bucket in Cloudflare dashboard before deploying.
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.squareup.sqldelight` (v1.x) | `app.cash.sqldelight` (v2.x) | 2023 | Different Gradle coordinates; v1 packages incompatible with v2 API |
| `github.com/clerkinc/clerk-sdk-go` (v1) | `github.com/clerk/clerk-sdk-go/v2` | EOL April 2025 | PROJECT.md explicitly locks v2; v1 must not be used |
| Nixpacks as Railway default | Railpack (new) as Railway default | 2025 | Both still supported; this research uses Nixpacks (explicit `builder: NIXPACKS` in railway.json to avoid ambiguity) |
| Gradle version catalogs optional | libs.versions.toml is the standard | ~2023 | D-07 locks this; all Gradle projects use it |
| Java 17 in KMP CI examples | Java 21 for this project | Locked in PROJECT.md | GitHub Actions setup-java must specify `java-version: "21"` not `"17"` |

**Deprecated/outdated to avoid:**
- `github.com/clerkinc/clerk-sdk-go` v1: EOL April 2025. Must never appear in go.mod.
- `com.squareup.sqldelight:*` v1 coordinates: Replaced by `app.cash.sqldelight:*` in v2. Any tutorial referencing `com.squareup` is outdated.
- Kotlin 2.0.x + Gradle 9.x: Not officially supported. Use 8.10.2 wrapper.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | AGP 8.7.3 is compatible with Kotlin 2.0.21 and compileSdk 36 | Standard Stack | Gradle build fails; need to downgrade AGP or upgrade Kotlin |
| A2 | SQLDelight 2.3.2 (latest) is stable and compatible with Kotlin 2.0.21 | Standard Stack | SQLDelight Gradle plugin may emit errors; downgrade to 2.0.2 which is verified in official docs |
| A3 | SKIE 0.10.12 is compatible with Kotlin 2.0.21 | Standard Stack | iosMain fails to build; search results indicate 0.10.x is in the supported range |
| A4 | Compose BOM `2024.09.00` works with AGP 8.7.3 and minSdk 29 | Standard Stack | Build warnings or incompatibility; use latest Compose BOM available at implementation time |
| A5 | Railway reads `railway.json` from the repo root when the service is configured with `backend/` as build root | Architecture Patterns | railway.json may need to be inside backend/ subdirectory depending on Railway monorepo configuration |
| A6 | Nixpacks correctly detects Go in the `backend/` subdirectory | Architecture Patterns | May need explicit `startCommand` in railway.json or a Dockerfile |
| A7 | `com.google.gms.google-services` plugin version `4.4.2` is current | Standard Stack | Slightly stale; check Maven Central for latest at implementation time |
| A8 | goose `validate` command works without a database connection in CI | Validation Architecture | If false, goose validation requires a PostgreSQL service container in CI |
| A9 | Cloudflare R2 "Postgres to R2 Backup" Railway template is still available and actively maintained | Don't Hand-Roll | Template may have changed URL; fall back to railwayapp-templates/postgres-s3-backups |
| A10 | The `production` branch can be created from `main` and Railway will auto-deploy from it immediately | Architecture Patterns | Railway may need service to be explicitly linked to the production branch in the dashboard after creation |

---

## Open Questions

1. **Railway monorepo: where does railway.json live?**
   - What we know: Railway supports monorepo deployments by setting a "root directory" per service in the Railway dashboard.
   - What's unclear: Whether `railway.json` must be inside `backend/` (the service root) or at the repository root.
   - Recommendation: Place `railway.json` inside `backend/` and set the Railway service "root directory" to `backend/`. This is the safest configuration for monorepo Go backends.

2. **CI google-services.json for assembleDebug**
   - What we know: `assembleDebug` requires `src/debug/google-services.json` to exist at build time (with `apply plugin: 'com.google.gms.google-services'`).
   - What's unclear: Whether to commit a staging google-services.json to the repo or inject it as a CI secret.
   - Recommendation: Commit the staging (debug) `google-services.json`. It is non-sensitive (the Firebase project itself is the staging environment). The production google-services.json is a CI secret (GitHub Secret → file write step), never committed.

3. **goose migration validation in CI without a DB**
   - What we know: `goose validate` checks file syntax and ordering without a database connection.
   - What's unclear: The exact CLI invocation for `goose validate` via `go run` (vs. a locally installed binary).
   - Recommendation: `go run github.com/pressly/goose/v3/cmd/goose@v3.27.1 -dir backend/db/migrations validate` — no database URL needed for validate.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Go | Backend build, go test | ✓ | go1.25.5 (darwin/arm64) | — |
| Java 21 JDK | Gradle wrapper, Kotlin toolchain | ✗ | not installed | Install Temurin 21 before any Gradle task |
| Docker | Local Railway/container testing | ✓ | 29.1.3 | — |
| Railway CLI | Railway service management | ✓ | 4.59.0 | Use Railway dashboard directly |
| psql / pg_dump | Manual DB inspection, local backup test | ✗ | not installed | Use Railway dashboard or Docker postgres image |
| Android SDK | Local Android build | ✗ | not found | CI uses ubuntu-latest which has Android SDK pre-installed |
| Gradle (local) | — | ✗ | not installed | Use Gradle wrapper `./gradlew` (downloads automatically) |

**Missing dependencies with no fallback:**
- **Java 21 JDK**: Required before any `./gradlew` task. Install Temurin 21 first: `brew install --cask temurin@21` (macOS).

**Missing dependencies with fallback:**
- **psql/pg_dump**: Manual DB operations can be done via Railway dashboard shell or `docker run postgres:16 psql`.
- **Android SDK**: Local Android builds require SDK. CI has it pre-installed. For local dev, install via Android Studio or `sdkmanager`.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Go stdlib `testing` + `net/http/httptest` |
| Config file | none (Go tests run with `go test ./...`) |
| Quick run command | `cd backend && go test ./...` |
| Full suite command | `cd backend && go test ./... && ./gradlew :androidApp:assembleDebug` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REQ-001 | GET /healthz returns 200 OK | unit (httptest) | `cd backend && go test ./internal/api/... -run TestHealthz -v` | ❌ Wave 0 |
| REQ-002 | /v1/ prefix established (no /v1/ endpoints yet) | compile | `cd backend && go build ./...` | ❌ Wave 0 |
| REQ-025 | 1.sqm compiles cleanly | build (Gradle) | `./gradlew :shared:generateCommonMainOneStepTwoDatabaseInterface` | ❌ Wave 0 |
| REQ-NF-001 | WriteProblem emits correct Content-Type + JSON shape | unit | `cd backend && go test ./internal/api/... -run TestWriteProblem -v` | ❌ Wave 0 |
| CI gate | Go build + Android assembleDebug pass | build | GitHub Actions `ci.yml` | ❌ Wave 0 |
| goose validation | Migration files are syntactically valid | validate | `go run github.com/pressly/goose/v3/cmd/goose@v3.27.1 -dir backend/db/migrations validate` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd backend && go test ./...`
- **Per wave merge:** `cd backend && go test ./... && ./gradlew :androidApp:assembleDebug`
- **Phase gate:** All of the above green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `backend/internal/api/server_test.go` — covers REQ-001 (TestHealthz) and REQ-NF-001 (TestWriteProblem)
- [ ] `backend/internal/api/problem.go` — required before tests can compile
- [ ] `backend/internal/api/generated.go` — generated by oapi-codegen; must be committed before CI compiles
- [ ] `shared/src/commonMain/sqldelight/migrations/1.sqm` — required for REQ-025 verify
- [ ] `.github/workflows/ci.yml` — the CI that validates all of the above

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Phase 1 has no auth; /healthz is explicitly unauthenticated |
| V3 Session Management | No | No sessions in Phase 1 |
| V4 Access Control | No | No access control in Phase 1 |
| V5 Input Validation | Minimal | /healthz accepts no input; no validation needed |
| V6 Cryptography | No | No crypto in Phase 1 |
| V9 Communication Security | Yes | Railway terminates TLS; Go binary listens on HTTP internally — this is correct and expected |

### Secret Management for Phase 1

The primary security obligation in Phase 1 is keeping secrets out of git. All external service credentials must live as environment variables only.

| Secret | Where It Lives | Must NOT Go |
|--------|---------------|-------------|
| Clerk Secret Key (`sk_*`) | Railway env dashboard / GitHub Secrets | Git repo |
| Firebase service account JSON | Railway env var `FCM_SERVICE_ACCOUNT_JSON` | Git repo |
| Cloudflare R2 access/secret keys | Railway cron service env | Git repo |
| PostgreSQL `DATABASE_URL` (with credentials) | Railway env dashboard | Git repo |
| Clerk Publishable Key (`pk_*`) | `buildConfigField` in build.gradle.kts — safe to commit | n/a (not a secret) |
| Firebase `google-services.json` (debug/staging) | Commit to repo | n/a (staging config is not sensitive) |
| Firebase `google-services.json` (release/production) | GitHub Secret → CI file write | Git repo |

### Known Threat Patterns for Phase 1 Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret key committed to git | Information Disclosure | `.gitignore` `*.json` in production directories; Railway env vars only |
| Healthcheck used for service enumeration | Information Disclosure | /healthz returns 200 with no body per REQ-001 — no version, stack, or environment info leaked |
| RFC 7807 `detail` field leaks stack traces | Information Disclosure | `detail` must be a developer explanation string — never include `err.Error()` or stack traces in production responses |
| Unlimited healthcheck endpoints enabling DoS | Denial of Service | Not a Phase 1 concern; Railway itself rate-limits; no rate limiting needed until Phase 9 (REQ-NF-004) |

---

## Sources

### Primary (MEDIUM confidence)

- Go module proxy `pkg.go.dev` — verified versions for pressly/goose/v3 (v3.27.1), oapi-codegen/v2 (v2.7.1), oapi-codegen/runtime (v1.4.2), clerk/clerk-sdk-go/v2 (v2.7.0)
- [gradle.org/releases](https://gradle.org/releases/) — confirmed Gradle 8.10.2 as latest 8.10.x (Sep 2024)
- [docs.railway.com/reference/config-as-code](https://docs.railway.com/reference/config-as-code) — railway.json schema and fields
- [docs.railway.com/deployments/healthchecks](https://docs.railway.com/deployments/healthchecks) — healthcheck mechanics and caveats
- [sqldelight.github.io/sqldelight/2.0.2/multiplatform_sqlite/migrations](https://sqldelight.github.io/sqldelight/2.0.2/multiplatform_sqlite/migrations/) — .sqm migration format and semantics
- [clerk.com/docs/android/getting-started/quickstart](https://clerk.com/docs/android/getting-started/quickstart) — clerk-android-api initialization pattern
- [kotlinlang.org/docs/multiplatform/github-actions-for-kmp.html](https://kotlinlang.org/docs/multiplatform/github-actions-for-kmp.html) — KMP GitHub Actions patterns

### Secondary (LOW confidence)

- WebSearch: Kotlin 2.0.21 + Gradle 8.10 compatibility (confirmed from Kotlin docs link; content verified against official source)
- WebSearch: SKIE 0.10.12 compatibility with Kotlin 2.0.21
- WebSearch: SQLDelight 2.3.2 as latest stable version
- WebSearch: goose validate command (no DB required)
- WebSearch: Firebase two-project google-services.json placement pattern
- WebSearch: Railway Postgres-to-R2 backup template

### Tertiary (LOW confidence — ASSUMED)

- AGP 8.7.3 specific compatibility with compileSdk 36 and Kotlin 2.0.21 (not directly verified)
- Compose BOM version for Phase 1 Android scaffolding
- google-services plugin version 4.4.2

---

## Metadata

**Confidence breakdown:**
- Standard Stack (Go modules): HIGH — verified via Go module proxy
- Standard Stack (Gradle/KMP): MEDIUM — versions from search results + official doc references
- Architecture: MEDIUM — patterns well-established; Railway monorepo config has one open question
- Pitfalls: MEDIUM — most are documented Railway/Gradle behaviors; Gradle 9.x issue confirmed from Kotlin docs
- Provisioning steps (Clerk, Firebase): LOW — dashboard-level steps not verifiable via tooling

**Research date:** 2026-06-26
**Valid until:** 2026-07-26 (30 days; stable infrastructure stack; Railway config format occasionally changes)
