# Phase 5 Stage 2 — History Tab, Settings Tab, Backend Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Stage 2 of Phase 5 (Core Event Logging) — the History tab (rolling heatmap + day-detail drill-down + per-event delete), the full Settings tab (family management, child management, notification preferences, account erasure), and the three backend pieces they depend on (`GET`/`PUT /v1/notification-preferences`, `PATCH /v1/children/{id}`).

**Architecture:** Two new Go endpoints follow the existing `backend/internal/api/server.go` handler conventions exactly (claims-based auth, `WriteProblem` for errors, `childListResponse`-style DTOs). Android/Kotlin additions follow Stage 1's established patterns: a repository per SQLDelight table, a `ViewModel` + `ViewModelFactory` pair per screen/section, `AppContainer` as the single hand-rolled composition root, and destructive actions routed through the existing `DestructiveConfirmDialog`. Settings actions call the real Go/Clerk APIs directly — no offline queue (05-CONTEXT.md D-02) — while a local SQLDelight table caches the caller's own notification preference for instant, offline-tolerant display.

**Tech Stack:** Go 1.x + `database/sql` + `lib/pq` + oapi-codegen (backend), Kotlin Multiplatform + SQLDelight 2.3.2 + Jetpack Compose + Ktor client + Clerk Android SDK 1.0.31 (Android/shared).

## Global Constraints

- `event_type` is the locked 5-value set `pee, poo, both, accident, tried` (05-CONTEXT.md D-04) — never the 6-value split from the imported design mockup.
- Settings actions (child CRUD, notification toggle, invite/remove caregiver) call real Go/Clerk APIs directly — no offline queue, no `sync_status` semantics (05-CONTEXT.md D-01/D-02). Potty events remain fully local-only; this plan does not touch `/v1/events` (that's Phase 6).
- Every destructive action uses the existing `DestructiveConfirmDialog` (`androidApp/src/main/kotlin/com/onesteptwo/android/ui/common/DestructiveConfirmDialog.kt`) with copy from `04-UI-SPEC.md` §Destructive Action Confirmations where a row exists, or the same title/body/confirm(error)/dismiss shape where the table has a gap (05-CONTEXT.md D-06).
- Caregiver removal AND caregiver self-erasure both use direct Clerk SDK calls — no new Go endpoint for either (05-CONTEXT.md D-08 + this plan's own decision on caregiver "Delete my data").
- The admin's own Family row never renders a remove `[✕]` action (05-CONTEXT.md D-11 — v1 has exactly one admin).
- Settings "Add child" repeats the full onboarding consent screen and inserts a fresh `consent_events` row before the new `children` row (05-CONTEXT.md D-09).
- The History heatmap grows incrementally from week 1 and caps at a fixed 12 weeks — no pre-rendered empty weeks, no scroll/pagination beyond the cap (05-CONTEXT.md D-07).
- Always use the `org:` prefix in Go role checks (`org:admin`, `org:caregiver`) — REQUIREMENTS.md REQ-016.
- Every Go handler explicitly checks `claims.ActiveOrganizationID` and returns 403 if empty or mismatched with the target row's `clerk_org_id` (REQ-027, REQ-015 IDOR check) — every new handler in this plan follows that pattern.
- **Test strategy is asymmetric by design, matching this repo's existing state:** the Go backend has a full `go test` suite (`backend/internal/api/*_test.go`) and every backend task in this plan is TDD (write failing test → implement → pass). The Android/Kotlin side (`shared`, `androidApp`) has **no unit test source set configured** — no JVM target for `shared`, no Robolectric, no androidTest — a state this plan does not change (out of scope: introducing a new test harness is a separate decision, not a byproduct of a feature plan). Android tasks therefore end with a `./gradlew :androidApp:assembleDebug` build-verification step plus a manual QA checklist, matching the precedent already set by `05-01-PLAN.md`/`05-02-PLAN.md`.

---

<a id="task-1"></a>
## Task 1: Backend — `GET`/`PUT /v1/notification-preferences`

**Files:**
- Modify: `api/openapi.yaml`
- Modify: `backend/internal/api/server.go`
- Modify: `backend/internal/api/generated.go` (regenerated via `make generate`, not hand-edited)
- Create: `backend/internal/api/notification_preferences_handler_test.go`
- Modify: `.planning/REQUIREMENTS.md:156-157`
- Modify: `.planning/ROADMAP.md:152,201`

**Interfaces:**
- Consumes: `backend/internal/api/problem.go` `WriteProblem(w, status, typ, title, detail)`; `backend/internal/api/auth_test.go` `withFakeClaims(r, userID, orgID, role) *http.Request` (test-only helper, already in package `api_test`).
- Produces: `Server.GetV1NotificationPreferences(w, r)` and `Server.PutV1NotificationPreferences(w, r)` methods on `*Server` (package `api`), routes `GET /v1/notification-preferences` and `PUT /v1/notification-preferences`. Response shape `{"child_id": "<uuid>", "enabled": <bool>}` (single object) for PUT, `[{"child_id": "...", "enabled": true}, ...]` (array) for GET — consumed by Task 4's `NotificationPreferencesApiClient`.

The `notification_preferences` table already exists (`backend/db/migrations/00002_schema.sql`, columns `id, clerk_user_id, child_id, enabled, created_at, updated_at`, `UNIQUE(clerk_user_id, child_id)`) — no new migration needed.

- [ ] **Step 1: Write the failing tests**

Create `backend/internal/api/notification_preferences_handler_test.go`:

```go
package api_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestGetV1NotificationPreferences_MissingAuthHeaders verifies GET /v1/notification-preferences
// without session claims returns 401.
func TestGetV1NotificationPreferences_MissingAuthHeaders(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/v1/notification-preferences", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
}

// TestGetV1NotificationPreferences_NoActiveOrg verifies 403 with claims but no active org (REQ-027).
func TestGetV1NotificationPreferences_NoActiveOrg(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/v1/notification-preferences", nil)
	req = withFakeClaims(req, "user_test", "", "org:caregiver")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Errorf("expected 403, got %d", rec.Code)
	}
}

// TestGetV1NotificationPreferences_CaregiverAllowed verifies no admin gate exists — a caregiver
// with an active org proceeds past auth to a DB call, which panics on the nil-DB test server.
// A recovered panic (rather than 401/403) is itself the assertion that no role check exists.
func TestGetV1NotificationPreferences_CaregiverAllowed(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/v1/notification-preferences", nil)
	req = withFakeClaims(req, "user_test", "org_test", "org:caregiver")
	rec := httptest.NewRecorder()

	defer func() {
		if recovered := recover(); recovered == nil {
			t.Fatal("expected a panic from the nil DB call past the auth gate (no role check), got none")
		}
	}()
	mux.ServeHTTP(rec, req)
}

// TestPutV1NotificationPreferences_MissingAuthHeaders verifies 401 without claims.
func TestPutV1NotificationPreferences_MissingAuthHeaders(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"child_id": "00000000-0000-0000-0000-000000000001", "enabled": true})
	req := httptest.NewRequest(http.MethodPut, "/v1/notification-preferences", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
}

// TestPutV1NotificationPreferences_MissingChildID verifies 400 when child_id is absent.
func TestPutV1NotificationPreferences_MissingChildID(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"enabled": true})
	req := httptest.NewRequest(http.MethodPut, "/v1/notification-preferences", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_test", "org_test", "org:caregiver")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", rec.Code)
	}
	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
}

// TestPutV1NotificationPreferences_MalformedBody verifies 400 on invalid JSON.
func TestPutV1NotificationPreferences_MalformedBody(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodPut, "/v1/notification-preferences", bytes.NewReader([]byte("{not valid")))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_test", "org_test", "org:caregiver")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", rec.Code)
	}
}

// TestPutV1NotificationPreferences_CaregiverAllowed verifies no admin gate exists for PUT either
// — valid body + caregiver role + nil DB panics past validation at the IDOR-check SELECT.
func TestPutV1NotificationPreferences_CaregiverAllowed(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"child_id": "00000000-0000-0000-0000-000000000001", "enabled": true})
	req := httptest.NewRequest(http.MethodPut, "/v1/notification-preferences", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_test", "org_test", "org:caregiver")
	rec := httptest.NewRecorder()

	defer func() {
		if recovered := recover(); recovered == nil {
			t.Fatal("expected a panic from the nil DB call past validation (no role check), got none")
		}
	}()
	mux.ServeHTTP(rec, req)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && go test ./internal/api/... -run NotificationPreferences -v`
Expected: every test FAILs — the routes don't exist yet, so `mux.ServeHTTP` returns 404 (not 401/403), and no panic occurs for the "CaregiverAllowed" tests.

- [ ] **Step 3: Add the endpoints to the OpenAPI spec**

In `api/openapi.yaml`, add a new path block (alphabetically after `/v1/children/{id}`, before `/v1/account` — exact position doesn't matter to oapi-codegen):

```yaml
  /v1/notification-preferences:
    get:
      operationId: getV1NotificationPreferences
      summary: List the caller's per-child notification preferences for the active org (REQ-022)
      responses:
        "200":
          description: Notification preferences for the caller's active-org children
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/NotificationPreferenceResponse'
        "401":
          description: Unauthorized
        "403":
          description: No active organization in session
    put:
      operationId: putV1NotificationPreferences
      summary: Upsert the caller's notification preference for one child (REQ-022)
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PutNotificationPreferenceRequest'
      responses:
        "200":
          description: Preference upserted
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/NotificationPreferenceResponse'
        "400":
          description: Invalid request
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        "401":
          description: Unauthorized
        "403":
          description: Forbidden
        "404":
          description: Child not found
```

And add these two schemas under `components/schemas` (alongside `ErasureConfirmation`):

```yaml
    NotificationPreferenceResponse:
      type: object
      properties:
        child_id:
          type: string
          format: uuid
        enabled:
          type: boolean
    PutNotificationPreferenceRequest:
      type: object
      required:
        - child_id
        - enabled
      properties:
        child_id:
          type: string
          format: uuid
        enabled:
          type: boolean
```

- [ ] **Step 4: Regenerate the server interface**

Run: `cd backend && make generate`
Expected: `backend/internal/api/generated.go` is rewritten to include `GetV1NotificationPreferences`/`PutV1NotificationPreferences` in `ServerInterface` and their route registrations. The package now fails to compile (`*Server` doesn't implement the new interface methods yet) — this is expected; Step 5 fixes it.

- [ ] **Step 5: Implement the handlers**

In `backend/internal/api/server.go`, add (near `GetV1Children`/`childListResponse`, reusing the existing `sql`, `clerk`, `json`, `http` imports already present in the file):

```go
// notificationPreferenceResponse mirrors the generated NotificationPreferenceResponse schema.
type notificationPreferenceResponse struct {
	ChildID string `json:"child_id"`
	Enabled bool   `json:"enabled"`
}

// GetV1NotificationPreferences implements GET /v1/notification-preferences — lists the caller's
// per-child notification preference for every child in their active org (REQ-022). A child with
// no row yet defaults to enabled=true (REQ-023 opt-in default) via COALESCE, without requiring a
// materialized row until the caregiver first toggles it.
//
// Auth paths return before touching s.DB, so a nil DB is safe for unit tests of the
// validation/auth code paths. No admin gate — any org member manages their own preferences.
func (s *Server) GetV1NotificationPreferences(w http.ResponseWriter, r *http.Request) {
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}
	clerkUserID := claims.Subject
	clerkOrgID := claims.ActiveOrganizationID

	rows, err := s.DB.QueryContext(r.Context(),
		`SELECT c.id, COALESCE(np.enabled, TRUE)
		 FROM children c
		 LEFT JOIN notification_preferences np ON np.child_id = c.id AND np.clerk_user_id = $1
		 WHERE c.clerk_org_id = $2
		 ORDER BY c.created_at ASC`,
		clerkUserID, clerkOrgID,
	)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not list notification preferences")
		return
	}
	defer rows.Close() //nolint:errcheck // safe no-op

	prefs := make([]notificationPreferenceResponse, 0)
	for rows.Next() {
		var p notificationPreferenceResponse
		if err := rows.Scan(&p.ChildID, &p.Enabled); err != nil {
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not read notification preferences")
			return
		}
		prefs = append(prefs, p)
	}
	if err := rows.Err(); err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not list notification preferences")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(prefs)
}

// putNotificationPreferenceRequest is the REQ-022 upsert payload.
type putNotificationPreferenceRequest struct {
	ChildID string `json:"child_id"`
	Enabled bool   `json:"enabled"`
}

// PutV1NotificationPreferences implements PUT /v1/notification-preferences — upserts the
// caller's own preference for one child via ON CONFLICT (clerk_user_id, child_id) DO UPDATE
// (REQ-022's exact required upsert shape). No admin gate — any org member manages their own
// preferences.
//
// Auth and validation paths return before touching s.DB, so a nil DB is safe for unit tests.
func (s *Server) PutV1NotificationPreferences(w http.ResponseWriter, r *http.Request) {
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}

	var req putNotificationPreferenceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "invalid or missing JSON body")
		return
	}
	if req.ChildID == "" {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "child_id must not be empty")
		return
	}

	clerkUserID := claims.Subject
	clerkOrgID := claims.ActiveOrganizationID

	// IDOR check (REQ-015): child must belong to the caller's active org.
	var childOrgID string
	err := s.DB.QueryRowContext(r.Context(), `SELECT clerk_org_id FROM children WHERE id = $1`, req.ChildID).Scan(&childOrgID)
	if err == sql.ErrNoRows {
		WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
		return
	}
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not look up child")
		return
	}
	if childOrgID != clerkOrgID {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "access denied")
		return
	}

	_, err = s.DB.ExecContext(r.Context(),
		`INSERT INTO notification_preferences (clerk_user_id, child_id, enabled)
		 VALUES ($1, $2, $3)
		 ON CONFLICT (clerk_user_id, child_id) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW()`,
		clerkUserID, req.ChildID, req.Enabled,
	)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not save notification preference")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(notificationPreferenceResponse{ChildID: req.ChildID, Enabled: req.Enabled})
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && go test ./internal/api/... -run NotificationPreferences -v`
Expected: PASS (all 7 tests).

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && go test ./...`
Expected: PASS — no regressions in existing handlers.

- [ ] **Step 8: Fix the stale requirement-phase tags**

`.planning/REQUIREMENTS.md` and `.planning/ROADMAP.md` still tag REQ-022/REQ-023 as "Phase 8" from before 05-CONTEXT.md D-03 decided Phase 5 builds the real table+endpoint (Phase 8 only adds FCM-sending on top). Fix the tracker to match reality.

In `.planning/REQUIREMENTS.md`, change lines 156-157 from:
```
| REQ-022 | Phase 8 | Pending |
| REQ-023 | Phase 8 | Pending |
```
to:
```
| REQ-022 | Phase 5 | Complete |
| REQ-023 | Phase 5 | Complete |
```

In `.planning/ROADMAP.md`, change line 152 from:
```
**Requirements**: REQ-003, REQ-006, REQ-007, REQ-030, REQ-031, REQ-032, REQ-033, REQ-035, REQ-036
```
to:
```
**Requirements**: REQ-003, REQ-006, REQ-007, REQ-022, REQ-023, REQ-030, REQ-031, REQ-032, REQ-033, REQ-035, REQ-036
```

And change line 201 from:
```
**Requirements**: REQ-020, REQ-021, REQ-022, REQ-023, REQ-024
```
to:
```
**Requirements**: REQ-020, REQ-021, REQ-024
```

- [ ] **Step 9: Commit**

```bash
git add api/openapi.yaml backend/internal/api/server.go backend/internal/api/generated.go backend/internal/api/notification_preferences_handler_test.go .planning/REQUIREMENTS.md .planning/ROADMAP.md
git commit -m "feat(05-02): add GET/PUT /v1/notification-preferences endpoint"
```

---

<a id="task-2"></a>
## Task 2: Backend — `PATCH /v1/children/{id}`

**Files:**
- Modify: `api/openapi.yaml`
- Modify: `backend/internal/api/server.go`
- Modify: `backend/internal/api/generated.go` (regenerated via `make generate`)
- Create: `backend/internal/api/patch_children_handler_test.go`

**Interfaces:**
- Consumes: `openapi_types.UUID` path-param type (already imported in `server.go` for `DeleteV1ChildrenId`); the existing `childListResponse` struct (from Task-adjacent `GetV1Children`, already in `server.go`) — reused as the PATCH response shape, no new response type needed.
- Produces: `Server.PatchV1ChildrenId(w, r, id openapi_types.UUID)` on `*Server`, route `PATCH /v1/children/{id}` — consumed by Task 4's `ChildrenApiClient.patchChild`.

- [ ] **Step 1: Write the failing tests**

Create `backend/internal/api/patch_children_handler_test.go`:

```go
package api_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestPatchV1ChildrenId_MissingAuthHeaders verifies 401 without session claims.
func TestPatchV1ChildrenId_MissingAuthHeaders(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"nickname": "NewName"})
	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
}

// TestPatchV1ChildrenId_NoActiveOrg verifies 403 with claims but no active org (REQ-027).
func TestPatchV1ChildrenId_NoActiveOrg(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"nickname": "NewName"})
	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_123", "", "org:admin")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Errorf("expected 403, got %d", rec.Code)
	}
}

// TestPatchV1ChildrenId_CaregiverRole_Returns403 verifies admin-only write gate (REQ-016).
func TestPatchV1ChildrenId_CaregiverRole_Returns403(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"nickname": "NewName"})
	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_123", "org_abc", "org:caregiver")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Errorf("expected 403, got %d", rec.Code)
	}
}

// TestPatchV1ChildrenId_MalformedBody_Returns400 verifies 400 on invalid JSON (admin role, past auth).
func TestPatchV1ChildrenId_MalformedBody_Returns400(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader([]byte("{not valid")))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_123", "org_abc", "org:admin")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", rec.Code)
	}
}

// TestPatchV1ChildrenId_InvalidBirthMonth_Returns400 verifies range validation only fires when
// the field is explicitly provided (partial-update semantics).
func TestPatchV1ChildrenId_InvalidBirthMonth_Returns400(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"birth_month": 13})
	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_123", "org_abc", "org:admin")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", rec.Code)
	}
	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
}

// TestPatchV1ChildrenId_EmptyNickname_Returns400 verifies an explicitly empty nickname is
// rejected (distinct from an absent nickname, which is a valid no-op for that field).
func TestPatchV1ChildrenId_EmptyNickname_Returns400(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"nickname": ""})
	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_123", "org_abc", "org:admin")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", rec.Code)
	}
}

// TestPatchV1ChildrenId_AdminAllowed_PanicsPastValidation verifies a valid partial body with an
// admin role proceeds past auth+validation to the nil-DB IDOR-check SELECT, which panics. A
// recovered panic (rather than 400/403) is the assertion that validation accepted the payload.
func TestPatchV1ChildrenId_AdminAllowed_PanicsPastValidation(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body, _ := json.Marshal(map[string]any{"nickname": "NewName"})
	req := httptest.NewRequest(http.MethodPatch, "/v1/children/00000000-0000-0000-0000-000000000001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req = withFakeClaims(req, "user_123", "org_abc", "org:admin")
	rec := httptest.NewRecorder()

	defer func() {
		if recovered := recover(); recovered == nil {
			t.Fatal("expected a panic from the nil DB call past validation, got none")
		}
	}()
	mux.ServeHTTP(rec, req)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && go test ./internal/api/... -run PatchV1ChildrenId -v`
Expected: every test FAILs — route doesn't exist yet (404s instead of the expected codes/panics).

- [ ] **Step 3: Add the endpoint to the OpenAPI spec**

In `api/openapi.yaml`, add a `patch:` block to the existing `/v1/children/{id}` path (alongside the existing `delete:` block):

```yaml
  /v1/children/{id}:
    patch:
      operationId: patchV1ChildrenId
      summary: Partially update a child's nickname or birth month/year (admin only)
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PatchChildRequest'
      responses:
        "200":
          description: Child profile updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ChildResponse'
        "400":
          description: Invalid request
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        "401":
          description: Unauthorized
        "403":
          description: Forbidden
        "404":
          description: Child not found
    delete:
      # ... existing delete block stays exactly as-is, unchanged ...
```

And add this schema under `components/schemas`:

```yaml
    PatchChildRequest:
      type: object
      properties:
        nickname:
          type: string
        birth_month:
          type: integer
        birth_year:
          type: integer
```

- [ ] **Step 4: Regenerate the server interface**

Run: `cd backend && make generate`
Expected: `generated.go` now requires `PatchV1ChildrenId` on `ServerInterface`; the package fails to compile until Step 5.

- [ ] **Step 5: Implement the handler**

In `backend/internal/api/server.go`, add (near `DeleteV1ChildrenId`):

```go
// patchChildRequest carries the partial-update payload. Unlike createChildRequest's non-pointer
// fields (all required on create), pointer fields distinguish "not provided" (nil, leave column
// unchanged) from an explicit value to validate and write.
type patchChildRequest struct {
	Nickname   *string `json:"nickname"`
	BirthMonth *int    `json:"birth_month"`
	BirthYear  *int    `json:"birth_year"`
}

// PatchV1ChildrenId implements PATCH /v1/children/{id} — partial update of nickname and/or
// birth month/year (Settings "Edit child"). Admin-only, same auth shape as DeleteV1ChildrenId
// (REQ-016, REQ-027, REQ-015 IDOR check).
//
// Auth paths return before touching s.DB, so a nil DB is safe for unit tests of the
// validation/auth code paths.
func (s *Server) PatchV1ChildrenId(w http.ResponseWriter, r *http.Request, id openapi_types.UUID) {
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}
	if !claims.HasRole("org:admin") {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "admin role required")
		return
	}

	var req patchChildRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "invalid or missing JSON body")
		return
	}

	if req.Nickname != nil {
		switch {
		case *req.Nickname == "":
			WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "nickname must not be empty")
			return
		case len(*req.Nickname) > 100:
			WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "nickname must not exceed 100 characters")
			return
		}
	}
	if req.BirthMonth != nil && (*req.BirthMonth < 1 || *req.BirthMonth > 12) {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "birth_month must be between 1 and 12")
		return
	}
	if req.BirthYear != nil && (*req.BirthYear < 2000 || *req.BirthYear > time.Now().Year()+1) {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "birth_year is out of valid range")
		return
	}

	childID := id.String()

	// IDOR check (REQ-015): child must belong to the caller's active org.
	var childOrgID string
	err := s.DB.QueryRowContext(r.Context(), `SELECT clerk_org_id FROM children WHERE id = $1`, childID).Scan(&childOrgID)
	if err == sql.ErrNoRows {
		WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
		return
	}
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not look up child")
		return
	}
	if claims.ActiveOrganizationID != childOrgID {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "access denied")
		return
	}

	var resp childListResponse
	err = s.DB.QueryRowContext(r.Context(),
		`UPDATE children
		 SET nickname = COALESCE($1, nickname),
		     birth_month = COALESCE($2, birth_month),
		     birth_year = COALESCE($3, birth_year),
		     updated_at = NOW()
		 WHERE id = $4
		 RETURNING id, clerk_org_id, nickname, birth_month, birth_year`,
		req.Nickname, req.BirthMonth, req.BirthYear, childID,
	).Scan(&resp.ID, &resp.ClerkOrgID, &resp.Nickname, &resp.BirthMonth, &resp.BirthYear)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not update child profile")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(resp)
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && go test ./internal/api/... -run PatchV1ChildrenId -v`
Expected: PASS (all 7 tests).

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && go test ./...`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add api/openapi.yaml backend/internal/api/server.go backend/internal/api/generated.go backend/internal/api/patch_children_handler_test.go
git commit -m "feat(05-02): add PATCH /v1/children/{id} endpoint"
```

---

<a id="task-3"></a>
## Task 3: Shared — notification-preferences local cache + child-update/cleanup queries

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/onesteptwo/db/NotificationPreferences.sq`
- Create: `shared/src/commonMain/kotlin/com/onesteptwo/data/NotificationPreferencesRepository.kt`
- Modify: `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq`
- Modify: `shared/src/commonMain/kotlin/com/onesteptwo/data/ChildrenRepository.kt`
- Modify: `shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq`
- Modify: `shared/src/commonMain/kotlin/com/onesteptwo/data/PottyEventsRepository.kt`

**Interfaces:**
- Consumes: existing `OneStepTwoDatabase` (SQLDelight-generated), existing `Dispatchers`/`Flow` conventions from Stage 1's `PottyEventsRepository`/`ChildrenRepository`.
- Produces: `NotificationPreferencesRepository(db).observe(childId): Flow<Boolean>`, `.setLocal(childId, enabled): suspend`, `.deleteLocal(childId): suspend`; `ChildrenRepository.update(id, nickname, birthMonth, birthYear, updatedAt): suspend`; `PottyEventsRepository.observeDailyCounts(childId, sinceInclusive): Flow<List<SelectDailyCounts>>`, `.observeEarliestOccurredAt(childId): Flow<String?>`, `.deleteAllForChild(childId): suspend` — all consumed by later tasks (5, 6, 8, 9).

- [ ] **Step 1: Add the local notification-preferences table**

Create `shared/src/commonMain/sqldelight/com/onesteptwo/db/NotificationPreferences.sq`:

```sql
-- Phase 5 Stage 2: local write-through cache of the caller's own per-child notification
-- preference (mirrors the Go notification_preferences table's (clerk_user_id, child_id, enabled)
-- shape for the caller only — REQ-022). Not a sync queue: Settings actions hit the real Go
-- endpoint directly (05-CONTEXT.md D-02); this table only makes the last-known/default state
-- available instantly and offline.
CREATE TABLE notification_preferences (
    child_id TEXT NOT NULL PRIMARY KEY,
    enabled INTEGER NOT NULL DEFAULT 1
);

upsert:
INSERT INTO notification_preferences (child_id, enabled)
VALUES (?, ?)
ON CONFLICT(child_id) DO UPDATE SET enabled = excluded.enabled;

selectByChildId:
SELECT * FROM notification_preferences WHERE child_id = ?;

deleteByChildId:
DELETE FROM notification_preferences WHERE child_id = ?;
```

- [ ] **Step 2: Add the repository**

Create `shared/src/commonMain/kotlin/com/onesteptwo/data/NotificationPreferencesRepository.kt`:

```kotlin
package com.onesteptwo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import com.onesteptwo.db.Notification_preferences
import com.onesteptwo.db.OneStepTwoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotificationPreferencesRepository(private val db: OneStepTwoDatabase) {

    /** REQ-023 opt-in default: a child with no local row yet reads as enabled. */
    fun observe(childId: String): Flow<Boolean> =
        db.notificationPreferencesQueries.selectByChildId(childId)
            .asFlow()
            .mapToOneOrDefault(Notification_preferences(childId, 1L), Dispatchers.IO)
            .map { it.enabled == 1L }

    suspend fun setLocal(childId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.notificationPreferencesQueries.upsert(childId, if (enabled) 1L else 0L)
    }

    suspend fun deleteLocal(childId: String) = withContext(Dispatchers.IO) {
        db.notificationPreferencesQueries.deleteByChildId(childId)
    }
}
```

- [ ] **Step 3: Add an `update` query for Settings "Edit child"**

In `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq`, append after the existing `deleteById` query:

```sql
update:
UPDATE children
SET nickname = ?, birth_month = ?, birth_year = ?, updated_at = ?
WHERE id = ?;
```

In `shared/src/commonMain/kotlin/com/onesteptwo/data/ChildrenRepository.kt`, add this method inside the `ChildrenRepository` class (after `insert`, before `deleteById`):

```kotlin
    suspend fun update(
        id: String,
        nickname: String,
        birthMonth: Long,
        birthYear: Long,
        updatedAt: String
    ) = withContext(Dispatchers.IO) {
        db.childrenQueries.update(
            nickname = nickname,
            birth_month = birthMonth,
            birth_year = birthYear,
            updated_at = updatedAt,
            id = id
        )
    }
```

- [ ] **Step 4: Add heatmap-support and child-removal-cleanup queries to `PottyEvents.sq`**

In `shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq`, append after `selectDailyCounts`:

```sql
selectEarliestOccurredAt:
SELECT MIN(occurred_at) AS earliest FROM potty_events
WHERE child_id = ? AND deleted_at IS NULL;

deleteAllByChildId:
DELETE FROM potty_events WHERE child_id = ?;
```

- [ ] **Step 5: Add matching repository methods**

In `shared/src/commonMain/kotlin/com/onesteptwo/data/PottyEventsRepository.kt`, add the import `app.cash.sqldelight.coroutines.mapToOne` (alongside the existing `mapToList`/`mapToOne`import already present) and `kotlinx.coroutines.flow.map`, then append these methods inside the `PottyEventsRepository` class (after `getDailyCounts`):

```kotlin
    /** Reactive daily counts for the History heatmap (REQ-033) — updates live as events are logged/deleted. */
    fun observeDailyCounts(childId: String, sinceInclusive: String): Flow<List<SelectDailyCounts>> =
        db.pottyEventsQueries.selectDailyCounts(childId, sinceInclusive).asFlow().mapToList(Dispatchers.IO)

    /** Earliest ever-logged event for this child (excluding soft-deleted) — anchors the heatmap's
     * incremental-growth window (05-CONTEXT.md D-07) and doubles as "has this child ever logged
     * anything" for the History empty state. MIN() over zero rows still returns one row with a
     * null column, so this is always exactly one row — mapToOne, then unwrap the nullable field. */
    fun observeEarliestOccurredAt(childId: String): Flow<String?> =
        db.pottyEventsQueries.selectEarliestOccurredAt(childId)
            .asFlow().mapToOne(Dispatchers.IO).map { it.earliest }

    /** Settings "Remove child" local cleanup — potty_events has no SQLite FK cascade. */
    suspend fun deleteAllForChild(childId: String) = withContext(Dispatchers.IO) {
        db.pottyEventsQueries.deleteAllByChildId(childId)
    }
```

- [ ] **Step 6: Build to verify SQLDelight codegen and Kotlin compile**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL. This regenerates `Notification_preferences`, `SelectEarliestOccurredAt`, and the new query methods used above.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/onesteptwo/db/NotificationPreferences.sq \
        shared/src/commonMain/kotlin/com/onesteptwo/data/NotificationPreferencesRepository.kt \
        shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq \
        shared/src/commonMain/kotlin/com/onesteptwo/data/ChildrenRepository.kt \
        shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq \
        shared/src/commonMain/kotlin/com/onesteptwo/data/PottyEventsRepository.kt
git commit -m "feat(05-02): add notification-preferences local cache and child-update/cleanup queries"
```

---

<a id="task-4"></a>
## Task 4: Shared — API client extensions

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/onesteptwo/api/ChildrenApiClient.kt`
- Create: `shared/src/commonMain/kotlin/com/onesteptwo/api/NotificationPreferencesApiClient.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/AppContainer.kt`

**Interfaces:**
- Consumes: Task 1's `PUT /v1/notification-preferences` response shape `{"child_id", "enabled"}`; Task 2's `PATCH /v1/children/{id}` response shape (same as `ChildResponse`); existing `buildHttpClient` from `com.onesteptwo.auth.HttpClientFactory`; existing `ApiResult<T>` sealed interface (already defined in `ChildrenApiClient.kt`).
- Produces: `ChildrenApiClient.patchChild(id, nickname, birthMonth, birthYear): ApiResult<ChildResponse>`, `.deleteChild(id): ApiResult<Unit>`, `.deleteAccount(): ApiResult<Unit>`; `NotificationPreferencesApiClient.getPreferences(): ApiResult<List<NotificationPreferenceResponse>>`, `.putPreference(childId, enabled): ApiResult<NotificationPreferenceResponse>`; `AppContainer.notificationPreferencesRepository`, `.notificationPreferencesApiClient` — all consumed by Tasks 8 and 9.

- [ ] **Step 1: Extend `ChildrenApiClient`**

Replace the full contents of `shared/src/commonMain/kotlin/com/onesteptwo/api/ChildrenApiClient.kt`:

```kotlin
package com.onesteptwo.api

import com.onesteptwo.auth.AuthRepository
import com.onesteptwo.auth.buildHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class CreateChildConsent(val app_version: String, val consent_text_version: String)

@Serializable
data class CreateChildRequest(
    val nickname: String,
    val birth_month: Int,
    val birth_year: Int,
    val consent: CreateChildConsent
)

@Serializable
data class PatchChildRequest(
    val nickname: String? = null,
    val birth_month: Int? = null,
    val birth_year: Int? = null
)

@Serializable
data class ChildResponse(
    val id: String,
    val clerk_org_id: String,
    val nickname: String,
    val birth_month: Int,
    val birth_year: Int
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String) : ApiResult<Nothing>
}

/**
 * Typed wrapper around `/v1/children` and `/v1/account` (`backend/internal/api/server.go`).
 * `createChild`/`getChildren` shipped in 05-01-PLAN.md (Stage 1); `patchChild`/`deleteChild`/
 * `deleteAccount` are 05-02-PLAN.md Stage 2 additions.
 */
class ChildrenApiClient(private val httpClient: HttpClient, private val baseUrl: String) {
    suspend fun createChild(
        nickname: String,
        birthMonth: Int,
        birthYear: Int,
        appVersion: String,
        consentTextVersion: String
    ): ApiResult<ChildResponse> {
        return try {
            val response = httpClient.post("$baseUrl/v1/children") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateChildRequest(
                        nickname = nickname,
                        birth_month = birthMonth,
                        birth_year = birthYear,
                        consent = CreateChildConsent(appVersion, consentTextVersion)
                    )
                )
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't save your child's profile. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /**
     * Lists the caller's active-org children — used once on the post-auth routing path so a
     * returning/invited caregiver with an empty local database skips the onboarding wizard.
     */
    suspend fun getChildren(): ApiResult<List<ChildResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/v1/children")
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't load your family. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /** Settings "Edit child" — partial update; only non-null params are changed server-side. */
    suspend fun patchChild(
        id: String,
        nickname: String?,
        birthMonth: Int?,
        birthYear: Int?
    ): ApiResult<ChildResponse> {
        return try {
            val response = httpClient.patch("$baseUrl/v1/children/$id") {
                contentType(ContentType.Application.Json)
                setBody(PatchChildRequest(nickname, birthMonth, birthYear))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't update your child's profile. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /** Settings "Remove child" — REQ-011 erasure cascade. */
    suspend fun deleteChild(id: String): ApiResult<Unit> {
        return try {
            val response = httpClient.delete("$baseUrl/v1/children/$id")
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("Couldn't remove child. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    /** Settings admin "Delete my data" — REQ-012 full family erasure cascade. */
    suspend fun deleteAccount(): ApiResult<Unit> {
        return try {
            val response = httpClient.delete("$baseUrl/v1/account")
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("Couldn't delete your data. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }
}

fun createChildrenApiClient(authRepository: AuthRepository, baseUrl: String, isDebug: Boolean): ChildrenApiClient =
    ChildrenApiClient(buildHttpClient(authRepository, baseUrl, isDebug), baseUrl)
```

- [ ] **Step 2: Create `NotificationPreferencesApiClient`**

Create `shared/src/commonMain/kotlin/com/onesteptwo/api/NotificationPreferencesApiClient.kt`:

```kotlin
package com.onesteptwo.api

import com.onesteptwo.auth.AuthRepository
import com.onesteptwo.auth.buildHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class NotificationPreferenceResponse(val child_id: String, val enabled: Boolean)

@Serializable
data class PutNotificationPreferenceRequest(val child_id: String, val enabled: Boolean)

/** Typed wrapper around `/v1/notification-preferences` (REQ-022, `backend/internal/api/server.go`). */
class NotificationPreferencesApiClient(private val httpClient: HttpClient, private val baseUrl: String) {
    suspend fun getPreferences(): ApiResult<List<NotificationPreferenceResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/v1/notification-preferences")
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't load data. Pull down to refresh.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }

    suspend fun putPreference(childId: String, enabled: Boolean): ApiResult<NotificationPreferenceResponse> {
        return try {
            val response = httpClient.put("$baseUrl/v1/notification-preferences") {
                contentType(ContentType.Application.Json)
                setBody(PutNotificationPreferenceRequest(childId, enabled))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Failure("Couldn't save notification preference. Try again.")
            }
        } catch (e: Exception) {
            ApiResult.Failure("Couldn't connect. Check your internet connection and try again.")
        }
    }
}

fun createNotificationPreferencesApiClient(
    authRepository: AuthRepository,
    baseUrl: String,
    isDebug: Boolean
): NotificationPreferencesApiClient =
    NotificationPreferencesApiClient(buildHttpClient(authRepository, baseUrl, isDebug), baseUrl)
```

- [ ] **Step 3: Wire both into `AppContainer`**

Replace the full contents of `androidApp/src/main/kotlin/com/onesteptwo/android/AppContainer.kt`:

```kotlin
package com.onesteptwo.android

import android.content.Context
import com.onesteptwo.android.BuildConfig
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.api.NotificationPreferencesApiClient
import com.onesteptwo.api.createChildrenApiClient
import com.onesteptwo.api.createNotificationPreferencesApiClient
import com.onesteptwo.auth.ClerkAuthRepository
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.ConsentEventsRepository
import com.onesteptwo.data.NotificationPreferencesRepository
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.DatabaseDriverFactory
import com.onesteptwo.db.OneStepTwoDatabase

/**
 * Hand-rolled composition root (no DI framework) — built once per process and held by
 * [ClerkApp]. Screens reach repositories via `(application as ClerkApp).container`.
 */
class AppContainer(context: Context) {
    private val database: OneStepTwoDatabase by lazy {
        OneStepTwoDatabase(DatabaseDriverFactory(context).createDriver())
    }

    val childrenRepository: ChildrenRepository by lazy { ChildrenRepository(database) }
    val pottyEventsRepository: PottyEventsRepository by lazy { PottyEventsRepository(database) }
    val consentEventsRepository: ConsentEventsRepository by lazy { ConsentEventsRepository(database) }
    val notificationPreferencesRepository: NotificationPreferencesRepository by lazy {
        NotificationPreferencesRepository(database)
    }

    val childrenApiClient: ChildrenApiClient by lazy {
        createChildrenApiClient(
            authRepository = ClerkAuthRepository(),
            baseUrl = BuildConfig.API_BASE_URL,
            isDebug = BuildConfig.DEBUG
        )
    }
    val notificationPreferencesApiClient: NotificationPreferencesApiClient by lazy {
        createNotificationPreferencesApiClient(
            authRepository = ClerkAuthRepository(),
            baseUrl = BuildConfig.API_BASE_URL,
            isDebug = BuildConfig.DEBUG
        )
    }
}
```

- [ ] **Step 4: Build to verify compile**

Run: `./gradlew :shared:assembleDebug :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/onesteptwo/api/ChildrenApiClient.kt \
        shared/src/commonMain/kotlin/com/onesteptwo/api/NotificationPreferencesApiClient.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/AppContainer.kt
git commit -m "feat(05-02): extend ChildrenApiClient and add NotificationPreferencesApiClient"
```

---

<a id="task-5"></a>
## Task 5: Android — History tab heatmap

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HeatmapView.kt`
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt`

**Interfaces:**
- Consumes: Task 3's `PottyEventsRepository.observeDailyCounts`/`.observeEarliestOccurredAt`; existing `AppContainer`, `ChildSelectionViewModel` (Stage 1); existing heatmap color constants `HeatmapLowLight/Dark`, `HeatmapMediumLight/Dark`, `HeatmapHighLight/Dark` and `Radius` (already defined in `ui/theme/Color.kt` and `ui/theme/Shape.kt` from Stage 1's theme task, anticipating this use).
- Produces: `HeatmapView(weeks: List<HeatmapWeekRow>, onDayClick: (LocalDate) -> Unit)`, `HeatmapDay`/`HeatmapWeekRow` data classes — consumed by Task 6's `DayDetailScreen` navigation target. `HistoryScreen(container, childSelectionViewModel, onDayClick)` — consumed by `MainTabNavigation`.

- [ ] **Step 1: Build the heatmap cell/grid composable**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HeatmapView.kt`:

```kotlin
package com.onesteptwo.android.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onesteptwo.android.ui.theme.HeatmapHighDark
import com.onesteptwo.android.ui.theme.HeatmapHighLight
import com.onesteptwo.android.ui.theme.HeatmapLowDark
import com.onesteptwo.android.ui.theme.HeatmapLowLight
import com.onesteptwo.android.ui.theme.HeatmapMediumDark
import com.onesteptwo.android.ui.theme.HeatmapMediumLight
import com.onesteptwo.android.ui.theme.Radius
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HeatmapDay(val date: LocalDate, val count: Int)
data class HeatmapWeekRow(val monthLabel: String?, val days: List<HeatmapDay>)

private val WeekdayHeaders = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
private val DateDescriptionFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/** GitHub-contribution-graph-style rolling heatmap (04-UI-SPEC.md Component 4). */
@Composable
fun HeatmapView(weeks: List<HeatmapWeekRow>, onDayClick: (LocalDate) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Row(modifier = Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WeekdayHeaders.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        weeks.forEach { week ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(28.dp)) {
                    week.monthLabel?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.days.forEach { day ->
                        HeatmapCell(day = day, isDark = isDark, emptyColor = emptyColor, onClick = onDayClick)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        HeatmapLegend(isDark = isDark, emptyColor = emptyColor)
    }
}

@Composable
private fun HeatmapCell(day: HeatmapDay, isDark: Boolean, emptyColor: Color, onClick: (LocalDate) -> Unit) {
    val color = intensityColor(day.count, isDark, emptyColor)
    val dateLabel = day.date.format(DateDescriptionFormatter)
    val description = if (day.count > 0) "${day.count} events on $dateLabel" else "$dateLabel, no events"
    Surface(
        color = color,
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier
            .size(32.dp)
            .semantics {
                contentDescription = description
                if (day.count > 0) role = Role.Button
            }
            .let { m -> if (day.count > 0) m.clickable { onClick(day.date) } else m }
    ) {}
}

private fun intensityColor(count: Int, isDark: Boolean, emptyColor: Color): Color = when {
    count == 0 -> emptyColor
    count <= 2 -> if (isDark) HeatmapLowDark else HeatmapLowLight
    count <= 5 -> if (isDark) HeatmapMediumDark else HeatmapMediumLight
    else -> if (isDark) HeatmapHighDark else HeatmapHighLight
}

@Composable
private fun HeatmapLegend(isDark: Boolean, emptyColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "less", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        listOf(0, 1, 3, 6).forEach { sample ->
            Surface(
                color = intensityColor(sample, isDark, emptyColor),
                shape = RoundedCornerShape(Radius.sm),
                modifier = Modifier.size(16.dp)
            ) {}
        }
        Text(text = "more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
```

- [ ] **Step 2: Build the view model — rolling window that grows from week 1, caps at 12**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryViewModel.kt`:

```kotlin
package com.onesteptwo.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Children
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val MAX_WEEKS = 12

data class HistoryUiState(
    val hasEverLogged: Boolean = false,
    val weeks: List<HeatmapWeekRow> = emptyList()
)

/** History tab (REQ-033): rolling heatmap that grows from week 1 and caps at 12 weeks (05-CONTEXT.md D-07). */
class HistoryViewModel(
    private val pottyEventsRepository: PottyEventsRepository,
    activeChildFlow: StateFlow<Children?>
) : ViewModel() {

    private val windowStart = mondayOfWeeksAgo(MAX_WEEKS - 1)
    private val windowStartIso = windowStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val earliestFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(null) else pottyEventsRepository.observeEarliestOccurredAt(child.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val countsFlow = activeChildFlow.flatMapLatest { child ->
        if (child == null) flowOf(emptyList()) else pottyEventsRepository.observeDailyCounts(child.id, windowStartIso)
    }

    val state: StateFlow<HistoryUiState> = combine(earliestFlow, countsFlow) { earliest, counts ->
        if (earliest == null) {
            HistoryUiState(hasEverLogged = false, weeks = emptyList())
        } else {
            val countsByDate = counts.associate { LocalDate.parse(it.day) to it.eventCount.toInt() }
            val earliestDate = Instant.parse(earliest).atZone(ZoneId.systemDefault()).toLocalDate()
            val earliestMonday = earliestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val thisMonday = mondayOfWeeksAgo(0)
            val weeksElapsed = ChronoUnit.WEEKS.between(earliestMonday, thisMonday).toInt() + 1
            val weeksToShow = weeksElapsed.coerceIn(1, MAX_WEEKS)
            HistoryUiState(hasEverLogged = true, weeks = buildWeeks(weeksToShow, countsByDate))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    private fun buildWeeks(weeksToShow: Int, countsByDate: Map<LocalDate, Int>): List<HeatmapWeekRow> {
        val thisMonday = mondayOfWeeksAgo(0)
        val startMonday = thisMonday.minusWeeks((weeksToShow - 1).toLong())
        var lastMonth = -1
        return (0 until weeksToShow).map { w ->
            val weekMonday = startMonday.plusWeeks(w.toLong())
            val days = (0..6).map { d ->
                val date = weekMonday.plusDays(d.toLong())
                HeatmapDay(date, countsByDate[date] ?: 0)
            }
            val label = if (weekMonday.monthValue != lastMonth) {
                lastMonth = weekMonday.monthValue
                weekMonday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } else null
            HeatmapWeekRow(monthLabel = label, days = days)
        }
    }
}

private fun mondayOfWeeksAgo(weeksAgo: Int): LocalDate =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo.toLong())

class HistoryViewModelFactory(
    private val pottyEventsRepository: PottyEventsRepository,
    private val activeChildFlow: StateFlow<Children?>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(pottyEventsRepository, activeChildFlow) as T
}
```

- [ ] **Step 3: Replace the History tab stub**

Replace the full contents of `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import java.time.LocalDate

/** History tab (04-UI-SPEC.md §Main App — History Tab): rolling heatmap, or empty state if the
 * active child has never logged an event. */
@Composable
fun HistoryScreen(
    container: AppContainer,
    childSelectionViewModel: ChildSelectionViewModel,
    onDayClick: (LocalDate) -> Unit
) {
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(container.pottyEventsRepository, childSelectionViewModel.activeChild)
    )
    val state by historyViewModel.state.collectAsState()

    if (!state.hasEverLogged) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "No events yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log your first potty trip to see it here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeatmapView(weeks = state.weeks, onDayClick = onDayClick)
        }
    }
}
```

- [ ] **Step 4: Wire the History tab's real params into the nav host**

In `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt`, add the import `import java.time.LocalDate` and replace:

```kotlin
            composable("history") { HistoryScreen() }
```

with:

```kotlin
            composable("history") {
                HistoryScreen(
                    container = container,
                    childSelectionViewModel = childSelectionViewModel,
                    onDayClick = { date -> navController.navigate("history/day/$date") }
                )
            }
```

(`LocalDate.toString()` produces ISO-8601 `yyyy-MM-dd`, parsed back in Task 6.)

- [ ] **Step 5: Build and manually verify**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual QA (emulator/device): sign in as a family with zero logged events → History tab shows "No events yet" empty state. Log one event via Home → switch to History → heatmap renders with exactly one week (this week) and one filled cell (today, low intensity). Log 6+ events on today's cell → intensity increases to "high" (darkest purple). Confirm the legend "less [swatch][swatch][swatch][swatch] more" renders below the grid.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HeatmapView.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryViewModel.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt
git commit -m "feat(05-02): build History tab rolling heatmap"
```

---

<a id="task-6"></a>
## Task 6: Android — History Day-Detail + event delete

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/DayDetailScreen.kt`
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/DayDetailViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/EventDetailSheet.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt`

**Interfaces:**
- Consumes: Task 5's `onDayClick` navigation target `history/day/{date}`; existing `PottyEventsRepository.observeByChildAndDayRange`/`.updateDetails`/`.deleteEvent` (Stage 1); existing `EventDetailSheet` (Stage 1, extended here); existing `DestructiveConfirmDialog`.
- Produces: `DayDetailScreen(date, childId, container, onBack)`, wired at route `history/day/{date}` with the tab bar hidden (05-CONTEXT.md D-06: every card tappable, delete lives inside the reopened sheet).

- [ ] **Step 1: Extend `EventDetailSheet` with an optional "Delete event" action**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/EventDetailSheet.kt`, change the function signature and add the delete button. Replace:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: Potty_events,
    onSave: (eventType: String?, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
```

with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: Potty_events,
    onSave: (eventType: String?, notes: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
```

Then, immediately after the existing "Save details" `Button` block (before the trailing `Spacer(modifier = Modifier.height(16.dp))`), add:

```kotlin
            if (onDelete != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete event", color = MaterialTheme.colorScheme.error)
                }
            }
```

Add the import `androidx.compose.material3.TextButton` to the file's import block. `onDelete` defaults to `null` so the existing Home-tab call site (`HomeScreen.kt`) compiles unchanged and shows no delete affordance there — Day-Detail (this task) is the only caller that passes a real one.

- [ ] **Step 2: Build the Day-Detail view model**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/DayDetailViewModel.kt`:

```kotlin
package com.onesteptwo.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Potty_events
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayDetailViewModel(
    private val pottyEventsRepository: PottyEventsRepository,
    date: LocalDate,
    childId: String
) : ViewModel() {

    private val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
    private val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    val events: StateFlow<List<Potty_events>> =
        pottyEventsRepository.observeByChildAndDayRange(childId, dayStart, dayEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedEvent = MutableStateFlow<Potty_events?>(null)
    val selectedEvent: StateFlow<Potty_events?> = _selectedEvent.asStateFlow()

    fun openEventDetail(event: Potty_events) {
        _selectedEvent.value = event
    }

    fun dismissEventDetail() {
        _selectedEvent.value = null
    }

    /** Event Detail sheet "Save details" — preserves the original occurred_at unless the caller passes an edited one. */
    fun saveEventDetails(eventType: String?, notes: String?, occurredAt: String) {
        val event = _selectedEvent.value ?: return
        viewModelScope.launch {
            pottyEventsRepository.updateDetails(
                id = event.id,
                eventType = eventType,
                notes = notes,
                occurredAt = occurredAt,
                updatedBy = Clerk.user?.id,
                now = Instant.now().toString()
            )
            _selectedEvent.value = null
        }
    }

    /** "Delete event" (05-CONTEXT.md D-06) — soft delete (REQ-007); excluded from the day list immediately via the reactive Flow. */
    fun deleteEvent(id: String) {
        viewModelScope.launch {
            pottyEventsRepository.deleteEvent(id, Clerk.user?.id, Instant.now().toString())
        }
    }
}

class DayDetailViewModelFactory(
    private val pottyEventsRepository: PottyEventsRepository,
    private val date: LocalDate,
    private val childId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DayDetailViewModel(pottyEventsRepository, date, childId) as T
}
```

- [ ] **Step 3: Build the Day-Detail screen**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/DayDetailScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.common.DestructiveConfirmDialog
import com.onesteptwo.android.ui.home.EventDetailSheet
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.db.Potty_events
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** History Day-Detail (04-UI-SPEC.md §Main App — History Day-Detail View): every card tappable
 * (05-CONTEXT.md D-06), tab bar hidden by the caller (MainTabNavigation). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: LocalDate,
    childId: String,
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: DayDetailViewModel = viewModel(
        factory = DayDetailViewModelFactory(container.pottyEventsRepository, date, childId)
    )
    val events by viewModel.events.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()
    var pendingDelete by remember { mutableStateOf<Potty_events?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Go back to History" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(event = event, onClick = { viewModel.openEventDetail(event) })
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            onSave = { type, notes -> viewModel.saveEventDetails(type, notes, event.occurred_at) },
            onDelete = {
                pendingDelete = event
                viewModel.dismissEventDetail()
            },
            onDismiss = viewModel::dismissEventDetail
        )
    }

    pendingDelete?.let { event ->
        DestructiveConfirmDialog(
            title = "Remove event?",
            body = "This permanently deletes this event. This cannot be undone.",
            confirmLabel = "Remove event",
            dismissLabel = "Keep event",
            onConfirm = {
                viewModel.deleteEvent(event.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun EventCard(event: Potty_events, onClick: () -> Unit) {
    val time = Instant.parse(event.occurred_at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val type = event.event_type
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        tonalElevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (type == null) "$time event — tap to add details" else "$time $type"
                role = Role.Button
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type == null) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text("Needs details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                } else {
                    Icon(eventTypeIcon(type), contentDescription = null)
                    Text(type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            event.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun eventTypeIcon(type: String): ImageVector = when (type) {
    "pee" -> Icons.Outlined.WaterDrop
    "poo" -> Icons.Outlined.Circle
    "both" -> Icons.Outlined.WaterDrop
    "accident" -> Icons.Outlined.Warning
    "tried" -> Icons.Outlined.RadioButtonUnchecked
    else -> Icons.Outlined.Circle
}
```

Note: Phase 5's Day-Detail intentionally omits the wireframe's "✓ synced" caption — Phase 5 is fully local-only (05-CONTEXT.md D-01), so `sync_status` never leaves `'pending'` until Phase 6 ships the sync layer; showing a synced badge that's always false would be misleading.

- [ ] **Step 4: Wire the route and hide the tab bar during Day-Detail**

In `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt`, add imports `androidx.navigation.NavType` and `androidx.navigation.navArgument`, then change:

```kotlin
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
```

to:

```kotlin
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val hideTabBar = currentDestination?.route?.startsWith("history/day/") == true

    Scaffold(
        bottomBar = {
            if (!hideTabBar) NavigationBar {
```

(the matching closing `}` for the `NavigationBar { ... }` block needs no change — only the opening line gains the `if (!hideTabBar)` guard). Then add the new route inside the `NavHost { ... }` block, after the `composable("history") { ... }` block added in Task 5:

```kotlin
            composable(
                route = "history/day/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { backStackEntry ->
                val dateArg = backStackEntry.arguments?.getString("date") ?: return@composable
                val activeChild by childSelectionViewModel.activeChild.collectAsState()
                val childId = activeChild?.id ?: return@composable
                DayDetailScreen(
                    date = java.time.LocalDate.parse(dateArg),
                    childId = childId,
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
```

Add the import `androidx.compose.runtime.collectAsState` if not already present in the file (it is not, in the Stage-1 version).

- [ ] **Step 5: Build and manually verify**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual QA: from History, tap a non-empty heatmap cell → Day-Detail pushes with the tab bar hidden, date header reads e.g. "Thursday, June 12". Tap any event card (complete or "Needs details") → sheet opens pre-filled. Change the type and tap "Save details" → card updates. Reopen a card, tap "Delete event" → confirmation dialog with exact copy "Remove event?" / "This permanently deletes this event. This cannot be undone." → confirm → card disappears from the list and the heatmap cell's intensity drops on returning to History. Press back (or the back button) → tab bar reappears, heatmap still visible.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/DayDetailScreen.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/DayDetailViewModel.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/EventDetailSheet.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt
git commit -m "feat(05-02): build History Day-Detail with per-event delete"
```

---

<a id="task-7"></a>
## Task 7: Android — Settings shell + Family section

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Clerk SDK `Clerk.organization`, `Clerk.organizationMembership`, `Clerk.user`, `Organization.getOrganizationMemberships()`, `Organization.removeMember(userId)`, `OrganizationMembership.delete()` (all `com.clerk.api.organizations.*`); existing `DestructiveConfirmDialog`.
- Produces: `SettingsViewModel` (role/email/family state, `removeCaregiver`, `leaveFamily`), `SettingsViewModelFactory()` — Task 8 extends this same class (adds a `childrenRepository` constructor param) rather than replacing it.

- [ ] **Step 1: Build `SettingsViewModel`**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt`:

```kotlin
package com.onesteptwo.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.ClerkPaginatedResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.organizations.OrganizationMembership
import com.clerk.api.organizations.delete
import com.clerk.api.organizations.getOrganizationMemberships
import com.clerk.api.organizations.removeMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingsUiState(
    val isAdmin: Boolean = false,
    val userEmail: String = "",
    val familyMembers: List<OrganizationMembership> = emptyList(),
    val isLoadingFamily: Boolean = false,
    val familyError: String? = null
)

/**
 * Backs the Settings tab shell. Role/email come straight from the live Clerk session (no
 * loading state needed); the family member list requires a network call. Children (Task 8) and
 * notification preferences (Task 9) are added to this same view model in later tasks rather than
 * split into per-section view models, since every section shares one Settings screen lifecycle.
 */
class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            isAdmin = Clerk.organizationMembership?.role == "org:admin",
            userEmail = Clerk.user?.primaryEmailAddress?.emailAddress ?: ""
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadFamily()
    }

    fun loadFamily() {
        val org = Clerk.organization ?: return
        _state.update { it.copy(isLoadingFamily = true, familyError = null) }
        viewModelScope.launch {
            when (val result = org.getOrganizationMemberships(limit = 100)) {
                is ClerkResult.Success<*> -> {
                    val members = (result.value as? ClerkPaginatedResponse<OrganizationMembership>)?.data ?: emptyList()
                    _state.update { it.copy(isLoadingFamily = false, familyMembers = members) }
                }
                else -> {
                    Timber.w("SettingsViewModel: failed to load family members")
                    _state.update {
                        it.copy(isLoadingFamily = false, familyError = "Couldn't load data. Pull down to refresh.")
                    }
                }
            }
        }
    }

    /** Family section [✕] — admin removes a caregiver (05-CONTEXT.md D-08, direct Clerk SDK call). */
    fun removeCaregiver(userId: String) {
        val org = Clerk.organization ?: return
        viewModelScope.launch {
            when (org.removeMember(userId)) {
                is ClerkResult.Success -> loadFamily()
                else -> _state.update { it.copy(familyError = "Couldn't remove caregiver. Try again.") }
            }
        }
    }

    /**
     * Caregiver's own "Delete my data" — self-leaves the family org via the Clerk SDK (same
     * direct-Clerk pattern as [removeCaregiver], applied to the current user's own membership).
     * No Go endpoint exists for this: DELETE /v1/account is admin-only (REQ-016) and full-family
     * erasure, so it cannot be reused for a caregiver's own data.
     */
    suspend fun leaveFamily(): Boolean {
        val membership = Clerk.organizationMembership ?: return false
        return when (membership.delete()) {
            is ClerkResult.Success -> true
            else -> false
        }
    }
}

class SettingsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel() as T
    }
}
```

- [ ] **Step 2: Replace the Settings screen stub with the real shell + Family section**

Replace the full contents of `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clerk.api.organizations.OrganizationMembership
import com.onesteptwo.android.ui.common.DestructiveConfirmDialog

/**
 * Settings tab shell (04-UI-SPEC.md §Main App — Settings Tab). Sections build up across
 * 05-02-PLAN.md tasks: Family (Task 7, this file), Children (Task 8), Notifications + Account
 * (Task 9). Role gate is structural — a caregiver never composes the Family or Children sections
 * at all (WIREFRAMES.md: "removed from view tree, not visibility:hidden").
 */
@Composable
fun SettingsScreen(onNavigateToInvite: () -> Unit, onSignOut: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory())
    val state by viewModel.state.collectAsState()
    var pendingRemoveCaregiver by remember { mutableStateOf<OrganizationMembership?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        if (state.isAdmin) {
            SectionLabel("Family")
            state.familyMembers.forEach { member ->
                FamilyMemberRow(member = member, onRemove = { pendingRemoveCaregiver = member })
            }
            SettingsRow(text = "Invite caregiver", onClick = onNavigateToInvite)
            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel("Children")
            Text(
                text = "Coming soon.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        SectionLabel("Notifications")
        Text(
            text = "Coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Account")
        Text(text = state.userEmail, style = MaterialTheme.typography.bodyLarge)
        SettingsRow(text = "Sign out", onClick = onSignOut)
        Spacer(modifier = Modifier.height(24.dp))
    }

    pendingRemoveCaregiver?.let { member ->
        val name = memberDisplayName(member)
        DestructiveConfirmDialog(
            title = "Remove $name?",
            body = "They will lose access to your family immediately.",
            confirmLabel = "Remove",
            dismissLabel = "Keep $name",
            onConfirm = {
                viewModel.removeCaregiver(member.publicUserData?.userId ?: "")
                pendingRemoveCaregiver = null
            },
            onDismiss = { pendingRemoveCaregiver = null }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(text: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun FamilyMemberRow(member: OrganizationMembership, onRemove: () -> Unit) {
    val name = memberDisplayName(member)
    val roleLabel = if (member.role == "org:admin") "Admin" else "Caregiver"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$name  ·  $roleLabel", style = MaterialTheme.typography.bodyLarge)
        // D-11: v1 has exactly one admin — never render remove on the admin's own row.
        if (member.role != "org:admin") {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove $name, destructive")
            }
        }
    }
}

private fun memberDisplayName(member: OrganizationMembership): String {
    val u = member.publicUserData ?: return "Member"
    return "${u.firstName.orEmpty()} ${u.lastName.orEmpty()}".trim().ifBlank { u.identifier }
}
```

- [ ] **Step 3: Build and manually verify**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual QA: sign in as admin → Settings shows "Family" with the admin's own row (no `[✕]`) and any invited caregivers (with `[✕]`) → "Invite caregiver" still navigates correctly (Stage 1 route unchanged). Tap `[✕]` on a caregiver row → confirmation dialog "Remove [name]?" / "They will lose access to your family immediately." → confirm → row disappears. Sign in as caregiver → Settings shows no Family section at all (not blank space — the composable isn't in the tree).

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt
git commit -m "feat(05-02): build Settings shell and Family section"
```

---

<a id="task-8"></a>
## Task 8: Android — Settings Children section (add/edit/remove)

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/AddChildScreen.kt`
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/EditChildScreen.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/OnboardingViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/ChildDetailsStep.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/ConsentStep.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt`

**Interfaces:**
- Consumes: Task 4's `ChildrenApiClient.patchChild`/`.deleteChild`; Task 3's `ChildrenRepository.update`, `PottyEventsRepository.deleteAllForChild`, `NotificationPreferencesRepository.deleteLocal`; onboarding's `ChildDetailsStep`/`ConsentStep`/`CONSENT_TEXT_VERSION` (widened for reuse in this task).
- Produces: `AddChildScreen(container, onDone)`, `EditChildScreen(container, child, onDone)`, wired at routes `settings/children/add` and `settings/children/{id}/edit`.

- [ ] **Step 1: Widen `ChildDetailsStep`/`ConsentStep` for reuse outside the onboarding wizard**

These composables currently always render onboarding's 4-dot `StepDots`, which is wrong outside the wizard. Add an opt-out param defaulting to the current (onboarding) behavior, so `OnboardingScreen.kt`'s existing call sites need no change.

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/ChildDetailsStep.kt`, change the signature from:

```kotlin
fun ChildDetailsStep(
    nickname: String,
    birthMonth: Int?,
    birthYear: Int?,
    errorMessage: String?,
    onNicknameChange: (String) -> Unit,
    onBirthMonthChange: (Int) -> Unit,
    onBirthYearChange: (Int) -> Unit,
    onContinue: () -> Unit
) {
```

to:

```kotlin
fun ChildDetailsStep(
    nickname: String,
    birthMonth: Int?,
    birthYear: Int?,
    errorMessage: String?,
    onNicknameChange: (String) -> Unit,
    onBirthMonthChange: (Int) -> Unit,
    onBirthYearChange: (Int) -> Unit,
    onContinue: () -> Unit,
    showStepDots: Boolean = true
) {
```

and change:

```kotlin
        StepDots(activeIndex = 3)
        Spacer(modifier = Modifier.height(24.dp))
```

to:

```kotlin
        if (showStepDots) {
            StepDots(activeIndex = 3)
            Spacer(modifier = Modifier.height(24.dp))
        }
```

Also promote the file-private dropdown helper to `internal` so `EditChildScreen.kt` (Step 3 below) can reuse it instead of duplicating ~15 lines of `ExposedDropdownMenuBox` boilerplate. Change:

```kotlin
private fun ExposedDropdownMenuBoxScope.MonthYearDropdownContent(value: String, label: String, expanded: Boolean) {
```

to:

```kotlin
internal fun ExposedDropdownMenuBoxScope.MonthYearDropdownContent(value: String, label: String, expanded: Boolean) {
```

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/ConsentStep.kt`, apply the same `showStepDots` change: signature gains `showStepDots: Boolean = true` as its last parameter, and:

```kotlin
        StepDots(activeIndex = 4)
        Spacer(modifier = Modifier.height(24.dp))
```

becomes:

```kotlin
        if (showStepDots) {
            StepDots(activeIndex = 4)
            Spacer(modifier = Modifier.height(24.dp))
        }
```

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/OnboardingViewModel.kt`, widen the consent-version constant's visibility so `AddChildViewModel` (Step 2 below) can import it instead of duplicating the string. Change:

```kotlin
private const val CONSENT_TEXT_VERSION = "consent_v1"
```

to:

```kotlin
const val CONSENT_TEXT_VERSION = "consent_v1"
```

- [ ] **Step 2: Build `AddChildScreen`**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/AddChildScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clerk.api.Clerk
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.BuildConfig
import com.onesteptwo.android.ui.onboarding.CONSENT_TEXT_VERSION
import com.onesteptwo.android.ui.onboarding.ChildDetailsStep
import com.onesteptwo.android.ui.onboarding.ConsentStep
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.ConsentEventsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class AddChildStep { DETAILS, CONSENT }

data class AddChildUiState(
    val step: AddChildStep = AddChildStep.DETAILS,
    val nickname: String = "",
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val consentChecked: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

/**
 * Settings "Add child" — repeats the full consent screen (05-CONTEXT.md D-09), reusing the
 * onboarding wizard's [ChildDetailsStep] and [ConsentStep] composables with `showStepDots =
 * false` (this is a standalone 2-step flow, not the 4-step onboarding wizard). No family/org
 * creation step — the org already exists.
 */
class AddChildViewModel(
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val consentEventsRepository: ConsentEventsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AddChildUiState())
    val state: StateFlow<AddChildUiState> = _state.asStateFlow()

    fun updateNickname(value: String) = _state.update { it.copy(nickname = value, errorMessage = null) }
    fun updateBirthMonth(month: Int) = _state.update { it.copy(birthMonth = month, errorMessage = null) }
    fun updateBirthYear(year: Int) = _state.update { it.copy(birthYear = year, errorMessage = null) }
    fun updateConsentChecked(checked: Boolean) = _state.update { it.copy(consentChecked = checked) }

    fun continueFromDetails() {
        val s = _state.value
        if (s.nickname.isBlank() || s.birthMonth == null || s.birthYear == null) {
            _state.update { it.copy(errorMessage = "Enter your child's nickname and birth month/year.") }
            return
        }
        _state.update { it.copy(step = AddChildStep.CONSENT, errorMessage = null) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun continueFromConsent() {
        val s = _state.value
        if (!s.consentChecked || s.isSubmitting) return
        val birthMonth = s.birthMonth ?: return
        val birthYear = s.birthYear ?: return
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = childrenApiClient.createChild(
                    nickname = s.nickname.trim(),
                    birthMonth = birthMonth,
                    birthYear = birthYear,
                    appVersion = BuildConfig.VERSION_NAME,
                    consentTextVersion = CONSENT_TEXT_VERSION
                )
            ) {
                is ApiResult.Success -> {
                    val child = result.value
                    val now = Instant.now().toString()
                    val localConsentId = Uuid.random().toString()
                    consentEventsRepository.insert(
                        id = localConsentId,
                        clerkUserId = Clerk.user?.id ?: "",
                        consentedAt = now,
                        appVersion = BuildConfig.VERSION_NAME,
                        consentTextVersion = CONSENT_TEXT_VERSION
                    )
                    childrenRepository.insert(
                        id = child.id,
                        clerkOrgId = child.clerk_org_id,
                        nickname = child.nickname,
                        birthMonth = child.birth_month.toLong(),
                        birthYear = child.birth_year.toLong(),
                        consentEventId = localConsentId,
                        createdAt = now,
                        updatedAt = now
                    )
                    _state.update { it.copy(isSubmitting = false, done = true) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }
}

class AddChildViewModelFactory(
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val consentEventsRepository: ConsentEventsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddChildViewModel(childrenApiClient, childrenRepository, consentEventsRepository) as T
}

@Composable
fun AddChildScreen(container: AppContainer, onDone: () -> Unit) {
    val viewModel: AddChildViewModel = viewModel(
        factory = AddChildViewModelFactory(
            container.childrenApiClient,
            container.childrenRepository,
            container.consentEventsRepository
        )
    )
    val state by viewModel.state.collectAsState()

    if (state.done) {
        onDone()
        return
    }

    when (state.step) {
        AddChildStep.DETAILS -> ChildDetailsStep(
            nickname = state.nickname,
            birthMonth = state.birthMonth,
            birthYear = state.birthYear,
            errorMessage = state.errorMessage,
            onNicknameChange = viewModel::updateNickname,
            onBirthMonthChange = viewModel::updateBirthMonth,
            onBirthYearChange = viewModel::updateBirthYear,
            onContinue = viewModel::continueFromDetails,
            showStepDots = false
        )
        AddChildStep.CONSENT -> ConsentStep(
            consentChecked = state.consentChecked,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onConsentCheckedChange = viewModel::updateConsentChecked,
            onContinue = viewModel::continueFromConsent,
            showStepDots = false
        )
    }
}
```

- [ ] **Step 3: Build `EditChildScreen`**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/EditChildScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.common.DestructiveConfirmDialog
import com.onesteptwo.android.ui.onboarding.MonthYearDropdownContent
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.ChildrenApiClient
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.data.NotificationPreferencesRepository
import com.onesteptwo.data.PottyEventsRepository
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Year
import java.time.format.TextStyle
import java.util.Locale

data class EditChildUiState(
    val nickname: String = "",
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

class EditChildViewModel(
    private val child: Children,
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val pottyEventsRepository: PottyEventsRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditChildUiState(
            nickname = child.nickname,
            birthMonth = child.birth_month.toInt(),
            birthYear = child.birth_year.toInt()
        )
    )
    val state: StateFlow<EditChildUiState> = _state.asStateFlow()

    fun updateNickname(value: String) = _state.update { it.copy(nickname = value, errorMessage = null) }
    fun updateBirthMonth(month: Int) = _state.update { it.copy(birthMonth = month, errorMessage = null) }
    fun updateBirthYear(year: Int) = _state.update { it.copy(birthYear = year, errorMessage = null) }

    fun save() {
        val s = _state.value
        val birthMonth = s.birthMonth ?: return
        val birthYear = s.birthYear ?: return
        if (s.nickname.isBlank() || s.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = childrenApiClient.patchChild(
                    id = child.id,
                    nickname = s.nickname.trim(),
                    birthMonth = birthMonth,
                    birthYear = birthYear
                )
            ) {
                is ApiResult.Success -> {
                    val updated = result.value
                    childrenRepository.update(
                        id = updated.id,
                        nickname = updated.nickname,
                        birthMonth = updated.birth_month.toLong(),
                        birthYear = updated.birth_year.toLong(),
                        updatedAt = Instant.now().toString()
                    )
                    _state.update { it.copy(isSubmitting = false, done = true) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }

    /** "Remove child" destructive row — delete lives inside the edit screen (05-CONTEXT.md D-06 pattern, applied to children). */
    fun removeChild() {
        viewModelScope.launch {
            when (childrenApiClient.deleteChild(child.id)) {
                is ApiResult.Success -> {
                    childrenRepository.deleteById(child.id)
                    pottyEventsRepository.deleteAllForChild(child.id)
                    notificationPreferencesRepository.deleteLocal(child.id)
                    _state.update { it.copy(done = true) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(errorMessage = "Couldn't remove child. Try again.") }
                }
            }
        }
    }
}

class EditChildViewModelFactory(
    private val child: Children,
    private val childrenApiClient: ChildrenApiClient,
    private val childrenRepository: ChildrenRepository,
    private val pottyEventsRepository: PottyEventsRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditChildViewModel(child, childrenApiClient, childrenRepository, pottyEventsRepository, notificationPreferencesRepository) as T
}

private val MonthNames = (1..12).map { month ->
    java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChildScreen(container: AppContainer, child: Children, onDone: () -> Unit) {
    val viewModel: EditChildViewModel = viewModel(
        factory = EditChildViewModelFactory(
            child,
            container.childrenApiClient,
            container.childrenRepository,
            container.pottyEventsRepository,
            container.notificationPreferencesRepository
        )
    )
    val state by viewModel.state.collectAsState()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val currentYear = Year.now().value
    val years = (currentYear downTo currentYear - 10).toList()

    if (state.done) {
        onDone()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Edit ${child.nickname}", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        androidx.compose.material3.OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::updateNickname,
            label = { Text("Nickname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExposedDropdownMenuBox(
                expanded = monthExpanded,
                onExpandedChange = { monthExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                MonthYearDropdownContent(
                    value = state.birthMonth?.let { MonthNames[it - 1] } ?: "",
                    label = "Birth month",
                    expanded = monthExpanded
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                    MonthNames.forEachIndexed { index, name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            viewModel.updateBirthMonth(index + 1)
                            monthExpanded = false
                        })
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                MonthYearDropdownContent(
                    value = state.birthYear?.toString() ?: "",
                    label = "Birth year",
                    expanded = yearExpanded
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    years.forEach { year ->
                        DropdownMenuItem(text = { Text(year.toString()) }, onClick = {
                            viewModel.updateBirthYear(year)
                            yearExpanded = false
                        })
                    }
                }
            }
        }

        state.errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = viewModel::save, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Save changes")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { showRemoveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Remove child", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showRemoveConfirm) {
        DestructiveConfirmDialog(
            title = "Remove ${child.nickname}?",
            body = "This permanently erases all events and data for ${child.nickname}. This cannot be undone.",
            confirmLabel = "Remove ${child.nickname}",
            dismissLabel = "Keep ${child.nickname}",
            onConfirm = {
                showRemoveConfirm = false
                viewModel.removeChild()
            },
            onDismiss = { showRemoveConfirm = false }
        )
    }
}
```

(Copy exactly matches `04-UI-SPEC.md` §Destructive Action Confirmations "Remove child (admin)" row.)

- [ ] **Step 4: Extend `SettingsViewModel` with the children list**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt`, add imports `androidx.lifecycle.viewModelScope` (already present), `com.onesteptwo.data.ChildrenRepository`, `com.onesteptwo.db.Children`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.stateIn`. Change the class declaration from:

```kotlin
class SettingsViewModel : ViewModel() {
```

to:

```kotlin
class SettingsViewModel(private val childrenRepository: ChildrenRepository) : ViewModel() {
```

Add this property inside the class body (after `val state: StateFlow<SettingsUiState> = ...`):

```kotlin
    val children: StateFlow<List<Children>> =
        childrenRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

And update the factory at the bottom of the file:

```kotlin
class SettingsViewModelFactory(private val childrenRepository: ChildrenRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(childrenRepository) as T
    }
}
```

- [ ] **Step 5: Wire the real Children section into `SettingsScreen`**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt`, add imports `com.onesteptwo.android.AppContainer` and `com.onesteptwo.db.Children`. Change the function signature from:

```kotlin
fun SettingsScreen(onNavigateToInvite: () -> Unit, onSignOut: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory())
```

to:

```kotlin
fun SettingsScreen(
    container: AppContainer,
    onNavigateToInvite: () -> Unit,
    onNavigateToAddChild: () -> Unit,
    onNavigateToEditChild: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container.childrenRepository))
    val children by viewModel.children.collectAsState()
```

Then replace the Children section's placeholder block:

```kotlin
            SectionLabel("Children")
            Text(
                text = "Coming soon.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
```

with:

```kotlin
            SectionLabel("Children")
            children.forEach { child ->
                SettingsRow(
                    text = "${child.nickname}  (${childBirthLabel(child)})",
                    onClick = { onNavigateToEditChild(child.id) }
                )
            }
            SettingsRow(text = "Add child", onClick = onNavigateToAddChild)
            Spacer(modifier = Modifier.height(24.dp))
```

And add this helper function near the bottom of the file, alongside `memberDisplayName`:

```kotlin
private fun childBirthLabel(child: Children): String {
    val monthName = java.time.Month.of(child.birth_month.toInt())
        .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    return "$monthName ${child.birth_year}"
}
```

- [ ] **Step 6: Wire the new routes**

In `androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt`, add imports `com.onesteptwo.android.ui.settings.AddChildScreen`, `com.onesteptwo.android.ui.settings.EditChildScreen`, `androidx.compose.runtime.LaunchedEffect` (if not already present — it is, from the `settings/invite` route). Replace:

```kotlin
            composable("settings") {
                SettingsScreen(
                    onNavigateToInvite = { navController.navigate("settings/invite") },
                    onSignOut = onSignOut
                )
            }
```

with:

```kotlin
            composable("settings") {
                SettingsScreen(
                    container = container,
                    onNavigateToInvite = { navController.navigate("settings/invite") },
                    onNavigateToAddChild = { navController.navigate("settings/children/add") },
                    onNavigateToEditChild = { childId -> navController.navigate("settings/children/$childId/edit") },
                    onSignOut = onSignOut
                )
            }
            composable("settings/children/add") {
                AddChildScreen(container = container, onDone = { navController.popBackStack() })
            }
            composable(
                route = "settings/children/{childId}/edit",
                arguments = listOf(navArgument("childId") { type = NavType.StringType })
            ) { backStackEntry ->
                val childId = backStackEntry.arguments?.getString("childId") ?: return@composable
                val children by childSelectionViewModel.children.collectAsState()
                val child = children.firstOrNull { it.id == childId } ?: return@composable
                EditChildScreen(container = container, child = child, onDone = { navController.popBackStack() })
            }
```

(`navArgument`/`NavType` imports already added in Task 6; `collectAsState` import already added in Task 6.)

- [ ] **Step 7: Build and manually verify**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual QA: Settings (admin) → "Children" lists each child as "[Nickname] (Mon YYYY)" → tap "Add child" → 2-step flow (details, no step dots → consent, no step dots) → "I agree — continue" → new child appears in the list and is immediately available in the Home/History child switcher. Tap an existing child row → Edit screen prefilled → change nickname → "Save changes" → list reflects the new name. Tap "Remove child" → confirmation "Remove [name]?" with the exact REQ-014 table copy → confirm → child, its events, and its heatmap all disappear.

- [ ] **Step 8: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/AddChildScreen.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/EditChildScreen.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/OnboardingViewModel.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/ChildDetailsStep.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/onboarding/ConsentStep.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/navigation/MainTabNavigation.kt
git commit -m "feat(05-02): build Settings Children section (add/edit/remove)"
```

---

<a id="task-9"></a>
## Task 9: Android — Notifications + Account sections

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/NotificationPreferenceRow.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Task 3's `NotificationPreferencesRepository`, Task 4's `NotificationPreferencesApiClient`, Task 4's `ChildrenApiClient.deleteAccount`, Task 7's `SettingsViewModel.leaveFamily`.
- Produces: final `SettingsScreen` — every section real, matching both the Admin and Caregiver wireframes exactly.

- [ ] **Step 1: Build `NotificationPreferenceRow`**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/NotificationPreferenceRow.kt`:

```kotlin
package com.onesteptwo.android.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onesteptwo.api.ApiResult
import com.onesteptwo.api.NotificationPreferencesApiClient
import com.onesteptwo.data.NotificationPreferencesRepository
import kotlinx.coroutines.launch

/** REQ-023 opt-in default: [NotificationPreferencesRepository.observe] defaults every unseen
 * child to enabled=true. Toggling writes optimistically to the local cache, fires the real PUT
 * (05-CONTEXT.md D-02 — Settings hits the API directly), and reverts on failure. */
@Composable
fun NotificationPreferenceRow(
    childId: String,
    childNickname: String,
    repository: NotificationPreferencesRepository,
    apiClient: NotificationPreferencesApiClient
) {
    val enabled by repository.observe(childId).collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Notify me for $childNickname",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = { newValue ->
                scope.launch {
                    repository.setLocal(childId, newValue)
                    val result = apiClient.putPreference(childId, newValue)
                    if (result is ApiResult.Failure) {
                        repository.setLocal(childId, !newValue)
                    }
                }
            },
            modifier = Modifier.semantics {
                contentDescription = "Notify me for $childNickname, ${if (enabled) "on" else "off"}"
            }
        )
    }
}
```

- [ ] **Step 2: Extend `SettingsViewModel` with notification refresh and account erasure**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt`, add imports `com.onesteptwo.api.ApiResult`, `com.onesteptwo.api.ChildrenApiClient`, `com.onesteptwo.api.NotificationPreferencesApiClient`, `com.onesteptwo.data.NotificationPreferencesRepository`. Change the class declaration from:

```kotlin
class SettingsViewModel(private val childrenRepository: ChildrenRepository) : ViewModel() {
```

to:

```kotlin
class SettingsViewModel(
    private val childrenRepository: ChildrenRepository,
    private val childrenApiClient: ChildrenApiClient,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notificationPreferencesApiClient: NotificationPreferencesApiClient
) : ViewModel() {
```

Add these methods inside the class body (after `leaveFamily`):

```kotlin
    /** Refreshes the local notification-preference cache from the server (Settings screen load). */
    fun refreshNotificationPreferences() {
        viewModelScope.launch {
            when (val result = notificationPreferencesApiClient.getPreferences()) {
                is ApiResult.Success -> result.value.forEach { pref ->
                    notificationPreferencesRepository.setLocal(pref.child_id, pref.enabled)
                }
                is ApiResult.Failure -> Unit // local cache already shows last-known/default state
            }
        }
    }

    /** Admin "Delete my data" — REQ-012 full family erasure via DELETE /v1/account. */
    suspend fun deleteAccount(): Boolean =
        when (childrenApiClient.deleteAccount()) {
            is ApiResult.Success -> true
            is ApiResult.Failure -> false
        }
```

And update the factory:

```kotlin
class SettingsViewModelFactory(
    private val childrenRepository: ChildrenRepository,
    private val childrenApiClient: ChildrenApiClient,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notificationPreferencesApiClient: NotificationPreferencesApiClient
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            childrenRepository,
            childrenApiClient,
            notificationPreferencesRepository,
            notificationPreferencesApiClient
        ) as T
    }
}
```

- [ ] **Step 3: Wire the real Notifications + Account sections**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt`, add imports `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`. Change the `viewModel(...)` construction line from:

```kotlin
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container.childrenRepository))
    val children by viewModel.children.collectAsState()
```

to:

```kotlin
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            container.childrenRepository,
            container.childrenApiClient,
            container.notificationPreferencesRepository,
            container.notificationPreferencesApiClient
        )
    )
    val children by viewModel.children.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showDeleteMyDataConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshNotificationPreferences() }
```

Replace the Notifications section's placeholder block:

```kotlin
        SectionLabel("Notifications")
        Text(
            text = "Coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
```

with:

```kotlin
        SectionLabel("Notifications")
        children.forEach { child ->
            NotificationPreferenceRow(
                childId = child.id,
                childNickname = child.nickname,
                repository = container.notificationPreferencesRepository,
                apiClient = container.notificationPreferencesApiClient
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
```

Replace the Account section:

```kotlin
        SectionLabel("Account")
        Text(text = state.userEmail, style = MaterialTheme.typography.bodyLarge)
        SettingsRow(text = "Sign out", onClick = onSignOut)
        Spacer(modifier = Modifier.height(24.dp))
```

with:

```kotlin
        SectionLabel("Account")
        Text(text = state.userEmail, style = MaterialTheme.typography.bodyLarge)
        SettingsRow(text = "Sign out", onClick = onSignOut)
        SettingsRow(text = "Delete my data", onClick = { showDeleteMyDataConfirm = true }, isDestructive = true)
        Spacer(modifier = Modifier.height(24.dp))
```

(`SettingsScreen`'s params already include `container: AppContainer` from Task 8 — no signature change needed here.) Finally, add the confirmation dialog block near the existing `pendingRemoveCaregiver?.let { ... }` block at the bottom of the composable, branching copy by role since the admin action is full-family erasure while the caregiver action is a self-leave:

```kotlin
    if (showDeleteMyDataConfirm) {
        if (state.isAdmin) {
            DestructiveConfirmDialog(
                title = "Delete family account?",
                body = "This permanently erases all family members, children, and events. This cannot be undone.",
                confirmLabel = "Delete everything",
                dismissLabel = "Keep account",
                onConfirm = {
                    showDeleteMyDataConfirm = false
                    coroutineScope.launch {
                        if (viewModel.deleteAccount()) onSignOut()
                    }
                },
                onDismiss = { showDeleteMyDataConfirm = false }
            )
        } else {
            DestructiveConfirmDialog(
                title = "Delete your data?",
                body = "Your account and all data will be permanently erased. This cannot be undone.",
                confirmLabel = "Delete my data",
                dismissLabel = "Keep my data",
                onConfirm = {
                    showDeleteMyDataConfirm = false
                    coroutineScope.launch {
                        if (viewModel.leaveFamily()) onSignOut()
                    }
                },
                onDismiss = { showDeleteMyDataConfirm = false }
            )
        }
    }
```

(Copy for the admin path matches `04-UI-SPEC.md` §Destructive Action Confirmations "Close family account (admin)" row exactly, since `DELETE /v1/account` is a full-family erasure and that row accurately describes its effect. The caregiver path uses the generic "Delete my data (any user)" row copy, per this plan's decision that a caregiver's own erasure is a Clerk self-leave, not a call to the admin-only endpoint.)

- [ ] **Step 4: Build and manually verify**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Manual QA:
- Toggle a notification preference off → kill and relaunch the app → Settings shows the toggle still off (round-tripped through `GET /v1/notification-preferences` on next load).
- Sign in as a caregiver → Settings shows exactly 2 sections (Notifications, Account) → "Delete my data" is reachable in 2 taps from the Settings tab (tap Settings tab → tap "Delete my data") → confirm → app returns to Sign In (caregiver left the org via Clerk).
- Sign in as admin → tap "Delete my data" → dialog reads "Delete family account?" / "This permanently erases all family members, children, and events. This cannot be undone." → confirm → app returns to Sign In and the family/org no longer exists (verify by attempting sign-in again with the same admin credentials: onboarding wizard reappears since no org remains).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/NotificationPreferenceRow.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsViewModel.kt \
        androidApp/src/main/kotlin/com/onesteptwo/android/ui/settings/SettingsScreen.kt
git commit -m "feat(05-02): build Settings Notifications and Account sections"
```

---

## Verification

- After every backend task (1, 2): `cd backend && go test ./...` passes.
- After every Android task (3-9): `./gradlew :shared:assembleDebug :androidApp:assembleDebug` succeeds.
- Full manual regression pass (after Task 9, on a clean install):
  1. Sign up as a new admin → onboarding wizard → Home tab.
  2. Log several events with different types on different days (adjust device clock or wait a day if testing the multi-week grow behavior) → History tab shows the heatmap growing correctly, capped at 12 weeks once the family is that old.
  3. Tap a heatmap cell → Day-Detail → tap a card → edit → save → delete another card with confirmation → return to History, heatmap reflects the change.
  4. Settings (admin): invite a caregiver, remove a caregiver, add a second child (repeats consent), edit a child, remove a child, toggle notifications per child, verify "Delete my data" reachable in 2 taps and its copy matches "Delete family account?".
  5. Sign in as the invited caregiver (separate device/account): Settings shows only Notifications + Account; toggle a notification preference; "Delete my data" self-leaves the family and returns to Sign In.
