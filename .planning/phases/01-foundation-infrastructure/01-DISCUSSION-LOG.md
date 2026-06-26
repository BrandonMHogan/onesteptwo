# Phase 1: Foundation & Infrastructure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-25
**Phase:** 1-Foundation & Infrastructure
**Areas discussed:** iOS CI runners, OpenAPI initial scope, Railway deploy trigger, Gradle version catalog

---

## iOS CI Runners

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub Actions macOS runner | Runs alongside Go + Android jobs. ~10× cost multiplier vs Linux. | |
| Xcode Cloud | Apple's native CI — 25 free compute hours/month. Separate from GitHub Actions. | |
| Skip iOS CI for now | Android + Go on Linux CI from day one. iOS verified locally until real code exists. | ✓ |

**User's choice:** Skip iOS CI for now

---

### CI Pipeline Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Go build + test + Android build | Linux runner only. Covers Go healthcheck, Android KMP build, 1.sqm migration. | ✓ |
| Go + Android + migration schema test | Same plus a dedicated goose dry-run migration check job. | |
| Go + Android only, migrations manual | Migrations verified manually until Phase 2. | |

**User's choice:** Go build + test + Android build (Recommended)

---

## OpenAPI Initial Scope

### Spec bootstrap

| Option | Description | Selected |
|--------|-------------|----------|
| Just /healthz | Spec starts minimal — only what Phase 1 implements. Grows with each phase. | ✓ |
| Full v1 endpoint stubs | Seed all v1 endpoints so oapi-codegen generates handler stubs from day one. | |
| Healthz + Phase 2 compliance endpoints | Seed Phase 1 + Phase 2 endpoints together. | |

**User's choice:** Just /healthz

---

### Codegen in CI

| Option | Description | Selected |
|--------|-------------|----------|
| Local only — generated.go committed | Matches PROJECT.md decision. CI verifies it compiles. | ✓ |
| CI regenerates and fails on diff | Ensures generated code is never stale; adds oapi-codegen to CI toolchain. | |

**User's choice:** Local only — generated.go committed

---

## Railway Deploy Trigger

### Deploy model

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-deploy from main branch | Railway watches main; every merged commit deploys. | |
| Manual deploy via Railway CLI / dashboard | Intentional deploys, no automation. | |
| GitHub Actions deploys via railway CLI | CI runs `railway up` after tests pass. | |
| Other (free text) | User described a branch-based model. | ✓ |

**User's choice:** "We have a production railway that would watch a branch called production. The staging railways would watch main."

**Notes:** Production Railway service watches the `production` branch. Staging Railway service watches `main`. The `production` branch does not exist yet — needs to be created as part of Phase 1.

---

### Production release process

| Option | Description | Selected |
|--------|-------------|----------|
| Manual PR from main → production | Deliberate release step with audit trail. | ✓ |
| Direct push / force-merge to production | Simpler, no PR review on production merges. | |
| GitHub Actions release workflow | Automated tag-triggered merge. | |

**User's choice:** Manual PR from main → production

---

## Gradle Version Catalog

### Dependency management

| Option | Description | Selected |
|--------|-------------|----------|
| libs.versions.toml version catalog | Centralized, IDE autocomplete, Renovate/Dependabot support. | ✓ |
| Direct declarations per build.gradle.kts | Simpler but version drift as modules grow. | |
| buildSrc with convention plugins | Overkill for current project size. | |

**User's choice:** libs.versions.toml version catalog (Recommended)

---

### Gradle module structure

| Option | Description | Selected |
|--------|-------------|----------|
| :shared + :androidApp | Two Gradle modules. iOS is an Xcode project consuming the KMP XCFramework. | ✓ |
| :shared + :androidApp + :iosApp placeholder | Adds an iOS marker module. | |
| Flat single-module with source sets | Everything in one module. | |

**User's choice:** :shared + :androidApp (Recommended)

---

## Claude's Discretion

- Go HTTP router choice for Phase 1 healthcheck (stdlib `net/http` is sufficient)
- Exact Gradle wrapper version
- Railway deployment configuration method (railway.json vs Nixpacks vs Dockerfile)
- goose migration directory location within the backend
- GitHub Actions job naming and structure

## Deferred Ideas

None — discussion stayed within Phase 1 scope.
