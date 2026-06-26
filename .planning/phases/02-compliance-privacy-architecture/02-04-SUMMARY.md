---
phase: 02-compliance-privacy-architecture
plan: 04
subsystem: database
tags: [sqldelight, kmp, sqlite, migration, offline-first, data-minimisation]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: KMP shared module with SQLDelight configured (verifyMigrations = true, 1.sqm placeholder)

provides:
  - Three SQLDelight .sq current-schema files defining the client SQLite tables
  - 2.sqm migration creating consent_events, children, potty_events on existing v1 databases
  - 3.db schema snapshot committed so verifySqlDelightMigration can compare migration output
  - Client-only sync_status column for offline pending queue (D-14)
  - Soft-delete visibility columns deleted_at/deleted_by on potty_events (D-15)
  - schemaOutputDirectory configured in shared/build.gradle.kts to enable generateSchema task

affects:
  - phase-05-offline-first-sync (consumes sync_status for pending queue)
  - phase-06-core-event-logging (writes to potty_events; uses client schema)
  - phase-03-authentication (reads children/consent_events tables)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SQLDelight .sq files define current schema; .sqm files are migrations applied to reach that schema"
    - "schemaOutputDirectory required in sqldelight block for generateSchema task and verifyMigrations"
    - "SQLite type mapping: TEXT for UUID/timestamp, INTEGER for integers, TEXT (nullable) for enums"
    - "No transaction wrappers in .sqm files (crashes some SQLite drivers)"

key-files:
  created:
    - shared/src/commonMain/sqldelight/com/onesteptwo/db/ConsentEvents.sq
    - shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq
    - shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq
    - shared/src/commonMain/sqldelight/migrations/2.sqm
    - shared/src/commonMain/sqldelight/migrations/3.db
  modified:
    - shared/build.gradle.kts

key-decisions:
  - "schemaOutputDirectory added to shared/build.gradle.kts: without it, the generateSchema task does not exist and verifySqlDelightMigration cannot run (3.db must be present)"
  - "3.db naming: SQLDelight names the schema file {latestMigration+1}.db; with 1.sqm and 2.sqm the snapshot is 3.db"
  - "Comments in .sq files must not mention column-name strings used in grep-based acceptance checks — reworded two comments to avoid false positives"

patterns-established:
  - "SQLDelight migration pair: .sq current schema + matching .sqm migration must be identical; run generateSchema then verifySqlDelightMigration to confirm"
  - "Client SQLite tables omit PostgreSQL-specific syntax; event_type is nullable TEXT, not ENUM"

requirements-completed: [REQ-C-005]

# Metrics
duration: 6min
completed: 2026-06-26
---

# Phase 2, Plan 4: Client SQLite Schema (SQLDelight) Summary

**SQLDelight 2.x client schema with three SQLite tables, 2.sqm migration, and verifySqlDelightMigration BUILD SUCCESSFUL — offline-first foundation with sync_status queue column and no on-device PII**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-06-26T21:00:00Z
- **Completed:** 2026-06-26T21:06:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Created three SQLite-valid .sq current-schema files under `shared/src/commonMain/sqldelight/com/onesteptwo/db/` matching the SQLDelight 2.x package-path convention
- Created `2.sqm` migration that creates the same three tables so that applying 1.sqm (empty) then 2.sqm yields the current .sq schema
- Added `schemaOutputDirectory` to `shared/build.gradle.kts` and generated `3.db` — this enables the `generateCommonMainOneStepTwoDatabaseSchema` task and allows `verifySqlDelightMigration` to run; the Gradle task `verifySqlDelightMigration` passes BUILD SUCCESSFUL
- Client schema stores only `clerk_user_id` and `clerk_org_id` identifiers; no parent/caregiver PII on-device (REQ-C-005)
- `potty_events` has `sync_status TEXT NOT NULL DEFAULT 'pending'` (D-14) and `deleted_at`/`deleted_by` (D-15)

## Task Commits

Each task was committed atomically:

1. **Task 1: Define the current client schema (.sq files)** - `c32a46b` (feat)
2. **Task 2: Write 2.sqm migration and verify** - `a9e99d3` (feat)
3. **Comment fix: reword .sq comments to avoid grep false positives** - `d6c14a4` (chore)

## Files Created/Modified

- `shared/src/commonMain/sqldelight/com/onesteptwo/db/ConsentEvents.sq` - consent_events table: id, clerk_user_id, consented_at, app_version, consent_text_version; no child_id, no IP
- `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq` - children table: id, clerk_org_id, nickname, birth_month, birth_year, consent_event_id, created_at, updated_at; no PII columns
- `shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq` - potty_events table with sync_status (D-14) and deleted_at/deleted_by (D-15)
- `shared/src/commonMain/sqldelight/migrations/2.sqm` - migration creating all three tables; SQLite types only
- `shared/src/commonMain/sqldelight/migrations/3.db` - generated schema snapshot used by verifySqlDelightMigration
- `shared/build.gradle.kts` - added schemaOutputDirectory to sqldelight block (Rule 3 fix — blocking without it)

## Decisions Made

- **schemaOutputDirectory required**: The `verifySqlDelightMigration` task requires a `.db` schema file to compare migrations against. This file is generated by `generateCommonMainOneStepTwoDatabaseSchema`, which only exists when `schemaOutputDirectory` is configured in the `sqldelight {}` block. Without it the build fails with "a database file to be present." Added the directory pointing to the migrations folder.
- **3.db naming convention**: SQLDelight names the schema snapshot file `{latestMigration + 1}.db`. With `1.sqm` and `2.sqm` as the complete migration history, the snapshot is `3.db` — this is correct and expected.
- **Comments reworded**: Two .sq files had comments listing column names that were excluded (e.g., "No child_id", "No gender, legal_name..."). These comments caused grep-based acceptance checks to false-positive. Reworded to express the constraint without naming the forbidden columns.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added schemaOutputDirectory to shared/build.gradle.kts**
- **Found during:** Task 2 (Run verifySqlDelightMigration)
- **Issue:** `verifySqlDelightMigration` failed with "Verifying a migration requires a database file to be present." The `generateSchema` task did not exist because `schemaOutputDirectory` was not configured in the `sqldelight {}` block.
- **Fix:** Added `schemaOutputDirectory.set(file("src/commonMain/sqldelight/migrations"))` to the database block in `shared/build.gradle.kts`. Ran `generateCommonMainOneStepTwoDatabaseSchema` to produce `3.db`. Committed both `3.db` and the updated `build.gradle.kts`.
- **Files modified:** `shared/build.gradle.kts`, `shared/src/commonMain/sqldelight/migrations/3.db`
- **Verification:** `./gradlew :shared:verifySqlDelightMigration` → BUILD SUCCESSFUL
- **Committed in:** `a9e99d3` (Task 2 commit)

**2. [Rule 1 - Bug] Reworded comments in .sq files to avoid grep false positives**
- **Found during:** Task 2 (overall verification)
- **Issue:** Comments in `Children.sq` and `ConsentEvents.sq` used the exact column names they were documenting as absent (e.g., "No child_id", "No gender, legal_name, dob, or photo columns"). The plan's grep-based acceptance checks searched for these strings and matched the comments, causing false-positive failures.
- **Fix:** Rewrote the comments to express the constraint without naming the forbidden column strings. Regenerated `3.db` to ensure the schema snapshot remained current.
- **Files modified:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq`, `shared/src/commonMain/sqldelight/com/onesteptwo/db/ConsentEvents.sq`
- **Verification:** All acceptance-criteria greps pass; `verifySqlDelightMigration` still BUILD SUCCESSFUL
- **Committed in:** `d6c14a4` (chore commit)

---

**Total deviations:** 2 auto-fixed (1 blocking build config, 1 comment/grep bug)
**Impact on plan:** Both fixes necessary for correctness. schemaOutputDirectory is essential for the Gradle tooling to function. Comment rewords are cosmetic but required for the verification script to work correctly.

## Issues Encountered

None beyond the two deviations documented above.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries beyond what the plan defined. The `.sq` and `.sqm` files are build-time artifacts (client schema definitions) with no runtime network exposure.

T-2-07 mitigation verified: client tables contain only `clerk_user_id`/`clerk_org_id` identifiers — no parent name, email, phone, or IP address column in any table.

T-2-12 mitigation verified: `verifyMigrations = true` gates the build; `2.sqm` is sequential (no gap after `1.sqm`); no BEGIN/END wrapper in migration file.

## Known Stubs

None — this plan creates schema-only files (DDL with no queries). No data source wiring or UI is part of this plan's scope.

## Next Phase Readiness

- Client SQLite schema is complete and verified; Phase 5 (offline-first sync) can consume `sync_status` for the pending queue
- Phase 3 (auth) and Phase 6 (event logging) can reference the `children` and `potty_events` tables
- The `schemaOutputDirectory` is now configured; any future `.sq` additions must be followed by re-running `generateCommonMainOneStepTwoDatabaseSchema` and committing the updated `.db` snapshot before the next migration is written

---
*Phase: 02-compliance-privacy-architecture*
*Completed: 2026-06-26*
