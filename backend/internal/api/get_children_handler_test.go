package api_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestGetV1Children_MissingAuthHeaders verifies that GET /v1/children without session
// claims returns 401 (no Clerk JWT in context → 401).
func TestGetV1Children_MissingAuthHeaders(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 401 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/v1/children", nil)
	// No claims injected — handler returns 401.
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
}

// TestGetV1Children_NoActiveOrg verifies that GET /v1/children with claims but no active
// organization returns 403 (REQ-027).
func TestGetV1Children_NoActiveOrg(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 403 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/v1/children", nil)
	req = withFakeClaims(req, "user_test", "", "org:admin") // empty orgID → no active org
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Errorf("expected 403, got %d", rec.Code)
	}
}

// TestGetV1Children_CaregiverAllowed verifies that a non-admin caregiver with an active org
// is NOT rejected for role (unlike POST/DELETE) — it should proceed past the auth gate to a
// DB call, which panics on the nil-DB test server. A panic recovered here (rather than a 401/
// 403) is itself the assertion that no role check exists.
func TestGetV1Children_CaregiverAllowed(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/v1/children", nil)
	req = withFakeClaims(req, "user_test", "org_test", "org:caregiver")
	rec := httptest.NewRecorder()

	defer func() {
		if recovered := recover(); recovered == nil {
			t.Fatal("expected a panic from the nil DB call past the auth gate (no role check), got none")
		}
	}()
	mux.ServeHTTP(rec, req)
}
