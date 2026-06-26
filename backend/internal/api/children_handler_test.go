package api_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestPostV1Children_MissingBody verifies that POST /v1/children with no body returns 400
// with Content-Type application/problem+json (REQ-C-001, Security V5 input validation).
func TestPostV1Children_MissingBody(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns before DB use on validation error
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodPost, "/v1/children", nil)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Clerk-User-Id", "user_test")
	req.Header.Set("X-Clerk-Org-Id", "org_test")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", rec.Code)
	}
	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
	var problem struct {
		Status int `json:"status"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&problem); err != nil {
		t.Fatalf("failed to decode problem response: %v", err)
	}
	if problem.Status != http.StatusBadRequest {
		t.Errorf("expected JSON status 400, got %d", problem.Status)
	}
}

// TestPostV1Children_MalformedBody verifies that a malformed JSON body returns 400.
func TestPostV1Children_MalformedBody(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodPost, "/v1/children", strings.NewReader("{not valid json"))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Clerk-User-Id", "user_test")
	req.Header.Set("X-Clerk-Org-Id", "org_test")
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

// TestPostV1Children_MissingAuthHeaders verifies that POST /v1/children without
// X-Clerk-User-Id or X-Clerk-Org-Id returns 401 (Phase 2 placeholder auth gate).
func TestPostV1Children_MissingAuthHeaders(t *testing.T) {
	srv := &api.Server{} // nil DB is safe: handler returns 401 before DB use
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body := map[string]any{
		"nickname":    "TestKid",
		"birth_month": 3,
		"birth_year":  2020,
		"consent": map[string]string{
			"app_version":          "1.0.0",
			"consent_text_version": "v1",
		},
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest(http.MethodPost, "/v1/children", bytes.NewReader(bodyBytes))
	req.Header.Set("Content-Type", "application/json")
	// No X-Clerk-User-Id or X-Clerk-Org-Id headers
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

// TestPostV1Children_MissingClerkUserId verifies 401 when only X-Clerk-Org-Id is present.
func TestPostV1Children_MissingClerkUserId(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	body := map[string]any{
		"nickname":    "TestKid",
		"birth_month": 3,
		"birth_year":  2020,
		"consent": map[string]string{
			"app_version":          "1.0.0",
			"consent_text_version": "v1",
		},
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest(http.MethodPost, "/v1/children", bytes.NewReader(bodyBytes))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Clerk-Org-Id", "org_test") // Only org id, no user id
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", rec.Code)
	}
}

// TestPostV1Children_InvalidInput verifies that POST /v1/children with an invalid
// birth_month (outside 1-12) or empty nickname returns 400.
func TestPostV1Children_InvalidInput(t *testing.T) {
	cases := []struct {
		name string
		body map[string]any
	}{
		{
			name: "birth_month_zero",
			body: map[string]any{
				"nickname":    "TestKid",
				"birth_month": 0, // invalid: must be 1-12
				"birth_year":  2020,
				"consent": map[string]string{
					"app_version":          "1.0.0",
					"consent_text_version": "v1",
				},
			},
		},
		{
			name: "birth_month_thirteen",
			body: map[string]any{
				"nickname":    "TestKid",
				"birth_month": 13, // invalid: must be 1-12
				"birth_year":  2020,
				"consent": map[string]string{
					"app_version":          "1.0.0",
					"consent_text_version": "v1",
				},
			},
		},
		{
			name: "empty_nickname",
			body: map[string]any{
				"nickname":    "", // invalid: must be non-empty
				"birth_month": 6,
				"birth_year":  2020,
				"consent": map[string]string{
					"app_version":          "1.0.0",
					"consent_text_version": "v1",
				},
			},
		},
		{
			name: "missing_consent_app_version",
			body: map[string]any{
				"nickname":    "TestKid",
				"birth_month": 6,
				"birth_year":  2020,
				"consent": map[string]string{
					"app_version":          "", // invalid: must be non-empty
					"consent_text_version": "v1",
				},
			},
		},
		{
			name: "missing_consent_text_version",
			body: map[string]any{
				"nickname":    "TestKid",
				"birth_month": 6,
				"birth_year":  2020,
				"consent": map[string]string{
					"app_version":          "1.0.0",
					"consent_text_version": "", // invalid: must be non-empty
				},
			},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			srv := &api.Server{} // nil DB is safe: handler returns 400 before DB use
			mux := http.NewServeMux()
			api.HandlerFromMux(srv, mux)

			bodyBytes, _ := json.Marshal(tc.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/children", bytes.NewReader(bodyBytes))
			req.Header.Set("Content-Type", "application/json")
			req.Header.Set("X-Clerk-User-Id", "user_test")
			req.Header.Set("X-Clerk-Org-Id", "org_test")
			rec := httptest.NewRecorder()
			mux.ServeHTTP(rec, req)

			if rec.Code != http.StatusBadRequest {
				t.Errorf("case %s: expected 400, got %d", tc.name, rec.Code)
			}
			ct := rec.Header().Get("Content-Type")
			if ct != "application/problem+json" {
				t.Errorf("case %s: expected Content-Type application/problem+json, got %q", tc.name, ct)
			}
		})
	}
}
