-- V2__constraints_and_indexes.sql

-- Indexes for reads and aggregation
CREATE INDEX postings_account_currency_created_idx
  ON postings (account_id, currency, created_at);

CREATE INDEX postings_journal_entry_idx
  ON postings (journal_entry_id);

CREATE INDEX journal_entries_created_at_idx
  ON journal_entries (created_at);

CREATE INDEX idempotency_requests_created_at_idx
  ON idempotency_requests (created_at);

-- Append-only: forbid UPDATE/DELETE on journal_entries and postings
CREATE OR REPLACE FUNCTION forbid_update_delete()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'append-only table: updates/deletes are forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS journal_entries_forbid_ud ON journal_entries;
CREATE TRIGGER journal_entries_forbid_ud
BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();

DROP TRIGGER IF EXISTS postings_forbid_ud ON postings;
CREATE TRIGGER postings_forbid_ud
BEFORE UPDATE OR DELETE ON postings
FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();

-- Double-entry invariant (deferred): per journal_entry_id per currency, sum(amount) must be 0.
-- PostgreSQL CONSTRAINT TRIGGER is always row-level (no FOR EACH STATEMENT), but can be deferred.
-- Given journal entries are small (typically 2..N postings), the per-row check is acceptable.

CREATE OR REPLACE FUNCTION assert_double_entry_zero_sum()
RETURNS trigger AS $$
DECLARE
  bad_count INT;
BEGIN
  SELECT COUNT(*) INTO bad_count
  FROM (
    SELECT currency
    FROM postings
    WHERE journal_entry_id = NEW.journal_entry_id
    GROUP BY currency
    HAVING SUM(amount) <> 0
  ) t;

  IF bad_count > 0 THEN
    RAISE EXCEPTION 'double-entry invariant violated for journal_entry_id=%', NEW.journal_entry_id;
  END IF;

  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS postings_double_entry_chk ON postings;
CREATE CONSTRAINT TRIGGER postings_double_entry_chk
AFTER INSERT ON postings
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION assert_double_entry_zero_sum();
