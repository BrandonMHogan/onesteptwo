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
	// enforces its own 401/403 rejection.
	authorizedParty := os.Getenv("CLERK_AUTHORIZED_PARTY")
	if authorizedParty == "" {
		// WR-05: When unset, AuthorizedPartyMatches("") is a no-op and the azp claim in
		// incoming JWTs is never verified. Any token signed by this Clerk instance passes.
		// Set CLERK_AUTHORIZED_PARTY to the mobile app bundle ID before production launch.
		log.Println("WARNING: CLERK_AUTHORIZED_PARTY is not set — azp claim unverified; set to mobile bundle ID for production")
	}
	authMiddleware := clerkhttp.WithHeaderAuthorization(
		clerkhttp.AuthorizedPartyMatches(authorizedParty),
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
