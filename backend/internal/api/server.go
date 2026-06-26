package api

import "net/http"

// Server implements the generated ServerInterface for all API handlers.
type Server struct{}

// GetHealthz satisfies the oapi-codegen ServerInterface for GET /healthz.
// Returns 200 OK with no body and requires no authentication (REQ-001).
func (s *Server) GetHealthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
}
