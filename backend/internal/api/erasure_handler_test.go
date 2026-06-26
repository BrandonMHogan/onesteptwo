package api_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestDeleteV1ChildrenId_MissingAuthHeader verifies that DELETE /v1/children/{id}
// without an X-Clerk-User-Id header returns 401 with Content-Type application/problem+json.
// The handler must return before touching the DB on this path (nil DB Server is safe for this test).
func TestDeleteV1ChildrenId_MissingAuthHeader(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 401 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	// Use a valid UUID-format path — the handler checks auth before parsing the id.
	req := httptest.NewRequest(http.MethodDelete, "/v1/children/00000000-0000-0000-0000-000000000001", nil)
	// Intentionally omit X-Clerk-User-Id to trigger 401.
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
}

// TestDeleteV1Account_MissingAuthHeader verifies that DELETE /v1/account
// without identity headers returns 401 with Content-Type application/problem+json.
// The handler must return before touching the DB on this path (nil DB Server is safe for this test).
func TestDeleteV1Account_MissingAuthHeader(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 401 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodDelete, "/v1/account", nil)
	// Intentionally omit all auth headers to trigger 401.
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
}
