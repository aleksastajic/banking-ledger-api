# Project Structure

This codebase uses **feature-first packages** (by API domain) with a small shared `ledger` package for cross-cutting persistence + hashing.

## Packages

- `com.aleksastajic.ledger.accounts`
  - Account API (`/accounts`) and account domain objects.
  - `AccountEntity` maps to the `accounts` table.

- `com.aleksastajic.ledger.journal`
  - Journal entry API (`/journal-entries`) and request/response DTOs.
  - Reversals are represented as a normal journal entry with `reversesJournalEntryId` set.

- `com.aleksastajic.ledger.balances`
  - Read-side helpers for balances derived from `postings`.

- `com.aleksastajic.ledger.ledger`
  - Write-side orchestration (`LedgerWriter`) and hashing.

- `com.aleksastajic.ledger.ledger.db`
  - JPA entities + repositories for the core ledger tables: `journal_entries`, `postings`, `ledger_chain_head`, `idempotency_requests`.

- `com.aleksastajic.ledger.config`
  - Infrastructure configuration like global error handling.
  - OpenAPI metadata (`OpenApiConfig`) and misc app config.

- `com.aleksastajic.ledger.api.problem`
  - API error DTOs (Problem Details).

- `com.aleksastajic.ledger.integrity`
  - Ledger integrity verification service + controller and report model (recomputes canonical entry hashes and validates the chain head).

## Conventions

- Prefer feature packages (`accounts`, `journal`, …) over global `controller/service/repository` folders.
- DB mappings live under `ledger.db` because multiple features use the same tables.
- Keep tests mirroring package names under `src/test/java`.

## Ops / repo layout notes

- `docker-compose.yml` defines a local Postgres service (host port `5433` by default).
- `logs/` is used for local run/test logs
- `src/test/resources/application-it.yml` holds test profile DB defaults used by `-Pit` integration tests.
- `scripts/` contains convenience wrappers:
  - `scripts/run_with_external_postgres.sh` — start local Postgres and run `./mvnw -Pit verify` (writes a log if run directly).
  - `scripts/run-integration.sh` — wrapper to choose external-Postgres (default) or Testcontainers via `USE_TESTCONTAINERS=1`.
- CI workflow: `.github/workflows/ci.yml` runs unit tests and `-Pit verify` against a Postgres service.
- `pom.xml` includes Maven Enforcer rules to require Java 17+.

## Testing modes

- Unit/web-slice tests are Docker-free and run via `./mvnw test`.
- Integration tests (end-to-end) are opt-in via `./mvnw -Pit verify` and by default run against the external Postgres (docker-compose). Testcontainers mode is supported behind `-Dit.useTestcontainers=true`.

Integration test classes live under `src/test/java/com/aleksastajic/ledger/it`:
- `LedgerFlowIT` — happy-path E2E coverage (idempotency, reversal 409, integrity tamper detection).
- `LedgerConcurrencyIT` — concurrency stress (idempotency + reversal races).
- `LedgerBusinessRulesIT` — business invariants (per-currency balancing, non-negative CUSTOMER, append-only DB triggers).

## When to refactor

If features become large, we can split each feature into subpackages:

- `accounts.api`, `accounts.service`, `accounts.db`
- `journal.api`, `journal.service`, `journal.db`

…but should wait until duplication or size makes it clearly worthwhile.
