package main

import (
	"log"
	"net/http"
	"os"

	"github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	srv := &api.Server{}
	mux := http.NewServeMux()
	api.HandlerFromMux(srv, mux)

	log.Printf("starting server on :%s", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatal(err)
	}
}
