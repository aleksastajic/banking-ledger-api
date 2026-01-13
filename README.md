# banking-ledger-api

Ledger API implementing double-entry accounting primitives using **Java 17**, **Spring Boot 3**, **PostgreSQL**, **Flyway**, and **JPA/Hibernate**.

## Status
The service is under active development. Current capabilities:
- Spring Boot application bootstrap (Maven + Maven Wrapper)
- PostgreSQL for local development via Docker Compose
- Flyway migrations (schema + constraints/triggers)
- Actuator health endpoint

## Tech stack
- Java 17
- Spring Boot 3.x (Maven)
- PostgreSQL (Docker Compose for local dev)
- Flyway for schema migrations
- Spring Data JPA/Hibernate
- OpenAPI/Swagger via springdoc
- Testing: JUnit 5 + Testcontainers (Postgres)

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

## Flyway migrations
Migrations are located at:
- [src/main/resources/db/migration/V1__init.sql](src/main/resources/db/migration/V1__init.sql)
- [src/main/resources/db/migration/V2__constraints_and_indexes.sql](src/main/resources/db/migration/V2__constraints_and_indexes.sql)

## Development notes
- **Append-only** tables: `journal_entries` and `postings` are protected by DB triggers (no UPDATE/DELETE).
- **Double-entry invariant** is enforced by a deferred trigger (validated at transaction commit).

## Next steps
Upcoming commits add:
- RFC 7807 Problem Details error responses
- Accounts + balances + postings read endpoints
- Journal entry creation (idempotency, locking, negative-balance rule)
- Integrity hash chain verification
- Integration tests (including concurrency and tamper detection)
