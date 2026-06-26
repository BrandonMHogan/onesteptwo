package api

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"time"

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

// createChildRequest mirrors the D-07 payload shape for JSON decoding.
// The generated CreateChildRequest from generated.go is used for the OpenAPI model;
// this internal struct decodes the request body within the handler.
type createChildRequest struct {
	Nickname   string `json:"nickname"`
	BirthMonth int    `json:"birth_month"`
	BirthYear  int    `json:"birth_year"`
	Consent    struct {
		AppVersion         string `json:"app_version"`
		ConsentTextVersion string `json:"consent_text_version"`
	} `json:"consent"`
}

// PostV1Children implements POST /v1/children — consent-gate handler.
// Atomically inserts a consent_events row (capturing its generated id) then a children row
// referencing it (D-07, REQ-009, REQ-010, REQ-C-001). No IP address is ever read or written
// (REQ-C-009, T-2-03).
//
// Auth paths return before touching s.DB, so a nil DB is safe for unit tests
// of the validation/auth code paths.
func (s *Server) PostV1Children(w http.ResponseWriter, r *http.Request) {
	// TODO Phase 3: replace with JWT claim extraction
	clerkUserID := r.Header.Get("X-Clerk-User-Id")
	clerkOrgID := r.Header.Get("X-Clerk-Org-Id")
	if clerkUserID == "" || clerkOrgID == "" {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing identity headers")
		return
	}

	// Decode JSON body — no body → EOF → 400.
	var req createChildRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "invalid or missing JSON body")
		return
	}

	// Input validation (Security V5).
	switch {
	case req.Nickname == "":
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "nickname must not be empty")
		return
	case len(req.Nickname) > 100:
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "nickname must not exceed 100 characters")
		return
	case req.BirthMonth < 1 || req.BirthMonth > 12:
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "birth_month must be between 1 and 12")
		return
	case req.BirthYear < 2000 || req.BirthYear > time.Now().Year()+1:
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "birth_year is out of valid range")
		return
	case req.Consent.AppVersion == "":
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "consent.app_version must not be empty")
		return
	case req.Consent.ConsentTextVersion == "":
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "consent.consent_text_version must not be empty")
		return
	}

	// Atomic transaction: consent_events INSERT (capture id) → children INSERT (D-07).
	// NOTE: The handler only reaches this point with a valid DB. nil DB panics here, which
	// is acceptable because unit tests exercise only the auth/validation paths above.
	tx, err := s.DB.BeginTx(r.Context(), nil)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not begin transaction")
		return
	}
	defer tx.Rollback() //nolint:errcheck // safe no-op after Commit

	// Step 1: INSERT consent_events row (D-06 fields only — no IP, no PII beyond clerk_user_id).
	// REQ-C-009: no IP source is read; column list is exactly the four D-06 fields.
	var consentEventID string
	err = tx.QueryRowContext(r.Context(),
		`INSERT INTO consent_events (clerk_user_id, consented_at, app_version, consent_text_version)
		 VALUES ($1, NOW(), $2, $3)
		 RETURNING id`,
		clerkUserID,
		req.Consent.AppVersion,
		req.Consent.ConsentTextVersion,
	).Scan(&consentEventID)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not record consent")
		return
	}

	// Step 2: INSERT children row referencing the just-created consent row.
	var childID string
	err = tx.QueryRowContext(r.Context(),
		`INSERT INTO children (clerk_org_id, nickname, birth_month, birth_year, consent_event_id)
		 VALUES ($1, $2, $3, $4, $5)
		 RETURNING id`,
		clerkOrgID,
		req.Nickname,
		req.BirthMonth,
		req.BirthYear,
		consentEventID,
	).Scan(&childID)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not create child profile")
		return
	}

	if err = tx.Commit(); err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not commit transaction")
		return
	}

	// 201 Created with ChildResponse body.
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"id":          childID,
		"clerk_org_id": clerkOrgID,
		"nickname":    req.Nickname,
		"birth_month": req.BirthMonth,
		"birth_year":  req.BirthYear,
	})
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
