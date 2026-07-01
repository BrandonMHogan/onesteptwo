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
