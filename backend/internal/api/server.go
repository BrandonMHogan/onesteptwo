package api

import (
	"database/sql"
	"net/http"

	openapi_types "github.com/oapi-codegen/runtime/types"
)

// Server implements the generated ServerInterface for all API handlers.
type Server struct {
	DB *sql.DB
}

// GetHealthz satisfies the oapi-codegen ServerInterface for GET /healthz.
// Returns 200 OK with no body and requires no authentication (REQ-001).
func (s *Server) GetHealthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
}

// PostV1Children implements POST /v1/children — consent-gate handler.
// Full implementation added in Task 2 (TDD) of plan 02-02.
// Auth and validation guard the DB path; nil DB is safe for unit tests of those paths.
func (s *Server) PostV1Children(w http.ResponseWriter, r *http.Request) {
	// TODO 02-02-T2: replaced by full TDD implementation in Task 2 of this plan
	WriteProblem(w, http.StatusNotImplemented, "about:blank", "Not Implemented", "implemented in task 2")
}

// DeleteV1ChildrenId implements DELETE /v1/children/{id} — child erasure cascade.
// TODO 02-03: full erasure cascade implemented in plan 02-03.
func (s *Server) DeleteV1ChildrenId(w http.ResponseWriter, r *http.Request, id openapi_types.UUID) {
	// TODO 02-03: full erasure cascade implemented in plan 02-03
	WriteProblem(w, http.StatusNotImplemented, "about:blank", "Not Implemented", "implemented in plan 02-03")
}

// DeleteV1Account implements DELETE /v1/account — full account erasure cascade.
// TODO 02-03: full erasure cascade implemented in plan 02-03.
func (s *Server) DeleteV1Account(w http.ResponseWriter, r *http.Request) {
	// TODO 02-03: full erasure cascade implemented in plan 02-03
	WriteProblem(w, http.StatusNotImplemented, "about:blank", "Not Implemented", "implemented in plan 02-03")
}
