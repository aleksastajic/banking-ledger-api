# banking-ledger-api

Ledger API implementing double-entry accounting primitives targeting **Java 17** (build requires Java 17+), using **Spring Boot 3**, **PostgreSQL**, **Flyway**, and **JPA/Hibernate**.

TL;DR
A Java 17 Spring Boot service demonstrating double-entry accounting primitives with idempotent journal writes, DB‑level invariants, and an integrity hash chain.

## Highlights
- Idempotent writes: POST /journal-entries uses (X-Client-Id, Idempotency-Key) semantics and returns the original response on retries.
- Strong invariants: append-only journal/postings, deferred-trigger double-entry checks, and an integrity hash chain to detect tampering.
- Robust integration tests: Testcontainers-based ITs for idempotency, concurrency, reversals, and integrity checks.

## What I learned
- Designing idempotency with database-backed records and hashing to ensure safe retries.
- Using DB triggers and deferred checks to move critical integrity rules into the database.
- Choosing testing strategies (Testcontainers + CI) to validate concurrency and tampering scenarios.

## Status
The service is under active development. Current capabilities:
- Spring Boot application bootstrap (Maven + Maven Wrapper)
- PostgreSQL for local development via Docker Compose
- Flyway migrations (schema + constraints/triggers)
- Actuator health endpoint
- RFC 7807-style Problem Details error responses
- Accounts API: create/get + derived balances
- Journal API: create (idempotent), get, list postings, create reversal
- Ledger invariants enforced in DB + service (append-only, double-entry per currency, non-negative CUSTOMER)

## Tech stack
- Java 17
- Spring Boot 3.x (Maven)
- PostgreSQL (Docker Compose for local dev)
- Flyway for schema migrations
- Spring Data JPA/Hibernate
- OpenAPI/Swagger via springdoc
- Testing: JUnit 5 + MockMvc web-slice tests + integration tests against PostgreSQL (external via Docker Compose by default; optional Testcontainers)

## Project setup

### Build
```bash
./mvnw -q -DskipTests package
```

### Test
```bash
./mvnw test
```

### Integration tests (PostgreSQL)
Integration tests (`*IT`) require a PostgreSQL database. There are two supported runs:

- External Postgres (recommended for iterative local development): the helper will start Postgres via Docker Compose, wait for readiness, run ITs and store logs under `logs/`.

	```bash
	./scripts/run_with_external_postgres.sh
	```

- Testcontainers (recommended for CI and reproducible environments): runs a real PostgreSQL container per test lifecycle using Testcontainers. The convenience wrapper exports Docker API version when available and captures any Testcontainers-created container logs into `logs/`.

	Run directly:

	```bash
	./mvnw -Pit -Dit.useTestcontainers=true verify
	```

	Or use the wrapper to force Testcontainers mode:

	```bash
	USE_TESTCONTAINERS=1 ./scripts/run-integration.sh
	```

Notes:
- Wrapper output and Maven logs are saved under `logs/` with timestamps.
- When Testcontainers starts containers, their docker logs are saved into `logs/` with filenames like `testcontainer_<ts>_<name>_<id>.log`.
- `./mvnw test` remains Docker-free; `./mvnw -Pit verify` runs unit tests and `*IT` integration tests.

### Run
The application expects a PostgreSQL database (local Docker Compose is the default).

Note: `docker compose up` in this repository starts only the `db` (Postgres) service. To access the API and Swagger UI you must also run the Spring Boot application locally (command below) or add an `app` service to `docker-compose.yml`.

## Quickstart (local dev)

### Prerequisites
- Java **17** (`java -version`)
- Docker + Docker Compose

If your system default Java is newer (e.g. Java 21), set `JAVA_HOME` to a Java 17 installation when building/running this project.

### 1) Start Postgres (Docker)
We map container port `5432` to host port `5433` by default to avoid conflicts with a local Postgres. Override with `POSTGRES_HOST_PORT` if needed.

```bash
docker compose up -d
```

To reset the local database (drops the Docker volume):

```bash
docker compose down -v
docker compose up -d
```

This project targets Java 17 bytecode (`--release 17`) but allows building/running with newer JDKs (e.g. Java 21).

### 2) Run the app
```bash
./mvnw -q -DskipTests spring-boot:run
```

Or build and run the fat jar:

```bash
./mvnw -DskipTests package
java -jar target/*-boot.jar --server.port=8080 --spring.datasource.url=jdbc:postgresql://localhost:5433/ledger
```

### 3) Verify health
```bash
curl -s http://localhost:8080/actuator/health
```
Expected:

```json
{"status":"UP"}
```

## Configuration
Configuration lives in [src/main/resources/application.yml](src/main/resources/application.yml). Values can be overridden via environment variables.

### Database
Defaults (override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`):
- URL: `jdbc:postgresql://localhost:5433/ledger?sslmode=disable`
- Username: `postgres`
- Password: `postgres`

## API docs
When the app is running:
- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## API overview

### Accounts
- `POST /accounts`
- `GET /accounts/{id}`
- `GET /accounts/{id}/balances`

### Journal entries
Headers required for write endpoints:
- `X-Client-Id: <uuid>`
- `Idempotency-Key: <string>`

Idempotency semantics:
Note: OpenAPI docs are kept up-to-date in controllers.
- Retrying the same request (same client id + idempotency key + same payload) returns the original `201` response.
- Reusing the same idempotency key with a different payload returns `409`.
- For reversals: if an entry was already reversed, subsequent reversal attempts return `409`.

- `POST /journal-entries`
- `GET /journal-entries/{id}` (includes `reversesJournalEntryId` when applicable)
- `GET /journal-entries/{id}/postings`
- `POST /journal-entries/{id}/reversal`

Example: create a journal entry (idempotent)
```bash
curl -sS -X POST http://localhost:8080/journal-entries \
	-H 'Content-Type: application/json' \
	-H 'X-Client-Id: 11111111-1111-1111-1111-111111111111' \
	-H 'Idempotency-Key: idem-demo-1' \
	-d '{
		"description": "payment",
		"postings": [
			{"accountId": "<customer-account-uuid>", "currency": "EUR", "amount": "-10.0000"},
			{"accountId": "<internal-account-uuid>", "currency": "EUR", "amount": "10.0000"}
		]
	}'
```

## Flyway migrations
Migrations are located at:
- [src/main/resources/db/migration/V1__init.sql](src/main/resources/db/migration/V1__init.sql)
- [src/main/resources/db/migration/V2__constraints_and_indexes.sql](src/main/resources/db/migration/V2__constraints_and_indexes.sql)
- [src/main/resources/db/migration/V3__journal_entry_reversals.sql](src/main/resources/db/migration/V3__journal_entry_reversals.sql)

## Development notes
- **Append-only** tables: `journal_entries` and `postings` are protected by DB triggers (no UPDATE/DELETE).
- **Double-entry invariant** is enforced by a deferred trigger (validated at transaction commit).
- **Reversals** are explicit: `journal_entries.reverses_journal_entry_id` links a reversal entry to its original, and is included in the canonical hash/idempotency.

## Next steps
Potential next improvements:
- Make Testcontainers mode reliable on all dev machines
- Tighten/extend business-rule integration tests (edge cases)
- OpenAPI enrichment (examples, schemas, error responses)

## CI
GitHub Actions workflow is defined in [.github/workflows/ci.yml](.github/workflows/ci.yml).

What CI does now:
- Unit tests: `./mvnw test`
- Integration tests: runs with Testcontainers enabled (`-Dit.useTestcontainers=true`) and uploads the `logs/` directory as a build artifact for inspection on failure.

If you need CI to run against an externally provided Postgres instance instead, update the workflow accordingly in `.github/workflows/ci.yml`.
