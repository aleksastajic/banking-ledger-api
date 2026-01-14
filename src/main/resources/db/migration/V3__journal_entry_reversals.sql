-- V3__journal_entry_reversals.sql

-- Explicit reversal linkage: a journal entry may reverse another journal entry.
-- Stored as a nullable FK and exposed via API as reversesJournalEntryId.

ALTER TABLE journal_entries
  ADD COLUMN reverses_journal_entry_id UUID NULL;

ALTER TABLE journal_entries
  ADD CONSTRAINT journal_entries_reverses_fk
  FOREIGN KEY (reverses_journal_entry_id) REFERENCES journal_entries(id);

ALTER TABLE journal_entries
  ADD CONSTRAINT journal_entries_reverses_not_self
  CHECK (reverses_journal_entry_id IS NULL OR reverses_journal_entry_id <> id);

-- Enforce at most one reversal per original entry.
CREATE UNIQUE INDEX journal_entries_reverses_unique
  ON journal_entries (reverses_journal_entry_id)
  WHERE reverses_journal_entry_id IS NOT NULL;
