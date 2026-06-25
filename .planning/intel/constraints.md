# Constraints

---

## Regulatory

- COPPA applies: the app processes data about children under 13. Parental consent is legally required before any child data is collected. (source: docs/05-privacy.md)

- GDPR applies: EU/UK users trigger full GDPR obligations including consent, right to erasure, data minimisation, and the Article 20 data portability right. (source: docs/05-privacy.md)

- PIPEDA / Law 25 applies: Canadian and Quebec users bring consent and erasure obligations. (source: docs/05-privacy.md)

- POPIA may apply: South African users trigger POPIA obligations if the app is marketed there. (source: docs/05-privacy.md)

- Consent must precede data collection: a consent_events row must exist before any children row is created. This is a hard legal gate, not a UX nice-to-have. (source: docs/04-data-model.md, docs/05-privacy.md)

- Erasure must be complete: legal erasure requests must result in real DELETE statements, not soft deletes. Orphaned rows are not acceptable under GDPR/COPPA. (source: docs/05-privacy.md)

- No IP addresses in consent records: storing an IP address in consent_events would itself require GDPR justification under data minimisation. (source: docs/04-data-model.md)

---

## Technical

- Android minimum SDK: 29 (Android 10). Unlocks java.time without desugaring. (source: docs/02-stack.md)

- iOS deployment target: 17.0. Approximately 96% device coverage as of mid-2026. (source: docs/02-stack.md)

- Java toolchain: 21 (Temurin 21). Required for Go build environment on Railway. (source: docs/02-stack.md)

- Kotlin version: 2.0.21 (pin explicitly; disable auto-upgrade). Upgrade Kotlin and SKIE together deliberately. (source: docs/02-stack.md)

- SKIE only — no KMP-NativeCoroutines: the two have conflicting cancellation behavior and confusing Swift call sites. Pick SKIE exclusively. (source: docs/01-architecture-evaluation.md, docs/02-stack.md)

- SKIE and Swift Export are mutually exclusive: do not use both. Swift Export is not production-ready (mid-2026); SKIE is the correct choice until Swift Export reaches parity. (source: docs/01-architecture-evaluation.md)

- Do not expose generic types at the KMP/Swift boundary: SKIE does not bridge Kotlin generics cleanly. Use concrete typed repository classes (e.g. UserRepository, not Repository<User>). (source: docs/01-architecture-evaluation.md)

- Use sealed classes for error states at the KMP boundary: Kotlin.Result<T> does not bridge to Swift Result<T>. SKIE converts sealed classes to exhaustive Swift enums. (source: docs/01-architecture-evaluation.md)

- Threading at Ktor → SQLDelight → SKIE boundary: wrap cross-boundary return values in withContext(Dispatchers.Main) or withContext(Dispatchers.Default) at the boundary layer. Test cancellation paths explicitly. (source: docs/01-architecture-evaluation.md)

- OpenAPI spec at /api/openapi.yaml is the single source of truth for API shapes. The Go server stubs are generated from it; manual edits to generated.go are overwritten by `make generate`. (source: docs/02-stack.md, docs/03-system-architecture.md)

- Clerk SDK v2 only (github.com/clerk/clerk-sdk-go/v2, v2.7.0+): do not use deprecated v1 or custom JWKS validation. (source: docs/06-auth.md)

- Clerk role prefix is mandatory: always check "org:admin" and "org:caregiver" — not bare "admin" or "caregiver". Hardcoding without the prefix causes silent failures. (source: docs/06-auth.md)

- CLERK_AUTHORIZED_PARTY must be set in production: the Clerk SDK does not validate the azp claim by default. Without this, any valid Clerk token from any frontend can call the API. (source: docs/06-auth.md)

- No direct APNs calls: FCM is the unified push delivery layer. FCM handles the APNs bridge. APNs auth key (.p8) is uploaded to Firebase console, not used in the Go backend. (source: docs/07-sync-and-notifications.md)

- Firebase service account JSON must never be committed to the repo: it must be stored as the Railway environment variable FCM_SERVICE_ACCOUNT_JSON (full JSON string). (source: docs/07-sync-and-notifications.md)

- goose only for backend migrations: migration files use `-- +goose Up` / `-- +goose Down` syntax. Run against staging first, verify, then production. (source: docs/02-stack.md, docs/03-system-architecture.md)

- SQLDelight .sqm migration files from version 1 on day one: version 1.sqm must exist from the moment the first schema is written. No gaps in the numbering sequence. (source: docs/07-sync-and-notifications.md)

- No file/media storage in v1: Cloudflare R2 is used for Postgres backup dumps only; child photo/media storage is explicitly deferred. (source: docs/02-stack.md)

- Production environment variables live in the Railway dashboard only — never committed to the repo. (source: docs/03-system-architecture.md)

---

## Operational

- Railway always-on deployment: sleeping is opt-in and is NOT enabled. The Go API container is always running. Cold-start concerns are irrelevant in normal operation. (source: docs/01-architecture-evaluation.md, docs/03-system-architecture.md)

- JVM memory flags must be set explicitly in Railway environment: JAVA_OPTS=-Xms64m -Xmx400m -XX:MaxMetaspaceSize=128m -XX:+UseContainerSupport. Without -XX:+UseContainerSupport the JVM reads host memory and can be OOM-killed. (source: docs/01-architecture-evaluation.md)

- Two Railway environments (staging, production): separate PostgreSQL instances, separate Clerk instances (dev vs prod), separate Firebase projects. No shared state between environments. (source: docs/03-system-architecture.md)

- Postgres backup must be configured on day one: Railway Postgres has no built-in PITR. Daily pg_dump to Cloudflare R2 via Railway cron is the backup strategy. (source: docs/02-stack.md)

- Go module path: github.com/BrandonMHogan/onesteptwo/backend (source: docs/02-stack.md)

- Android applicationId: com.onesteptwo.android; iOS bundle ID: com.onesteptwo.ios; KMP framework name: Shared (imported in Swift as `import Shared`). (source: docs/02-stack.md)

- Android compileSdk / targetSdk: 36 (current stable). (source: docs/02-stack.md)

- The Clerk org with 100 free organizations is the hard ceiling for free-tier auth. The architecture places one org per family; plan accordingly. (source: docs/01-architecture-evaluation.md, docs/02-stack.md)
