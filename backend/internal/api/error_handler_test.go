package api_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestProblemErrorHandler_InvalidUUIDPathParam verifies that a DELETE request
// with a non-UUID {id} path parameter returns an RFC 7807 application/problem+json
// response (REQ-NF-001, CR-02) and does not leak the raw Go error string.
func TestProblemErrorHandler_InvalidUUIDPathParam(t *testing.T) {
	mux := http.NewServeMux()
	// nil DB is safe: the UUID binding failure fires before the handler body runs.
	api.HandlerWithOptions(&api.Server{}, api.StdHTTPServerOptions{
		BaseRouter:       mux,
		ErrorHandlerFunc: api.ProblemErrorHandler,
	})

	req := httptest.NewRequest(http.MethodDelete, "/v1/children/not-a-uuid", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	// Save body string before any reads so we can inspect it after JSON decode.
	bodyStr := rec.Body.String()

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected status 400, got %d", rec.Code)
	}

	ct := rec.Header().Get("Content-Type")
	if ct != "application/problem+json" {
		t.Errorf("expected Content-Type application/problem+json, got %q", ct)
	}

	var body map[string]any
	if err := json.Unmarshal([]byte(bodyStr), &body); err != nil {
		t.Fatalf("failed to decode response body: %v — body was: %s", err, bodyStr)
	}

	statusField, ok := body["status"]
	if !ok {
		t.Fatalf("expected JSON body to have 'status' field; got: %s", bodyStr)
	}
	// JSON numbers unmarshal as float64 when decoding into map[string]any.
	if int(statusField.(float64)) != http.StatusBadRequest {
		t.Errorf("expected JSON status 400, got %v", statusField)
	}

	// Ensure the raw Go error string (from InvalidParamFormatError.Error()) is not leaked.
	if strings.Contains(bodyStr, "Invalid format for parameter") {
		t.Errorf("response must not leak raw Go error string; got: %s", bodyStr)
	}
}
