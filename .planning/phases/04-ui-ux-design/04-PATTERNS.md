# Phase 4: UI/UX Design — Pattern Map

**Mapped:** 2026-06-29
**Files analyzed:** 3 new markdown documents
**Analogs found:** 3 / 3

---

## File Classification

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `docs/DESIGN-TOKENS.md` | reference doc | transform (values extracted from UI-SPEC.md) | `docs/06-auth.md` | role-match (reference tables, two-column platform comparisons) |
| `docs/SCREEN-FLOWS.md` | architecture doc | event-driven (user navigation paths) | `docs/03-system-architecture.md` | role-match (system diagrams, ASCII art, sequential flow docs) |
| `docs/WIREFRAMES.md` | specification doc | request-response (screen state → layout description) | `docs/07-sync-and-notifications.md` | partial-match (numbered sequences, state descriptions, annotated prose) |

---

## Pattern Assignments

### `docs/DESIGN-TOKENS.md` (reference doc, transform)

**Analog:** `docs/06-auth.md`

**File header pattern** (`docs/06-auth.md` lines 1–4):
```markdown
# Authentication & Family Model

> Last updated: 2026-06-25

```

Apply to DESIGN-TOKENS.md as:
```markdown
# Design Tokens — OneStepTwo

> Source: `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` (approved 2026-06-29)
> Values must not be altered — all modifications require UI-SPEC.md to be updated first.

```

**Table structure pattern** (`docs/06-auth.md` lines 27–31):
```markdown
| Role | Clerk token value | Can do |
|---|---|---|
| `admin` | `org:admin` | Full access — add/remove members, create/delete child profiles, view and log all events |
| `caregiver` | `org:caregiver` | Log events and view history — cannot manage the family or other members |
```

Apply as the per-category token table format. Each category gets its own `## {Category} Tokens` section with a markdown table. Token names use backtick code formatting. Column order per D-23: `Token | Compose value | SwiftUI value`. For Color tokens, expand to: `Token | Light | Dark | Usage`.

**Section heading pattern** (`docs/06-auth.md` lines 5, 13, 24, 38 — all `##` headings for each major concept):
```markdown
## Color Tokens
## Typography Tokens
## Spacing Tokens
## Corner Radius Tokens
## Elevation Tokens
## Motion Tokens
```

Six `##`-level sections exactly. No sub-sections within a token category except Color (which has `### Semantic Tokens`, `### Primary Purple Scale`, `### Heatmap Intensity Colors` matching UI-SPEC.md §Color structure).

**Code-formatted values pattern** (`docs/06-auth.md` lines 35–36 — inline code for exact values):
```markdown
Always check `"org:admin"` and `"org:caregiver"` — not `"admin"` or `"caregiver"`.
```

Apply: all token values in tables must be backtick-formatted when they are code literals (e.g., `tween(150)`, `Color(0xFF7E22CE)`, `.easeInOut(duration: 0.15)`). Raw hex values (`#7E22CE`) use inline code only in the Color table; plain numeric values (e.g., `8dp`) do not need backticks.

**Critical extraction rule:** Every value in DESIGN-TOKENS.md traces to a specific UI-SPEC.md section:
- Color → UI-SPEC.md §Color (lines 99–161)
- Typography → UI-SPEC.md §Typography (lines 70–76)
- Spacing → UI-SPEC.md §Spacing Scale (lines 40–49)
- Corner Radius → UI-SPEC.md §Corner Radii (lines 252–256)
- Elevation → UI-SPEC.md §Elevation (lines 262–268)
- Motion → UI-SPEC.md §Motion Tokens (lines 275–293)

---

### `docs/SCREEN-FLOWS.md` (architecture doc, event-driven)

**Analog:** `docs/03-system-architecture.md`

**File header pattern** (`docs/03-system-architecture.md` lines 1–3):
```markdown
# System Architecture

> Last updated: 2026-06-25

```

Apply to SCREEN-FLOWS.md as:
```markdown
# Screen Flows — OneStepTwo

> Source: `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` §Screen Inventory, §Navigation Patterns
> Last updated: 2026-06-29

```

**ASCII diagram pattern** (`docs/03-system-architecture.md` lines 7–52 — full component map in a fenced code block):
```markdown
```
┌─────────────────────────────────────────────────────┐
│                   Mobile Clients                    │
│  ┌──────────────────┐    ┌──────────────────┐      │
│  │   Android App    │    │    iOS App       │      │
│  └────────┬─────────┘    └────────┬─────────┘      │
│           └──────────┬────────────┘                 │
│          ┌───────────▼────────────┐                 │
│          │     KMP Shared Module  │                 │
│          └───────────┬────────────┘                 │
└──────────────────────┼──────────────────────────────┘
```
```

This confirms: ASCII art and box-drawing characters are established project convention. SCREEN-FLOWS.md may use Mermaid diagrams in fenced blocks (` ```mermaid `) OR plain ASCII flows in fenced code blocks. The existing docs use fenced ASCII — Mermaid is additive.

**Section heading pattern** (`docs/03-system-architecture.md` line 55 onward — `##` per major flow area):

SCREEN-FLOWS.md should have:
```markdown
## Auth + Onboarding Flow
## Main App Navigation
## Home Tab Interaction Flow (Log → Toast → Sheet)
## Platform-Specific Navigation Notes
```

**Numbered sequence pattern** (`docs/07-sync-and-notifications.md` lines 12–16):
```markdown
1. User logs an event → written to SQLDelight immediately with `sync_status = 'pending'`
2. Ktor Client sends `POST /children/{id}/events` to Go API
3. Go API inserts into PostgreSQL via `ON CONFLICT (id) DO NOTHING`
4. On success, local SQLDelight record is updated to `sync_status = 'synced'`
5. On failure (offline, timeout), record stays `pending` — on next reconnect...
```

Apply for the Home Tab Interaction Flow section in SCREEN-FLOWS.md — the log → toast → sheet chain is a numbered sequence (not a diagram) matching this pattern exactly. Source: UI-SPEC.md §Log Button → Toast → Bottom Sheet Flow (lines 615–623).

**Blockquote callout pattern** (`docs/07-sync-and-notifications.md` line 32):
```markdown
> **Revisit before scaling:** ...
```

Apply as a platform-exception callout in SCREEN-FLOWS.md:
```markdown
> **Platform exception (D-10):** iOS uses NavigationStack swipe-from-left-edge; Android uses system predictive back gesture. No custom back button on either platform.
```

---

### `docs/WIREFRAMES.md` (specification doc, request-response)

**Analog:** `docs/03-system-architecture.md` (ASCII art) + `docs/06-auth.md` (structured per-item specs)

**File header pattern** (`docs/03-system-architecture.md` lines 1–3 as analog):
```markdown
# Wireframes — OneStepTwo

> Source: `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` §Screen Inventory, §Component Inventory
> Last updated: 2026-06-29
> 22+ distinct screen/state wireframes. Each wireframe includes layout annotations and component callout refs.

```

**Per-wireframe structure pattern** — based on ASCII convention from `docs/03-system-architecture.md` and the RESEARCH.md Pattern 3 excerpt (which is itself derived from the project's established ASCII style):

```markdown
### {Screen Name} — {State Variant}

```
┌────────────────────────────────────────┐
│ STATUS BAR (system insets)             │
├────────────────────────────────────────┤
│                                        │
│  [component zone with annotation]      │
│  · Typography: Title 20sp semibold     │
│  · Color: color.on-background          │
│                                        │
├────────────────────────────────────────┤
│  Home  │ History │ Progress │ Settings │
│   ●    │         │          │          │
└────────────────────────────────────────┘
```
Ref: UI-SPEC §{Section Name}, §{Component Name}
```

**Group heading pattern** — `##` for each group, `###` for each screen/state:
```markdown
## Group A — Auth + Org Screens
### Sign In — Default State
### Sign In — Error State
...
## Group B — Onboarding Wizard
### Step 2 — Family Name Input
...
```

Six groups total (A through F) matching the RESEARCH.md Screen Inventory.

**Annotation line style** — bullet points below the ASCII box with `·` prefix, not `-`, matching the excerpt style from RESEARCH.md Pattern 3:
```
· Typography: Body 16sp regular, color.on-background
· Color: color.primary background, color.on-primary text
· Elevation: overlay (8dp shadow)
· a11y: contentDescription = "Log potty trip", role = button, 48dp min tap target
```

**Ref line pattern** — each wireframe ends with a `Ref:` line cross-referencing UI-SPEC.md sections:
```
Ref: UI-SPEC §Home Tab, §Log Button (component 2), §Status Chips (component 8)
```

---

## Shared Patterns

### File Header Convention
**Source:** `docs/06-auth.md` lines 1–3, `docs/03-system-architecture.md` lines 1–3
**Apply to:** All three new docs files
```markdown
# {Document Title}

> {Source reference or last updated line}

```

All three docs use a `# Title` followed immediately by a `>` blockquote metadata line. No YAML front matter. No phase/status metadata block (those live in `.planning/` files, not `docs/`).

### Markdown Table Style
**Source:** `docs/06-auth.md` lines 27–31
**Apply to:** All tables in DESIGN-TOKENS.md and any summary tables in SCREEN-FLOWS.md
- Pipes aligned, header separator row uses `---` per cell
- Column count kept minimal — no more columns than necessary
- Token/code values in backtick code spans within table cells

### Section Hierarchy
**Source:** All existing docs files consistently use `##` for major sections, `###` for subsections
**Apply to:** All three new docs files
- `#` — document title only
- `##` — major section (token category, flow group, wireframe group)
- `###` — individual item (per-screen wireframe, per-subsection)
- No `####` headings anywhere in existing docs; avoid in new files

### ASCII Box-Drawing Wireframe Convention
**Source:** `docs/03-system-architecture.md` lines 7–52
**Apply to:** `docs/WIREFRAMES.md` and any flow diagrams in `docs/SCREEN-FLOWS.md`
- Outer border: `┌─┐` / `│` / `└─┘` with `├─┤` for horizontal dividers
- Width: 40–42 characters (matching the existing system architecture diagram)
- Fenced plain code block (` ``` ` without language tag) — consistent with `docs/03-system-architecture.md`

### Cross-Reference Format
**Source:** `docs/06-auth.md` line 7 — `> Last updated:` and inline section references
**Apply to:** All `Ref:` lines in WIREFRAMES.md and source citations in DESIGN-TOKENS.md
```
Ref: UI-SPEC §{Section Name} (component {N})
Source: D-{NN}, UI-SPEC.md §{Section Name}
```

---

## No Analog Found

All three files have close analogs in the existing `docs/` directory. No files require fallback to RESEARCH.md patterns alone.

| File | Analog Quality | Note |
|------|---------------|------|
| `docs/DESIGN-TOKENS.md` | role-match | `docs/06-auth.md` provides table structure and code-formatting conventions |
| `docs/SCREEN-FLOWS.md` | role-match | `docs/03-system-architecture.md` provides diagram and section conventions |
| `docs/WIREFRAMES.md` | partial-match | Both `docs/03-system-architecture.md` (ASCII) and `docs/06-auth.md` (per-item specs) combined |

---

## Metadata

**Analog search scope:** `docs/` directory (7 existing files)
**Files scanned:** 4 (03-system-architecture.md, 06-auth.md, 07-sync-and-notifications.md, 04-data-model.md)
**Pattern extraction date:** 2026-06-29
**Source of truth for all token values:** `.planning/phases/04-ui-ux-design/04-UI-SPEC.md`
