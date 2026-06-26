package api_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

func TestHealthz(t *testing.T) {
	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", rec.Code)
	}
	if rec.Body.Len() != 0 {
		t.Errorf("expected empty body, got %d bytes: %s", rec.Body.Len(), rec.Body.String())
	}
}

func TestWriteProblem(t *testing.T) {
	rec := httptest.NewRecorder()
	api.WriteProblem(rec, http.StatusBadRequest, "about:blank", "Bad Request", "field x missing")

	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}
	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected status 400, got %d", rec.Code)
	}

	var detail struct {
		Type   string `json:"type"`
		Title  string `json:"title"`
		Status int    `json:"status"`
		Detail string `json:"detail"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&detail); err != nil {
		t.Fatalf("failed to decode response body: %v", err)
	}
	if detail.Status != http.StatusBadRequest {
		t.Errorf("expected JSON status 400, got %d", detail.Status)
	}
	if detail.Type == "" {
		t.Errorf("expected non-empty type field")
	}
	if detail.Title == "" {
		t.Errorf("expected non-empty title field")
	}
	if detail.Detail == "" {
		t.Errorf("expected non-empty detail field")
	}
}
