package com.aleksastajic.ledger.ledger;

import com.aleksastajic.ledger.journal.CreateJournalEntryRequest;
import com.aleksastajic.ledger.journal.JournalEntryResponse;

import java.util.UUID;

public interface LedgerWriter {
    JournalEntryResponse createJournalEntry(UUID clientId, String idempotencyKey, CreateJournalEntryRequest request);

    JournalEntryResponse createJournalEntry(UUID clientId, String idempotencyKey, CreateJournalEntryRequest request, UUID reversesJournalEntryId);
}
