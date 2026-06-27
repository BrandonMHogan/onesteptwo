package main

import (
	"database/sql"
	"log"
	"net/http"
	"os"
	"time"

	_ "github.com/lib/pq"

	clerk "github.com/clerk/clerk-sdk-go/v2"
	clerkhttp "github.com/clerk/clerk-sdk-go/v2/http"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)

	// Set the Clerk secret key for JWKS-backed JWT verification.
	clerk.SetKey(os.Getenv("CLERK_SECRET_KEY"))

	// WithHeaderAuthorization (not RequireHeaderAuthorization) so /healthz — which carries
	// no Authorization header — passes through unauthenticated. Each protected handler
	// enforces its own 401/403 rejection. (REQ-026: CLERK_AUTHORIZED_PARTY may be unset
	// until plan 03-03 empirically discovers the azp value from a native-app JWT; when the
	// env var is empty, AuthorizedPartyMatches("") is a no-op that does not reject.)
	authMiddleware := clerkhttp.WithHeaderAuthorization(
		clerkhttp.AuthorizedPartyMatches(os.Getenv("CLERK_AUTHORIZED_PARTY")),
		clerkhttp.Leeway(5*time.Second),
	)

	srv := &api.Server{DB: db}
	srv.Clerk = api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))
	mux := http.NewServeMux()
	api.HandlerWithOptions(srv, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: api.ProblemErrorHandler})

	log.Printf("starting server on :%s", port)
	if err := http.ListenAndServe(":"+port, authMiddleware(mux)); err != nil {
		log.Fatal(err)
	}
}
