package api

import (
	"database/sql"
	"encoding/json"
	"log"
	"net/http"
	"time"

	clerk "github.com/clerk/clerk-sdk-go/v2"
	"github.com/google/uuid"
	"github.com/lib/pq"
	openapi_types "github.com/oapi-codegen/runtime/types"
)

// Server implements the generated ServerInterface for all API handlers.
type Server struct {
	DB    *sql.DB
	Clerk ClerkOrgClient
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
	// Extract JWT claims populated by WithHeaderAuthorization middleware.
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	// Require an active organization in the session (REQ-027).
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}
	// Require admin role for write operations (REQ-016). CRITICAL: prefix must be "org:" not "admin".
	if !claims.HasRole("org:admin") {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "admin role required")
		return
	}
	clerkUserID := claims.Subject
	clerkOrgID := claims.ActiveOrganizationID

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
		"id":           childID,
		"clerk_org_id": clerkOrgID,
		"nickname":     req.Nickname,
		"birth_month":  req.BirthMonth,
		"birth_year":   req.BirthYear,
	})
}

// childListResponse mirrors the generated ChildResponse schema for JSON encoding.
type childListResponse struct {
	ID         string `json:"id"`
	ClerkOrgID string `json:"clerk_org_id"`
	Nickname   string `json:"nickname"`
	BirthMonth int    `json:"birth_month"`
	BirthYear  int    `json:"birth_year"`
}

// GetV1Children implements GET /v1/children — lists the caller's active-org children.
// Any signed-in member of the active org may list children (no admin gate, unlike
// POST/DELETE) — both org:admin and org:caregiver need this to drive the app's
// onboarding-vs-main-shell routing decision (REQ-036's "returning caregiver skips the wizard").
//
// Auth paths return before touching s.DB, so a nil DB is safe for unit tests
// of the validation/auth code paths.
func (s *Server) GetV1Children(w http.ResponseWriter, r *http.Request) {
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}

	rows, err := s.DB.QueryContext(r.Context(),
		`SELECT id, clerk_org_id, nickname, birth_month, birth_year
		 FROM children
		 WHERE clerk_org_id = $1
		 ORDER BY created_at ASC`,
		claims.ActiveOrganizationID,
	)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not list children")
		return
	}
	defer rows.Close() //nolint:errcheck // safe no-op

	children := make([]childListResponse, 0)
	for rows.Next() {
		var c childListResponse
		if err := rows.Scan(&c.ID, &c.ClerkOrgID, &c.Nickname, &c.BirthMonth, &c.BirthYear); err != nil {
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not read children")
			return
		}
		children = append(children, c)
	}
	if err := rows.Err(); err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not list children")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(children)
}

// DeleteV1ChildrenId implements DELETE /v1/children/{id} — child erasure cascade.
// Hard-deletes a child's data in FK-safe order (REQ-011, REQ-013, REQ-014, REQ-C-002, REQ-C-003, REQ-C-004).
// Each call first purges erasure_audit rows older than 90 days (D-12, REQ-C-008).
//
// Auth path returns before DB use — nil DB is safe for unit tests of the auth code path.
func (s *Server) DeleteV1ChildrenId(w http.ResponseWriter, r *http.Request, id openapi_types.UUID) {
	// Extract JWT claims populated by WithHeaderAuthorization middleware.
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	// Require an active organization in the session (REQ-027).
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}
	// Require admin role for write operations (REQ-016). CRITICAL: prefix must be "org:" not "admin".
	if !claims.HasRole("org:admin") {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "admin role required")
		return
	}
	clerkUserID := claims.Subject

	ctx := r.Context()
	childID := id.String()

	tx, err := s.DB.BeginTx(ctx, nil)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not begin transaction")
		return
	}
	defer tx.Rollback() //nolint:errcheck // safe no-op after Commit

	// D-12: purge erasure_audit rows older than 90 days (sweep-on-request, REQ-C-008).
	_, _ = tx.ExecContext(ctx, `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`)

	// Capture consent_event_id and clerk_org_id BEFORE deleting the children row.
	// CRITICAL (RESEARCH.md Pitfall 1): D-05 FK direction means children.consent_event_id REFERENCES consent_events(id).
	// Deleting consent_events BEFORE children throws a FK violation — children must be deleted first.
	// SELECT must come here, before any DELETE, so the values are preserved.
	var consentEventID, childClerkOrgID string
	err = tx.QueryRowContext(ctx,
		`SELECT consent_event_id, clerk_org_id FROM children WHERE id = $1`, childID,
	).Scan(&consentEventID, &childClerkOrgID)
	if err == sql.ErrNoRows {
		WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
		return
	}
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not look up child")
		return
	}
	// REQ-015 / T-2-02: child must belong to requester's active org (IDOR gap closure).
	if claims.ActiveOrganizationID != childClerkOrgID {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "access denied")
		return
	}

	// FK-safe deletion order (D-05 FK: children → consent_events).
	// children must be deleted BEFORE consent_events to release the FK reference (Pitfall 1).
	r1, err := tx.ExecContext(ctx, `DELETE FROM potty_events WHERE child_id = $1`, childID)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete potty events")
		return
	}
	r2, err := tx.ExecContext(ctx, `DELETE FROM notification_preferences WHERE child_id = $1`, childID)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete notification preferences")
		return
	}
	_, err = tx.ExecContext(ctx, `DELETE FROM children WHERE id = $1`, childID) // FK released here
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete child")
		return
	}
	r3, err := tx.ExecContext(ctx, `DELETE FROM consent_events WHERE id = $1`, consentEventID) // now safe
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete consent event")
		return
	}

	// Audit row written inside the same tx — no deletion without an audit row (T-2-04).
	_, err = tx.ExecContext(ctx,
		`INSERT INTO erasure_audit (clerk_user_id, action, target_id, target_type, deleted_at)
		 VALUES ($1, 'child_deletion', $2, 'child', NOW())`,
		clerkUserID, childID,
	)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not write audit record")
		return
	}

	if err = tx.Commit(); err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not commit deletion")
		return
	}

	eventsDeleted, _ := r1.RowsAffected()
	prefsDeleted, _ := r2.RowsAffected()
	consentDeleted, _ := r3.RowsAffected()

	// D-10: structured erasure confirmation JSON (not 204 No Content per REQ-013/REQ-014).
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"deleted_children":                 1,
		"deleted_events":                   eventsDeleted,
		"deleted_consent_events":           consentDeleted,
		"deleted_notification_preferences": prefsDeleted,
		"deleted_device_tokens":            0, // device tokens are removed only on account deletion
		"requested_by":                     clerkUserID,
		"requested_at":                     time.Now().UTC().Format(time.RFC3339),
	})
}

// DeleteV1Account implements DELETE /v1/account — full account erasure cascade.
// Hard-deletes every child in the requesting org and all their dependents, in FK-safe order,
// then deletes all org members' device_tokens and the Clerk Organization after commit
// (REQ-012, REQ-013, REQ-014, REQ-C-002, REQ-C-003, REQ-C-004, REQ-C-008).
// Each call first purges erasure_audit rows older than 90 days (D-12, REQ-C-008).
//
// Auth path returns before any external call — nil DB and nil Clerk are safe for auth tests.
// Member fetch happens before BeginTx — a fetch failure returns 500 without opening a tx.
func (s *Server) DeleteV1Account(w http.ResponseWriter, r *http.Request) {
	// Extract JWT claims populated by WithHeaderAuthorization middleware.
	claims, ok := clerk.SessionClaimsFromContext(r.Context())
	if !ok || claims == nil {
		WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing or invalid session token")
		return
	}
	// Require an active organization in the session (REQ-027).
	if claims.ActiveOrganizationID == "" {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "no active organization in session")
		return
	}
	// Require admin role for write operations (REQ-016). CRITICAL: prefix must be "org:" not "admin".
	if !claims.HasRole("org:admin") {
		WriteProblem(w, http.StatusForbidden, "about:blank", "Forbidden", "admin role required")
		return
	}
	clerkUserID := claims.Subject
	clerkOrgID := claims.ActiveOrganizationID

	ctx := r.Context()

	// Fetch all org member user IDs from Clerk BEFORE opening a transaction (REQ-012).
	// If the fetch fails, no data is deleted — no BeginTx has been called at this point.
	// The response body from Clerk is never forwarded to the caller (T-2-15).
	memberIDs, err := s.Clerk.ListOrgMemberUserIDs(ctx, clerkOrgID)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not list organization members")
		return
	}
	// Append the requesting user defensively — no dedup needed for SQL ANY($1).
	memberIDs = append(memberIDs, clerkUserID)

	tx, err := s.DB.BeginTx(ctx, nil)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not begin transaction")
		return
	}
	defer tx.Rollback() //nolint:errcheck // safe no-op after Commit

	// D-12: purge erasure_audit rows older than 90 days (sweep-on-request, REQ-C-008).
	_, _ = tx.ExecContext(ctx, `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`)

	// Enumerate all children in the org for FK-safe cascade deletion.
	rows, err := tx.QueryContext(ctx, `SELECT id, consent_event_id FROM children WHERE clerk_org_id = $1`, clerkOrgID)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not query children")
		return
	}
	type childRow struct {
		id             string
		consentEventID string
	}
	var children []childRow
	for rows.Next() {
		var c childRow
		if err := rows.Scan(&c.id, &c.consentEventID); err != nil {
			rows.Close() //nolint:errcheck
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not scan children")
			return
		}
		children = append(children, c)
	}
	rows.Close() //nolint:errcheck
	if err := rows.Err(); err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not enumerate children")
		return
	}

	var totalEvents, totalConsent, totalPrefs, totalChildren int64
	for _, c := range children {
		r1, err := tx.ExecContext(ctx, `DELETE FROM potty_events WHERE child_id = $1`, c.id)
		if err != nil {
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete potty events")
			return
		}
		r2, err := tx.ExecContext(ctx, `DELETE FROM notification_preferences WHERE child_id = $1`, c.id)
		if err != nil {
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete notification preferences")
			return
		}
		_, err = tx.ExecContext(ctx, `DELETE FROM children WHERE id = $1`, c.id) // FK released here
		if err != nil {
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete child")
			return
		}
		r3, err := tx.ExecContext(ctx, `DELETE FROM consent_events WHERE id = $1`, c.consentEventID) // now safe
		if err != nil {
			WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete consent event")
			return
		}
		n1, _ := r1.RowsAffected()
		n2, _ := r2.RowsAffected()
		n3, _ := r3.RowsAffected()
		totalEvents += n1
		totalPrefs += n2
		totalConsent += n3
		totalChildren++
	}

	// Delete device_tokens for ALL org members fetched from Clerk (REQ-012).
	// memberIDs was populated pre-tx from the Clerk org member list, with the requesting user
	// appended defensively. This replaces the prior single-user delete with an org-wide sweep
	// that satisfies GDPR/COPPA erasure for multi-caregiver families.
	r4, err := tx.ExecContext(ctx, `DELETE FROM device_tokens WHERE clerk_user_id = ANY($1)`, pq.Array(memberIDs))
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not delete device tokens")
		return
	}
	tokensDeleted, _ := r4.RowsAffected()

	// Compute a deterministic UUIDv5 of the clerk_org_id for the erasure_audit target_id.
	// Clerk org ids (e.g. "org_...") are not UUIDs; erasure_audit.target_id is UUID NOT NULL.
	// A deterministic UUID means the same org always produces the same target_id — recompute
	// uuid.NewSHA1(uuid.NameSpaceURL, []byte("onesteptwo:org:"+clerkOrgID)) at any time to
	// locate the org's audit rows. (REQ-C-008 traceability)
	orgTargetID := uuid.NewSHA1(uuid.NameSpaceURL, []byte("onesteptwo:org:"+clerkOrgID)).String()

	// Audit row written inside the same tx — no deletion without an audit row (T-2-04).
	_, err = tx.ExecContext(ctx,
		`INSERT INTO erasure_audit (clerk_user_id, action, target_id, target_type, deleted_at)
		 VALUES ($1, 'account_deletion', $2, 'family', NOW())`,
		clerkUserID, orgTargetID,
	)
	if err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not write audit record")
		return
	}

	// Commit DB work BEFORE any external call (RESEARCH.md Pitfall 2 — Clerk-API-after-commit).
	if err = tx.Commit(); err != nil {
		WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not commit deletion")
		return
	}

	// Delete the Clerk Organization after the DB transaction commits (Pitfall 2 ordering, REQ-012).
	// A Clerk failure is logged but does not fail the request — DB data is already erased and the
	// lingering Clerk org holds only PII that Clerk owns. The request still returns success.
	if err := s.Clerk.DeleteOrganization(ctx, clerkOrgID); err != nil {
		log.Printf("clerk org deletion failed for %s: %v", clerkOrgID, err)
	}

	// D-10: structured erasure confirmation JSON (not 204 No Content per REQ-013/REQ-014).
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"deleted_children":                 totalChildren,
		"deleted_events":                   totalEvents,
		"deleted_consent_events":           totalConsent,
		"deleted_notification_preferences": totalPrefs,
		"deleted_device_tokens":            tokensDeleted,
		"requested_by":                     clerkUserID,
		"requested_at":                     time.Now().UTC().Format(time.RFC3339),
	})
}
