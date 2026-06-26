package api

import (
	"encoding/json"
	"net/http"
)

// ProblemDetail is the RFC 7807 error response format.
// The `detail` field is for developers only — never shown to end users.
type ProblemDetail struct {
	Type   string `json:"type"`
	Title  string `json:"title"`
	Status int    `json:"status"`
	Detail string `json:"detail"`
}

// WriteProblem writes an RFC 7807 problem response.
// All Go handlers must use this function for error responses (REQ-NF-001).
// The detail parameter is for developers only — callers must never pass err.Error() or stack traces.
func WriteProblem(w http.ResponseWriter, status int, typ, title, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(ProblemDetail{
		Type:   typ,
		Title:  title,
		Status: status,
		Detail: detail,
	})
}
