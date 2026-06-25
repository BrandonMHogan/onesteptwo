# Project Context

## What We're Building

OneStepTwo is a mobile app (Android + iOS) that helps families track potty training milestones for their children. Multiple caregivers — parents, grandparents, daycare workers — can log potty events for a shared child profile and receive push notifications when any caregiver logs a new event. The app is offline-first: events are recorded instantly to a local database and synced to the server when connectivity is available.

## Goals

- Enable multi-caregiver families to coordinate potty training across different locations and caregivers
- Log potty events quickly (one-tap) with optional detail added later
- Deliver timely push notifications to all opted-in family members when a new event is logged
- Operate fully offline — no connectivity required to log an event
- Minimise child PII by design (nickname + birth month/year only) to satisfy COPPA, GDPR, PIPEDA, and POPIA
- Support family onboarding via Clerk invitation flow — no custom invitation backend required
- Provide a stable, versioned API that supports old app versions in the field during staged rollouts

## Non-Goals (v1)

- Real-time sync (no WebSockets or SSE — pull-to-refresh only)
- Web dashboard for parents (marketing site only in v1; dashboard deferred to v2)
- Photo or media storage (child media has high privacy risk; deferred to v2)
- Background sync when app is closed (deferred to v2)
- Data portability export endpoint (GDPR Article 20; deferred to v2)
- MFA on free Clerk tier (Pro plan required; not launch-blocking for a family tracking app)
- Rate limiting in v1 (to be added as Go middleware before scaling)
- Automatic data expiry (user-initiated deletion is the retention mechanism)
- Verified parental consent (VPC) beyond self-attestation checkbox (verify with legal counsel per market)
- EU data residency (Railway EU region to be evaluated when EU user base scales)

## Key Stakeholders / Users

- Parents / guardians (admin role) — create the family org, add child profiles, invite caregivers, manage members
- Caregivers (caregiver role) — log events and view history; cannot manage family or members (e.g. daycare workers, grandparents, nannies)
- Children — subjects of data collection; not app users themselves; parental consent required before any data is stored

## Background and Architecture Evaluation

The initial architecture evaluation (docs/01-architecture-evaluation.md) examined three open decisions:

1. Backend: Ktor Server (Kotlin) vs Go — Ktor was recommended for shared KMP model types; Go was ultimately chosen (docs/02-stack.md) for operational simplicity, fast builds, and easy onboarding. An OpenAPI spec + oapi-codegen recovers the type-safety benefit without Gradle complexity.

2. Auth: Clerk vs Kinde — Clerk was chosen decisively. Kinde's 5-org free tier limit is incompatible with an architecture where every family is a Clerk Organization. Clerk provides 100 free orgs and headless native SDKs on both Android and iOS.

3. KMP + SKIE stability — Confirmed production-ready. SKIE tracks Kotlin releases within days; Touchlab is commercially backed. Chosen over Swift Export (not production-ready in mid-2026) and KMP-NativeCoroutines (conflicting cancellation behavior with SKIE).

## Deployment Summary

- Mobile: Android (Jetpack Compose) + iOS (SwiftUI), shared KMP business logic in :shared module
- Backend: Go binary on Railway (always-on), PostgreSQL on Railway, migrations via goose
- Auth: Clerk (SaaS) — Organizations as families, JWT validation in Go middleware
- Push: FCM (unified delivery to Android + iOS via APNs bridge)
- Web: SvelteKit marketing site on Cloudflare Pages (static output)
- Backups: Daily pg_dump → Cloudflare R2

## Regulatory Context

The app collects data about children. Applicable regulations:

| Regulation | Region | Core Requirement |
|---|---|---|
| COPPA | United States | Parental consent before child data collection |
| GDPR | EU / UK | Consent, right to erasure, data minimisation, portability |
| PIPEDA / Law 25 | Canada / Quebec | Consent and erasure obligations |
| POPIA | South Africa | Similar consent and erasure framework |

Compliance strategy: minimise child data collected, require explicit parental consent at child profile creation, provide a hard-delete erasure path, and store parent/caregiver PII exclusively in Clerk (not our database).
