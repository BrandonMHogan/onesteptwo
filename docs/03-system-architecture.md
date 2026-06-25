# System Architecture

> Last updated: 2026-06-25

## Component Map

```
┌─────────────────────────────────────────────────────┐
│                   Mobile Clients                    │
│                                                     │
│  ┌──────────────────┐    ┌──────────────────┐      │
│  │   Android App    │    │    iOS App       │      │
│  │ (Jetpack Compose)│    │   (SwiftUI)      │      │
│  └────────┬─────────┘    └────────┬─────────┘      │
│           │                       │                 │
│           └──────────┬────────────┘                 │
│                      │                              │
│          ┌───────────▼────────────┐                 │
│          │     KMP Shared Module  │                 │
│          │  SQLDelight (local DB) │                 │
│          │  Ktor Client (HTTP)    │                 │
│          │  SKIE bridge (iOS)     │                 │
│          └───────────┬────────────┘                 │
└──────────────────────┼──────────────────────────────┘
                       │ HTTPS (REST/JSON)
                       │ OpenAPI contract (/api/openapi.yaml)
┌──────────────────────▼──────────────────────────────┐
│                  Railway (private network)           │
│                                                     │
│  ┌─────────────────┐    ┌──────────────────┐       │
│  │   Go API Server │◄───►  PostgreSQL       │       │
│  │  (oapi-codegen) │    │  (Railway plugin) │       │
│  └────────┬────────┘    └──────────────────┘       │
└───────────┼─────────────────────────────────────────┘
            │
     ┌──────┴───────┐
     │              │
     ▼              ▼
┌─────────┐   ┌──────────┐
│  Clerk  │   │   FCM    │
│  (Auth) │   │  (Push)  │
└─────────┘   └──────────┘
                   │
          ┌────────┴────────┐
          ▼                 ▼
    Android device     iOS device
    (via FCM)          (via APNs)

┌──────────────────────────────────────────────────────┐
│              Cloudflare Pages                        │
│         SvelteKit — Marketing Site (v1)              │
└──────────────────────────────────────────────────────┘
```

## Rate Limiting

Not implemented in v1. Revisit before scaling — a per-user rate limit on event writes (e.g. 60/minute) protects against sync retry storms or a misbehaving client flooding the database. Can be added as Go middleware without changing the API contract.

## Healthcheck

The Go API exposes `GET /healthz` returning `200 OK` with no body. Railway uses this endpoint to determine container readiness and liveness. It must respond before any other endpoint is registered and must not require authentication.

## API Versioning

All endpoints are prefixed with `/v1/`. Example: `POST /v1/children/{id}/events`.

Versioning is set in the OpenAPI spec:

```yaml
servers:
  - url: https://api.onesteptwo.com/v1
```

Breaking changes introduce a `/v2/` prefix. Old app versions on stores continue working against `/v1/` until adoption drops to negligible levels.

## API Error Format

All Go API errors use **RFC 7807 Problem Details** (`application/problem+json`):

```json
{
  "type": "https://onesteptwo.com/errors/not-found",
  "title": "Child not found",
  "status": 404,
  "detail": "No child with ID abc-123 exists in your organization."
}
```

| Field | Purpose |
|---|---|
| `type` | URI identifying the error class — stable, machine-readable |
| `title` | Short description — safe for logs |
| `status` | HTTP status code mirrored in the body |
| `detail` | Developer-facing explanation — never shown to end users in the mobile app |

A reusable `ProblemDetails` schema is defined in `/api/openapi.yaml` and referenced by all error responses. The mobile app displays its own localised error strings — it does not surface `detail` to users.

## Data Flow — Logging an Event

1. Caregiver taps "potty success" on their phone
2. KMP shared module writes the event to local SQLDelight DB immediately (offline-first)
3. Ktor Client sends a POST to Go API (`/children/{id}/events`)
4. Go API inserts the event row into PostgreSQL
5. Go API reads notification preferences for all family members of that child
6. Go API sends FCM push notification to eligible device tokens
7. FCM delivers to subscribed family members' devices
8. Recipients' apps pull fresh data on next open or manual refresh

## Offline Behaviour

The app is fully functional without a network connection. SQLDelight is the source of truth on-device. Events written offline are queued and synced when connectivity is restored. The server is append-only for events — there are no merge conflicts on inserts. See `07-sync-and-notifications.md` for the conflict model.

## Auth Flow

Clerk handles all identity. The Go API validates Clerk JWTs on every request. Family membership is enforced via Clerk Organization membership — a caregiver can only read/write events for children within their Clerk org. See `06-auth.md` for detail.

## Deployment Topology

| Service | Platform | Notes |
|---|---|---|
| Go API | Railway (production only) | Always-on container, private network |
| PostgreSQL | Railway (staging + production) | Two separate instances — one per environment |
| Marketing site | Cloudflare Pages | Static SvelteKit output, global CDN |
| Auth | Clerk (SaaS) | External, SOC 2 Type II |
| Push notifications | FCM (Google, SaaS) | External |
| DB backups | Cloudflare R2 | Daily `pg_dump` from a Railway cron job |

## Environment Strategy

Two environments. No local Docker required.

| | Staging | Production |
|---|---|---|
| Go API | Runs locally (`go run ./cmd/api`) | Railway service |
| PostgreSQL | Railway instance | Railway instance |
| Clerk | Clerk dev instance | Clerk prod instance |
| FCM | Firebase test project | Firebase prod project |

**Migration workflow:** run `goose up` against staging Postgres first, verify, then run against production. The Go API in staging points at the Railway staging Postgres via `DATABASE_URL` in `.env.local` (gitignored). Production environment variables live in the Railway dashboard only — never in the repo.
