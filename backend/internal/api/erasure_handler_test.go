package api_test

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// stubClerk is a test double implementing api.ClerkOrgClient.
// memberErr, if non-nil, is returned by ListOrgMemberUserIDs.
// deleteErr, if non-nil, is returned by DeleteOrganization.
type stubClerk struct {
	memberErr error
	members   []string
	deleteErr error
	deleted   bool
}

func (s *stubClerk) ListOrgMemberUserIDs(_ context.Context, _ string) ([]string, error) {
	return s.members, s.memberErr
}

func (s *stubClerk) DeleteOrganization(_ context.Context, _ string) error {
	s.deleted = true
	return s.deleteErr
}

// TestDeleteV1ChildrenId_MissingAuthHeader verifies that DELETE /v1/children/{id}
// without session claims returns 401 with Content-Type application/problem+json.
// The handler must return before touching the DB on this path (nil DB Server is safe for this test).
func TestDeleteV1ChildrenId_MissingAuthHeader(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 401 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	// Use a valid UUID-format path — the handler checks auth before parsing the id.
	req := httptest.NewRequest(http.MethodDelete, "/v1/children/00000000-0000-0000-0000-000000000001", nil)
	// No claims injected — handler returns 401 (no Clerk JWT in context).
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
// without session claims returns 401 with Content-Type application/problem+json.
// The handler must return before touching the DB on this path (nil DB Server is safe for this test).
func TestDeleteV1Account_MissingAuthHeader(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 401 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodDelete, "/v1/account", nil)
	// No claims injected — handler returns 401 (no Clerk JWT in context).
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

// TestDeleteV1Account_ClerkMemberFetchError verifies that when the Clerk org-member
// list cannot be fetched, DELETE /v1/account returns 500 application/problem+json
// BEFORE any data is deleted (nil DB is safe — the handler returns before BeginTx).
func TestDeleteV1Account_ClerkMemberFetchError(t *testing.T) {
	clerkStub := &stubClerk{memberErr: errors.New("clerk down")}
	srv := &api.Server{Clerk: clerkStub} // nil DB is safe: error returned before BeginTx
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodDelete, "/v1/account", nil)
	// Inject admin claims so the request passes the auth gate and reaches the Clerk member-fetch path.
	req = withFakeClaims(req, "user_abc", "org_xyz", "org:admin")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Errorf("expected 500, got %d", rec.Code)
	}
	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
}
