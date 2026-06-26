package api_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

// TestNewClerkClient_EmptyKeyListError verifies that ListOrgMemberUserIDs returns
// an error when the Clerk client was created with an empty secret key.
func TestNewClerkClient_EmptyKeyListError(t *testing.T) {
	c := api.NewClerkClient("")
	_, err := c.ListOrgMemberUserIDs(context.Background(), "org_123")
	if err == nil {
		t.Error("expected error for empty secret key, got nil")
	}
}

// TestNewClerkClient_EmptyKeyDeleteError verifies that DeleteOrganization returns
// an error when the Clerk client was created with an empty secret key.
func TestNewClerkClient_EmptyKeyDeleteError(t *testing.T) {
	c := api.NewClerkClient("")
	err := c.DeleteOrganization(context.Background(), "org_123")
	if err == nil {
		t.Error("expected error for empty secret key, got nil")
	}
}

// TestListOrgMemberUserIDs_Non2xxReturnsError verifies that a non-2xx HTTP response
// from the Clerk memberships endpoint causes ListOrgMemberUserIDs to return an error.
func TestListOrgMemberUserIDs_Non2xxReturnsError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	t.Setenv("CLERK_API_URL", srv.URL)
	c := api.NewClerkClient("sk_test_fake")
	_, err := c.ListOrgMemberUserIDs(context.Background(), "org_123")
	if err == nil {
		t.Error("expected error for non-2xx response, got nil")
	}
}

// TestListOrgMemberUserIDs_ParsesUserIDs verifies that ListOrgMemberUserIDs
// correctly parses the data[].public_user_data.user_id fields from the Clerk response.
func TestListOrgMemberUserIDs_ParsesUserIDs(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"data": []map[string]any{
				{"public_user_data": map[string]any{"user_id": "user_abc"}},
				{"public_user_data": map[string]any{"user_id": "user_def"}},
			},
		})
	}))
	defer srv.Close()

	t.Setenv("CLERK_API_URL", srv.URL)
	c := api.NewClerkClient("sk_test_fake")
	ids, err := c.ListOrgMemberUserIDs(context.Background(), "org_123")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(ids) != 2 {
		t.Errorf("expected 2 user IDs, got %d: %v", len(ids), ids)
	}
}

// TestDeleteOrganization_404IsSuccess verifies that a 404 response from the Clerk
// organization deletion endpoint is treated as success (org is already gone).
func TestDeleteOrganization_404IsSuccess(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer srv.Close()

	t.Setenv("CLERK_API_URL", srv.URL)
	c := api.NewClerkClient("sk_test_fake")
	err := c.DeleteOrganization(context.Background(), "org_123")
	if err != nil {
		t.Errorf("expected 404 to be treated as success, got error: %v", err)
	}
}

// TestDeleteOrganization_Non2xxReturnsError verifies that a non-2xx, non-404 response
// from the Clerk organization deletion endpoint causes DeleteOrganization to return an error.
func TestDeleteOrganization_Non2xxReturnsError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	t.Setenv("CLERK_API_URL", srv.URL)
	c := api.NewClerkClient("sk_test_fake")
	err := c.DeleteOrganization(context.Background(), "org_123")
	if err == nil {
		t.Error("expected error for non-2xx non-404 response, got nil")
	}
}

// TestListOrgMemberUserIDs_SetsAuthHeader verifies that the Authorization header
// is sent with the correct Bearer token format.
func TestListOrgMemberUserIDs_SetsAuthHeader(t *testing.T) {
	var gotAuth string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{"data": []any{}})
	}))
	defer srv.Close()

	t.Setenv("CLERK_API_URL", srv.URL)
	c := api.NewClerkClient("sk_test_mysecret")
	_, _ = c.ListOrgMemberUserIDs(context.Background(), "org_123")
	if gotAuth != "Bearer sk_test_mysecret" {
		t.Errorf("expected Authorization header 'Bearer sk_test_mysecret', got %q", gotAuth)
	}
}
