# banking-ledger-api

Ledger API implementing double-entry accounting primitives using **Java 17**, **Spring Boot 3**, **PostgreSQL**, **Flyway**, and **JPA/Hibernate**.

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
- Testing: JUnit 5 + MockMvc web-slice tests (Testcontainers planned)

## Project setup

### Build
```bash
./mvnw -q -DskipTests package
```

### Test
```bash
./mvnw test
```

### Run
The application expects a PostgreSQL database (local Docker Compose is the default).

## Quickstart (local dev)

### Prerequisites
- Java **17** (`java -version`)
- Docker + Docker Compose

If your system default Java is newer (e.g. Java 21), set `JAVA_HOME` to a Java 17 installation when building/running this project.

### 1) Start Postgres (Docker)
We map container port `5432` to host port `5433` by default to avoid conflicts with a local Postgres.

```bash
docker compose up -d
```

To reset the local database (drops the Docker volume):
```bash
docker compose down -v
docker compose up -d
```

### 2) Run the app
```bash
./mvnw -q -DskipTests spring-boot:run
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

- `POST /journal-entries`
- `GET /journal-entries/{id}` (includes `reversesJournalEntryId` when applicable)
- `GET /journal-entries/{id}/postings`
- `POST /journal-entries/{id}/reversal`

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
Upcoming commits add:
- Integrity hash chain verification
- Integration tests via Testcontainers (including concurrency and tamper detection)
- OpenAPI enrichment (examples, schemas, error responses)
