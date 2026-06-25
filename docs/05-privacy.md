# Privacy & Compliance

> Last updated: 2026-06-25

OneStepTwo targets users globally. This document covers our approach to child data privacy and the regulations that apply.

## Regulations

| Regulation | Region | Applies Because |
|---|---|---|
| COPPA | United States | We process data about children under 13 |
| GDPR | European Union / UK | We have EU/UK users |
| PIPEDA / Law 25 | Canada / Quebec | We have Canadian users |
| POPIA | South Africa | If we accept SA users |

These differ in detail but share the same core requirements: parental consent before collecting child data, the right to access and erase data, and data minimisation. Our approach satisfies all of them by keeping child data as minimal as possible.

## What We Store on Children

**Nickname** and **birth month + year**. That is all.

No legal name, no full date of birth, no gender, no photo, no medical history, no device identifiers tied to the child. Every field added to the child profile in future must be documented in `04-data-model.md` with a written justification before any migration is written.

## What Clerk Stores (Not Us)

Parent and caregiver PII — name, email, phone number — lives in Clerk, not in our database. Our database holds only a `clerk_user_id` foreign key. Clerk is SOC 2 Type II certified and handles their own GDPR obligations as a data processor. When a user deletes their Clerk account, their PII is Clerk's responsibility to erase.

## Consent

Before a child profile is created, the parent must explicitly consent to data collection on behalf of the child. The consent screen must include:

1. A self-attestation checkbox: **"I confirm I am the parent or legal guardian of this child and am 18 years of age or older."**
2. Plain-language explanation of what data is collected and why.

The consent event must be logged (timestamp, which user consented, which child profile was created as a result) — see `consent_events` table in `04-data-model.md`. This is a legal requirement under COPPA and GDPR — not a nice-to-have.

Self-attestation is the minimum acceptable standard under COPPA for most app categories. Verify with legal counsel whether verified parental consent (VPC) — a higher bar involving email confirmation or similar — is required for target markets before launch.

## Right to Erasure

Users have the legal right to request deletion of all data we hold on them and their children. This must be:

1. **Easy to find** — not buried in settings. The delete flow should be clearly labelled and presented during offboarding ("graduating" from the app is our core narrative — lean into it).
2. **Complete** — deleting a child profile removes every associated row. Deleting an account removes every child profile. See the cascade spec in `04-data-model.md`.
3. **Hard delete** — soft deletes (`deleted_at`) are for user-facing event cleanup. Erasure requests produce real `DELETE` statements. No orphaned rows.
4. **Verifiable** — the Go API's deletion endpoints should return a confirmation of what was deleted. Log the erasure event (who requested it, when, what was removed) in an audit table that is itself purged after 90 days.

## Data Retention

Potty events are retained until the user explicitly deletes them or closes their account. There is no automatic expiry in v1.

GDPR's storage limitation principle requires data not be kept longer than necessary. The narrative of this app is that families "graduate" — at that point, the offboarding flow encourages account deletion, which hard-deletes all data. This is the retention mechanism: user-initiated, not time-based.

Revisit if regulators or legal counsel require a defined maximum retention window.

## Data Minimisation

We do not collect data we do not need. Before adding any new field to any table, ask:
- Does a feature actually require this?
- Is there a less sensitive way to achieve the same result?
- What is the deletion path if a user requests erasure?

## Data Residency

Railway supports both US and EU regions. When EU users are onboarded at scale, evaluate whether the Go API and PostgreSQL instance should move to or replicate to an EU Railway region. This is not a day-one requirement but should not be an afterthought.

## Right to Data Portability

Under GDPR Article 20, users have the right to receive a copy of their data in a machine-readable format. This is distinct from erasure.

Implementation is deferred to v2 but must not be forgotten. The export endpoint (`GET /v1/account/export`) returns a JSON bundle of all children, events, preferences, and consent records for the requesting user's org. The deletion cascade already identifies every relevant table — the export reads the same set.

## What to Write Before Launch

- [ ] Privacy policy (with a lawyer, not Claude)
- [ ] Terms of service
- [ ] In-app consent screen for child profile creation (copy must be plain language, not legalese)
- [ ] Clear offboarding / deletion flow in the app UI
- [ ] `docs/06-data-map.md` — one row per database table listing data stored, legal basis, and deletion path
- [ ] `GET /v1/account/export` endpoint (v2) — GDPR Article 20 data portability
