# Confirmed Tech Stack

> Last updated: 2026-06-25

## Mobile

| Layer | Choice | Why |
|---|---|---|
| Android UI | Jetpack Compose (native) | Full platform capability, no cross-platform compromise |
| iOS UI | SwiftUI (native) | Same — native performance and feel |
| Shared business logic | Kotlin Multiplatform (KMP) | One implementation of sync, data models, and networking shared across both platforms |
| Local storage | SQLDelight | Type-safe SQL, KMP-compatible, generates Kotlin models from schema; uses built-in `.sqm` migration files for on-device schema versioning |
| Networking client | Ktor Client | KMP-native HTTP client, works in commonMain |
| Swift interop | SKIE (Touchlab) | Generates idiomatic Swift API from Kotlin — async/await, sealed class enums, default params |

### Mobile Build Config

| Identifier | Value | Notes |
|---|---|---|
| Root project name | `onesteptwo` | |
| KMP shared module | `:shared` | |
| Android app module | `:android` | |
| iOS Xcode folder | `ios` | |
| iOS framework name | `Shared` | KMP artifact imported in Swift as `import Shared` |
| Android `applicationId` | `com.onesteptwo.android` | |
| Android `minSdk` | `29` | Android 10 — unlocks `java.time` without desugaring |
| Android `compileSdk` / `targetSdk` | `36` | Current stable |
| iOS bundle ID | `com.onesteptwo.ios` | |
| iOS deployment target | `17.0` | ~96% device coverage as of mid-2026 |
| Kotlin version | `2.0.21` | Verify latest stable at scaffold time |
| Java toolchain | `21` | |

## Backend

| Layer | Choice | Why |
|---|---|---|
| Language | Go | Simple deployment (single binary), fast compile times, low memory footprint on Railway, easy to onboard new devs |
| Go module path | `github.com/BrandonMHogan/onesteptwo/backend` | Matches the GitHub repo root |
| API contract | OpenAPI spec | Source of truth for request/response shapes — lives at `/api/openapi.yaml` in the repo root |
| Code generation | `oapi-codegen` | Generates `/backend/internal/api/generated.go` from the spec; run via `make generate`; generated file is committed |
| Deployment | Railway | Simple container hosting, private networking to Postgres, one dashboard |

## Database

| Layer | Choice | Why |
|---|---|---|
| Database | PostgreSQL on Railway | Private network to Go backend (~1ms latency), zero extra service, standard Postgres |
| Migration tooling | `goose` | Plain SQL migrations (`-- +goose Up` / `-- +goose Down`), runs via `go run cmd/migrate/main.go`, no external binary |
| Backups | `pg_dump` → Cloudflare R2 (daily cron) | Railway Postgres has no built-in PITR — must configure manually on day one |

## Auth

| Layer | Choice | Why |
|---|---|---|
| Provider | Clerk | Headless native SDKs (Android + iOS), Organizations for family grouping, built-in invitation flow, 100 free orgs |
| Android SDK | `clerk-android-api` (no UI artifact) | Headless JWT management, integrates cleanly with KMP Ktor networking layer |
| iOS SDK | `ClerkKit` (no UI artifact) | Same — no browser redirect required |
| Web | `clerk-sveltekit` (community adapter) | Handles SSR session, injects into SvelteKit `locals` |

## Web Frontend

| Layer | Choice | Why |
|---|---|---|
| Framework | SvelteKit | Lightweight, fast, good SSR support |
| Scope (v1) | Marketing landing page only | Mobile is the product; web dashboard deferred to v2 |
| Deployment | Cloudflare Pages | Free, fast global CDN, zero config for SvelteKit static output |

## Infrastructure

| Layer | Choice | Why |
|---|---|---|
| Push notifications | FCM (Firebase Cloud Messaging) | Unified layer — one API call from Go delivers to both Android and iOS |
| File storage | None in v1 | Photos deferred — privacy/security implications for child media need separate discussion |
| Backups | Cloudflare R2 | S3-compatible, zero egress fees, used for Postgres backup dumps |

## Explicitly Deferred

- **Photo/media storage** — Privacy and security around child photos needs dedicated discussion before v2
- **Web dashboard** — Full parent dashboard (auth, data views, event logging) deferred until after mobile v1 ships
