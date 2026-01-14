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

- `com.aleksastajic.ledger.api.problem`
  - API error DTOs (Problem Details).

## Conventions

- Prefer feature packages (`accounts`, `journal`, …) over global `controller/service/repository` folders.
- DB mappings live under `ledger.db` because multiple features use the same tables.
- Keep tests mirroring package names under `src/test/java`.

## When to refactor

If features become large, we can split each feature into subpackages:

- `accounts.api`, `accounts.service`, `accounts.db`
- `journal.api`, `journal.service`, `journal.db`

…but should wait until duplication or size makes it clearly worthwhile.
