-- V1__init.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ACCOUNTS
CREATE TABLE accounts (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name         TEXT NOT NULL,
  account_type TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL,
  CONSTRAINT accounts_account_type_chk
    CHECK (account_type IN ('CUSTOMER','INTERNAL','CLEARING','FEES','SUSPENSE'))
);

-- SINGLE-ROW CHAIN HEAD (deterministic PK + singleton CHECK)
CREATE TABLE ledger_chain_head (
  id          UUID PRIMARY KEY,
  last_seq_no BIGINT NOT NULL,
  last_hash   CHAR(64) NOT NULL,
  hash_algo   TEXT NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL,

  CONSTRAINT chain_head_hash_algo_chk CHECK (hash_algo = 'SHA-256'),
  CONSTRAINT chain_head_single_row_chk
    CHECK (id = '00000000-0000-0000-0000-000000000001')
);

-- Seed exactly one row (id is constant)
INSERT INTO ledger_chain_head (id, last_seq_no, last_hash, hash_algo, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 0, repeat('0', 64), 'SHA-256', now());

-- JOURNAL ENTRIES (idempotency NOT stored here)
CREATE TABLE journal_entries (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  seq_no      BIGINT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL,
  description TEXT NULL,

  prev_hash   CHAR(64) NOT NULL,
  entry_hash  CHAR(64) NOT NULL,
  hash_algo   TEXT NOT NULL,

  CONSTRAINT journal_entries_seq_no_uk UNIQUE (seq_no),
  CONSTRAINT journal_entries_hash_algo_chk CHECK (hash_algo = 'SHA-256')
);

-- POSTINGS
CREATE TABLE postings (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
  account_id       UUID NOT NULL REFERENCES accounts(id),

  currency         CHAR(3) NOT NULL,
  amount           NUMERIC(19,4) NOT NULL,
  memo             TEXT NULL,
  created_at       TIMESTAMPTZ NOT NULL,

  CONSTRAINT postings_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT postings_amount_nonzero_chk CHECK (amount <> 0),
  CONSTRAINT postings_amount_scale_chk CHECK (scale(amount) <= 4)
);

-- IDEMPOTENCY (source of truth)
CREATE TABLE idempotency_requests (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id        UUID NOT NULL,
  idempotency_key  TEXT NOT NULL,

  request_hash     CHAR(64) NOT NULL,
  hash_algo        TEXT NOT NULL,

  journal_entry_id UUID NULL REFERENCES journal_entries(id),
  created_at       TIMESTAMPTZ NOT NULL,

  CONSTRAINT idem_hash_algo_chk CHECK (hash_algo = 'SHA-256'),
  CONSTRAINT idempotency_requests_client_key_uk UNIQUE (client_id, idempotency_key)
);
