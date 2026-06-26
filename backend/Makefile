.PHONY: generate migrate-up migrate-down

generate:
	cd .. && go run github.com/oapi-codegen/oapi-codegen/v2/cmd/oapi-codegen@v2.7.1 \
		-config api/codegen.yaml \
		api/openapi.yaml

migrate-up:
	go run github.com/pressly/goose/v3/cmd/goose@v3.27.1 \
		-dir db/migrations postgres "$(DATABASE_URL)" up

migrate-down:
	go run github.com/pressly/goose/v3/cmd/goose@v3.27.1 \
		-dir db/migrations postgres "$(DATABASE_URL)" down
