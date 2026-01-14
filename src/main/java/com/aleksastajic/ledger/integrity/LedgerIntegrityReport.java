package com.aleksastajic.ledger.integrity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerIntegrityReport(
        String status,
        long entriesChecked,
        Long headSeqNo,
        String headHash,
        UUID lastEntryId,
        Instant verifiedAt,
        List<Issue> issues
) {

    public record Issue(
            String code,
            long seqNo,
            UUID journalEntryId,
            String message,
            String expected,
            String actual
    ) {
    }
}
