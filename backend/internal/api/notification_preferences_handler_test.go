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
