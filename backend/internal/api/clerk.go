package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"time"
)

// ClerkOrgClient defines the org-level Clerk API operations needed by handlers.
// The interface enables test stubs to be injected without a live Clerk account.
type ClerkOrgClient interface {
	// ListOrgMemberUserIDs returns the Clerk user IDs for all members of orgID.
	// Family-sized orgs never exceed 100 members, so a single page with limit=100 is
	// sufficient. If pagination is ever required, loop on the offset query parameter.
	ListOrgMemberUserIDs(ctx context.Context, orgID string) ([]string, error)

	// DeleteOrganization deletes the Clerk organization identified by orgID.
	// A 404 response is treated as success — the org is already gone.
	// Any other non-2xx response is returned as an error.
	DeleteOrganization(ctx context.Context, orgID string) error
}

// httpClerkClient implements ClerkOrgClient via the Clerk REST API.
type httpClerkClient struct {
	secretKey  string
	baseURL    string
	httpClient *http.Client
}

// NewClerkClient returns a ClerkOrgClient that targets the Clerk REST API.
// The base URL is read from the CLERK_API_URL environment variable; when empty,
// it defaults to "https://api.clerk.com". secretKey is used for Bearer authentication.
func NewClerkClient(secretKey string) ClerkOrgClient {
	baseURL := os.Getenv("CLERK_API_URL")
	if baseURL == "" {
		baseURL = "https://api.clerk.com"
	}
	return &httpClerkClient{
		secretKey:  secretKey,
		baseURL:    baseURL,
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}
}

// ListOrgMemberUserIDs GETs /v1/organizations/{orgID}/memberships?limit=100 and
// returns the user IDs found in data[].public_user_data.user_id.
// Returns an error when the secret key is empty or the API responds with non-2xx.
// The response body is never included in error values to prevent PII disclosure (T-2-15).
func (c *httpClerkClient) ListOrgMemberUserIDs(ctx context.Context, orgID string) ([]string, error) {
	if c.secretKey == "" {
		return nil, fmt.Errorf("clerk secret key not configured")
	}

	endpoint := c.baseURL + "/v1/organizations/" + url.PathEscape(orgID) + "/memberships?limit=100"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("clerk: failed to build memberships request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.secretKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("clerk: memberships request failed: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		// Response body is intentionally NOT included to avoid leaking PII to callers (T-2-15).
		return nil, fmt.Errorf("clerk: unexpected status %d listing org memberships", resp.StatusCode)
	}

	var body struct {
		Data []struct {
			PublicUserData struct {
				UserID string `json:"user_id"`
			} `json:"public_user_data"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, fmt.Errorf("clerk: failed to decode memberships response: %w", err)
	}

	ids := make([]string, 0, len(body.Data))
	for _, m := range body.Data {
		if m.PublicUserData.UserID != "" {
			ids = append(ids, m.PublicUserData.UserID)
		}
	}
	return ids, nil
}

// DeleteOrganization issues DELETE /v1/organizations/{orgID} against the Clerk API.
// A 404 response is treated as success (the org is already gone).
// Returns an error when the secret key is empty or the API responds with a non-2xx status
// other than 404. The response body is never included in errors (T-2-15).
func (c *httpClerkClient) DeleteOrganization(ctx context.Context, orgID string) error {
	if c.secretKey == "" {
		return fmt.Errorf("clerk secret key not configured")
	}

	endpoint := c.baseURL + "/v1/organizations/" + url.PathEscape(orgID)
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, endpoint, nil)
	if err != nil {
		return fmt.Errorf("clerk: failed to build delete request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.secretKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("clerk: delete organization request failed: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck

	// 404 means the org is already gone — treat as success.
	if resp.StatusCode == http.StatusNotFound {
		return nil
	}
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		// Response body is intentionally NOT included to avoid leaking PII to callers (T-2-15).
		return fmt.Errorf("clerk: unexpected status %d deleting organization", resp.StatusCode)
	}
	return nil
}
