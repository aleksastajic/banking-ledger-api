package com.aleksastajic.ledger.journal;

import java.time.Instant;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        long seqNo,
        Instant createdAt,
        UUID reversesJournalEntryId,
        String prevHash,
        String entryHash
) {
}
