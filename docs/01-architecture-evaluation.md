# OneStepTwo — Architecture Evaluation (Historical)

> **Status: DECISIONS FINALISED — See `02-stack.md` for the confirmed stack.**
> Generated: 2026-06-25

This document captures the research and reasoning behind the initial stack decisions. Kept as a reference for *why* choices were made, not as an active guide.

---

## Scope

This document evaluates three open architecture decisions before scaffold initialization:

1. Backend runtime: Ktor Server (Kotlin) vs Go
2. Auth provider: Clerk vs Kinde
3. KMP + SKIE stability status

---

## 1. Backend: Ktor Server vs Go

### The Core Trade-off

Ktor's appeal is sharing Kotlin data model classes between the KMP `/shared` module and the API server — a compile-time contract across the entire stack. Go's appeal is operational simplicity: a static binary, a 10–20 MB Docker image, and ~100 ms startup.

### Can You Actually Share Models?

Yes — cleanly — but it requires one structural change you should make at day one.

**The mechanism:** A KMP module that declares `jvm()` in its Gradle targets publishes a regular JVM jar. A plain JVM Gradle module (the Ktor server) depends on that jar like any other library. `@Serializable` from `kotlinx.serialization` is a multiplatform annotation with no platform-specific behavior.

**The required change:** Do not point the Ktor server directly at your `/shared` module. That module carries SQLDelight schema code and the Ktor *Client*, which a server has no use for. The correct pattern is to extract a slim `:core` module:

```
/core          ← new: only @Serializable DTOs/API models
               targets: androidTarget(), iosX64(), iosSimulatorArm64(), iosArm64(), jvm()
               deps: only kotlinx-serialization-json in commonMain

/shared        ← existing: SQLDelight, Ktor Client, business logic
               depends on :core

/backend       ← Ktor server (pure JVM)
               depends on :core — gets the JVM artifact automatically
```

JetBrains's own 2026 recommended KMP default structure formalizes exactly this pattern.

**Most common mistake:** declaring `jvm()` target on `:core` but forgetting to publish it to the local Maven repo before the server module resolves it. Set `kotlin.native.cocoapods.generate.wrapper=true` and confirm `./gradlew :core:publishToMavenLocal` runs before `:backend:compileKotlin` in your build graph.

### Railway-Specific Numbers

| Metric | Ktor (JVM on Temurin 21) | Go (static binary) |
|---|---|---|
| Startup time | 2–4 seconds | < 100 ms |
| Idle memory | 70–90 MB | 10–30 MB |
| Docker image size | 250–500 MB (JRE base) | 10–20 MB |
| Redeploy total time | Container restart + 2–4 s JVM = noticeably slower | Container restart only |
| Cold start (if app sleeping enabled) | ~12–34 s total | ~10–30 s container only |

**Critical Railway context:** Railway does *not* sleep apps by default. Sleeping is opt-in. On always-on paid tiers (Hobby at $5/mo, Pro at $20/mo), startup time is only relevant on deploy, not on every idle period. This significantly weakens Go's cold-start argument.

**JVM memory on constrained Railway containers:** You must explicitly set JVM flags. Without them, the JVM reads host memory rather than cgroup limits and can be OOM-killed:

```
JAVA_OPTS=-Xms64m -Xmx400m -XX:MaxMetaspaceSize=128m -XX:+UseContainerSupport
```

`-XX:+UseContainerSupport` is on by default in JDK 11+ (Railway uses Temurin 21), but make it explicit in your Railway environment variables.

### Developer Experience

| Concern | Ktor + KMP sharing | Go separate models |
|---|---|---|
| Type safety across layers | Compile-time guaranteed | Manual or OpenAPI-generated |
| Model change cost | Change once, propagates to Android + iOS + API | Update two files |
| Build complexity | High (Gradle, KMP multi-target, plugin versions) | Low (`go build`) |
| Idle memory on Railway | 70–90 MB | 10–30 MB |
| Docker image | 250–500 MB | 10–20 MB |
| Team onboarding | Kotlin required across all layers | Go learnable in 1–2 weeks |
| Build time on Railway | Minutes (Gradle + KMP) | Seconds |

### Recommendation: **Ktor Server**

**Rationale:** OneStepTwo is a product still finding its shape. The API surface will change frequently during early development — milestone fields, event types, child profile attributes, family sharing models. In that context, a compile-time guarantee that Android, iOS, and the server all agree on the shape of every request and response is genuinely valuable, not theoretical.

The `:core` module split is a half-day of Gradle work. Once done, the model-sharing benefit compounds indefinitely. Go's operational advantages (smaller image, faster start) are real but largely neutralized by always-on Railway deployment.

**If you later decide to switch to Go:** invest 2 hours writing an OpenAPI spec first. `oapi-codegen` generates typed Go server stubs and models from the spec. You can generate a TypeScript client for SvelteKit from the same spec. This recovers most of the "contract safety" without Gradle complexity — but it is a different workflow to maintain.

**Go remains the right answer if:** you anticipate multi-language contributors, you care deeply about Railway image pull speed, or you will enable app sleeping to save cost (the cold-start penalty becomes real).

---

## 2. Auth Provider: Clerk vs Kinde

### The Core Questions

This stack has three demanding auth requirements that most auth provider comparisons ignore:

1. Native Android SDK with headless JWT capability (no browser-redirect-only path), to integrate cleanly into a KMP Ktor networking layer
2. Native iOS SDK with the same headless capability
3. Organizations-as-Families: every user is inside an org; the free tier must support more than a handful of orgs

### SDK Comparison

**Clerk — Android**
GA'd September 2025. Two artifacts: `clerk-android-api` (core auth, no UI) and `clerk-android-ui` (optional Compose components). The split is critical: `clerk-android-api` gives you headless JWT management without a browser redirect. Token refresh is handled internally by the SDK.

**Clerk — iOS**
Native Swift, distributed via SPM. Two packages: `ClerkKit` (headless) and `ClerkKitUI` (optional SwiftUI components). 132+ releases over 2 years. Actively maintained (last commit < 1 week ago).

**Kinde — Android**
Native Kotlin SDK, published to Maven. **Hard constraint: authentication requires browser-based redirects.** OAuth 2.0 + PKCE via system browser or custom tab. No documented headless-only token path. You can implement raw PKCE yourself against Kinde's OIDC endpoints (they support it), but that means building and maintaining the full auth flow in KMP Kotlin — code verifier, challenge, token endpoint, refresh logic — without SDK help.

**Kinde — iOS**
Native Swift, built on AppAuth. Same browser-redirect requirement as Android. No headless path in the SDK.

### SvelteKit Integration

| | Clerk | Kinde |
|---|---|---|
| Official SvelteKit SDK | No | Yes (`@kinde-oss/kinde-auth-sveltekit`) |
| SSR-compatible | Yes (via community packages) | Yes (official) |
| SvelteKit `locals` injection | Yes (markjaquith community adapter) | Yes (official) |
| Maintenance risk | Community-dependent | Low (Kinde-owned) |

Kinde has the cleaner SvelteKit story. The gap is real — but `clerk-sveltekit` (markjaquith) is actively used in production, handles `locals` injection, supports a headless bundle import path, and is stable.

### Free Tier

| Limit | Clerk | Kinde |
|---|---|---|
| Monthly active users | 50,000 MRU | 10,500 MAU |
| Free organizations | **100** | **5** |
| Members per org (free) | 20 | Unlimited |
| MFA (free) | No (Pro required, $25/mo) | Yes |

**The Kinde org limit is a dealbreaker.** Your architecture puts every user inside an Organization by design — one org per family. Five free orgs means you can serve exactly five families before hitting the wall. You will blow past this limit before your first real beta. Clerk gives you 100 free orgs with 20 members each, which covers you through launch and meaningful early growth with zero auth spend.

### KMP Integration Pattern (Clerk)

```
Android platform layer:
  - clerk-android-api artifact (no UI dependency)
  - ClerkAndroid.signIn() → returns session token
  - Pass token to shared KMP AuthRepository interface

iOS platform layer:
  - ClerkKit package (no UI dependency)
  - Clerk.shared.session?.lastActiveToken → JWT string
  - Pass token to shared KMP AuthRepository interface

Shared KMP module:
  - AuthRepository stores and vends current token
  - Ktor client plugin adds Authorization: Bearer <token> to every request
  - On 401: call platform AuthRepository.refresh() → new token → retry request
```

No custom PKCE implementation. No browser round-trip in the core token-fetching path (login screens are handled once by the native SDK; subsequent requests are token-only).

### Recommendation: **Clerk**

| Criterion | Clerk | Kinde | Winner |
|---|---|---|---|
| Headless JWT for KMP Ktor | Yes (API-only artifacts) | DIY raw PKCE only | **Clerk** |
| Free orgs | 100 | **5** | **Clerk** |
| Free MAU | 50,000 | 10,500 | **Clerk** |
| Native Android SDK | Yes, headless | Yes, browser-redirect only | **Clerk** |
| Native iOS SDK | Yes, headless | Yes, browser-redirect only | **Clerk** |
| SvelteKit SDK | Community | Official | Kinde |
| MFA free | No | Yes | Kinde |

Kinde's 5-org free limit and browser-only SDK paths make it the wrong choice for this specific architecture. Clerk's headless-capable native SDKs and 100-org free tier are decisive.

**One genuine Clerk gotcha:** MFA (TOTP/SMS) is gated behind the Pro plan at $25/month. If MFA is a day-one requirement, budget for it. For a family tracking app in early development this is unlikely to be launch-blocking.

---

## 3. KMP + SKIE: Compatibility & Red Flags

### Current Version Status

As of June 2026, SKIE tracks Kotlin releases within days:

| SKIE | Kotlin Supported |
|---|---|
| 0.10.3 | 2.2.0 |
| 0.10.4 | 2.2.0 + Gradle 9.0; **dropped Kotlin 1.9.x** |
| 0.10.10 | 2.3.10 |
| 0.10.13 (current) | 2.4.0 (shipped June 24, 2026 — 21 days after Kotlin 2.4.0) |

SKIE officially supports Kotlin 2.0.0 through 2.4.0. A project starting now should use Kotlin 2.4.0 + SKIE 0.10.13.

### Known Issues and Workarounds

**swiftBundling broke in Kotlin 2.1.0 (fixed in SKIE 0.10.1)**
Kotlin 2.1.0 changed `.klib` output from files to directories, breaking SKIE's Swift code bundling feature. Fixed in 0.10.1. No impact if you are starting fresh on 2.4.0 + 0.10.13.

**macOS framework build failure in SKIE 0.10.4 + Kotlin 2.2.0 (closed)**
A `.swiftmodule` was placed in the wrong path for macOS XCFramework targets, causing `FileAlreadyExistsException`. Fixed in a follow-on patch. Not relevant unless you have a macOS target.

**`Kotlin.Result<T>` does not bridge to Swift `Result<T>`**
SKIE does not convert `Kotlin.Result<T>` into a Swift-idiomatic `Result<T, Error>`. It surfaces as an opaque `KotlinResult` object. **Use sealed classes for error states instead** — SKIE converts Kotlin sealed classes to exhaustive Swift enums, which is idiomatic Swift.

**Generics do not cross the bridge cleanly**
Do not expose generic types at the KMP boundary. Use concrete typed classes per domain: `UserRepository`, `MilestoneRepository`, not `Repository<User>`, `Repository<Milestone>`.

**Threading at the Ktor → SQLDelight → SKIE boundary**
The highest-risk real-world issue for this stack. Chain: Ktor HTTP call (on `Dispatchers.IO`) → result flows through repository → exposed as a `suspend fun` → SKIE bridges it to Swift `async`. Swift may resume on a different thread than Kotlin expects. SQLDelight's `NativeSqliteDriver` has its own thread requirements. Mitigation: explicitly wrap cross-boundary return values in `withContext(Dispatchers.Main)` or `withContext(Dispatchers.Default)` at the boundary layer. Test cancellation paths explicitly.

**Swift Export vs SKIE — choose now**
JetBrains is building Swift Export as a next-generation replacement for the Obj-C interop layer that SKIE hooks into. SKIE and Swift Export are mutually exclusive — you cannot use both. Swift Export is still incomplete for production use in mid-2025 (generics severely limited, no migration tooling). Touchlab explicitly recommends SKIE for the foreseeable future. You are committing to a migration project when Swift Export reaches parity (estimated 2026–2027), but that is an acceptable trade-off.

### Maintenance Status

SKIE is **actively maintained** with **high confidence**.

- Touchlab is a commercial KMP consultancy; SKIE is their flagship product
- Every Kotlin release gets a SKIE patch within days (confirmed for 2.0, 2.1, 2.2, 2.3, 2.4)
- Active support on KotlinLang Slack `#touchlab-tools`
- No signs of issue queue neglect or release cadence decay

### Recommendation: **Proceed with SKIE**

Production-ready. Pin Kotlin and upgrade SKIE + Kotlin together deliberately rather than auto-upgrading Kotlin in CI. The window where a new Kotlin version exists but SKIE does not yet support it creates a brief iOS build block — short (days), but unpredictable if you auto-upgrade.

**Do not mix SKIE with KMP-NativeCoroutines** (Rick Clephas). Conflicting cancellation behavior and confusing Swift call sites. Pick SKIE exclusively.

---

## Summary Recommendations

| Decision | Recommendation | Confidence |
|---|---|---|
| Backend | **Ktor Server** (Kotlin) | Medium-High |
| Auth Provider | **Clerk** | High |
| KMP + SKIE | **Proceed** — production-ready | High |

### Revised Stack (Final)

```
/androidApp     Native Android (Jetpack Compose)
/iosApp         Native iOS (SwiftUI)
/core           KMP module: @Serializable DTOs only (new)
                targets: android, iosX64, iosSimulatorArm64, iosArm64, jvm
/shared         KMP module: SQLDelight, Ktor Client, business logic
                depends on :core
/backend        Ktor Server (Kotlin JVM)
                depends on :core
                deploy target: Railway (always-on, Temurin 21)
/frontend       Svelte 5 / SvelteKit
                auth: Clerk (clerk-sveltekit community adapter)
/docs           Architecture and decision records
```

### Day-One Action Items (pending your approval)

1. Create the `:core` KMP module with `jvm()` target before writing any data models
2. Add `clerk-android-api` (not the full clerk-android) to the Android module
3. Add `ClerkKit` (not ClerkKitUI) to the iOS module
4. Pin Kotlin and SKIE versions explicitly in root `gradle/libs.versions.toml`; disable auto-upgrade
5. Set Railway environment variable: `JAVA_OPTS=-Xms64m -Xmx400m -XX:MaxMetaspaceSize=128m -XX:+UseContainerSupport`

---

*Awaiting stack approval before scaffold initialization proceeds.*
