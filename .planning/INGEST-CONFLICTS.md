## Conflict Detection Report

Mode: new
Docs processed: 7 (1 ADR, 5 SPEC, 1 DOC)
Precedence applied: ADR (0) > SPEC (1) > DOC (3)

---

### BLOCKERS (0)

None. No two LOCKED ADR decisions contradict each other on the same scope.

---

### WARNINGS (0)

None. No two SPECs assert competing variants for the same behavior with incompatible acceptance criteria.

Note on cross-reference cycle: docs/04-data-model.md and docs/05-privacy.md contain bidirectional
cross-references (04 lists 05 in cross_refs; 05 lists 04 in cross_refs). This forms a reference
cycle in the dependency graph. However, the two documents cover complementary concerns without
logical contradiction — 04 is the schema authority, 05 is the regulatory authority — and neither
document's synthesis depends on the other's output. Both were loaded and synthesized from source
without issue. No action required.

---

### INFO (4)

[INFO] ADR overrides DOC — Backend language: Go chosen over Ktor recommendation
  Note: docs/01-architecture-evaluation.md (DOC, precedence 3) recommended Ktor Server with a
  :core KMP module for compile-time type sharing between Android, iOS, and the backend.
  docs/02-stack.md (ADR, precedence 0, LOCKED) chose Go instead, citing operational simplicity,
  fast compile, low memory footprint, and easy onboarding.
  The DOC itself acknowledges the decision: its header states "DECISIONS FINALISED — See
  02-stack.md for the confirmed stack." The ADR recovers the type-safety benefit via an
  OpenAPI spec + oapi-codegen approach described in the DOC's own "If you later decide to
  switch to Go" section.
  source Found: docs/01-architecture-evaluation.md (recommendation: Ktor Server)
  source Expected: docs/02-stack.md (decision: Go)
  → No action required. ADR wins. DOC retained as historical rationale only.

[INFO] ADR overrides DOC — Kotlin version pinned at 2.0.21 vs DOC's 2.4.0 recommendation
  Note: docs/01-architecture-evaluation.md (DOC, precedence 3) stated "a project starting now
  should use Kotlin 2.4.0 + SKIE 0.10.13" based on compatibility tables current at June 2026.
  docs/02-stack.md (ADR, precedence 0, LOCKED) pins Kotlin at 2.0.21 with a note to "verify
  latest stable at scaffold time."
  This is not a contradiction — the ADR note implies the version was set at scaffold time and
  should be verified. The DOC recommendation represents a point-in-time reading of SKIE/Kotlin
  compatibility, not a fixed requirement.
  source Found: docs/01-architecture-evaluation.md (recommendation: Kotlin 2.4.0 + SKIE 0.10.13)
  source Expected: docs/02-stack.md (pinned: Kotlin 2.0.21)
  → No action required. Verify Kotlin version is current stable at scaffold time per ADR guidance.
  If 2.0.21 is outdated relative to current SKIE support, update the ADR and pins together.

[INFO] ADR overrides DOC — No :core module in confirmed stack
  Note: docs/01-architecture-evaluation.md (DOC, precedence 3) proposed a three-module KMP
  structure: :core (serializable DTOs only, jvm() target), :shared (SQLDelight + Ktor Client),
  :backend (Ktor server). This design was specific to the Ktor backend option.
  docs/02-stack.md (ADR, precedence 0, LOCKED) defines only :shared (KMP shared module) and
  :android / ios for the mobile layers. The :core module split is irrelevant with a Go backend
  because Go does not consume KMP JVM artifacts.
  source Found: docs/01-architecture-evaluation.md (proposed: :core + :shared + :backend modules)
  source Expected: docs/02-stack.md (confirmed: :shared + :android + ios only)
  → No action required. The :core module proposal is superseded by the Go decision.

[INFO] Cross-reference cycle detected — docs/04-data-model.md and docs/05-privacy.md
  Note: docs/04-data-model.md lists docs/05-privacy.md in its cross_refs; docs/05-privacy.md
  lists docs/04-data-model.md in its cross_refs. This is a directed graph cycle between two
  SPEC documents.
  Assessment: the cycle is a documentation reference cycle, not a logical dependency cycle.
  04-data-model.md is authoritative on schema structure; docs/05-privacy.md is authoritative
  on regulatory obligations. They reference each other for complementary information (e.g.,
  "see the consent_events table" and "see the erasure spec"). There is no circular synthesis
  dependency and no content contradiction between the two documents.
  → No action required. Both documents were synthesized from source. If an automated synthesis
  tool trips on this cycle, add a cycle-break annotation to one cross_refs list in the
  classification JSON.
