package com.aleksastajic.ledger.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Canonicalization helpers for hashing/idempotency.
 *
 * Important: posting order must be deterministic and recoverable from the database,
 * otherwise integrity verification cannot recompute entry hashes.
 */
public final class LedgerCanonical {

    private LedgerCanonical() {
    }

    public record CanonicalPosting(UUID accountId, String currency, BigDecimal amount, String memo) {
    }

    public static String canonicalRequest(
            UUID clientId,
            String idempotencyKey,
            String description,
            UUID reversesJournalEntryId,
            List<CanonicalPosting> postings
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("clientId=").append(clientId).append('\n');
        sb.append("idempotencyKey=").append(escape(idempotencyKey)).append('\n');
        sb.append("reversesJournalEntryId=").append(reversesJournalEntryId == null ? "" : reversesJournalEntryId).append('\n');
        sb.append("description=").append(escape(nullToEmpty(description))).append('\n');
        sb.append("postings_count=").append(postings.size()).append('\n');

        for (CanonicalPosting p : sortPostings(postings)) {
            sb.append("posting=")
                    .append(p.accountId()).append('|')
                    .append(p.currency()).append('|')
                    .append(p.amount().toPlainString()).append('|')
                    .append(escape(nullToEmpty(p.memo())))
                    .append('\n');
        }

        return sb.toString();
    }

    public static String canonicalEntry(
            long seqNo,
            Instant createdAt,
            String description,
            UUID reversesJournalEntryId,
            String prevHash,
            List<CanonicalPosting> postings
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("seqNo=").append(seqNo).append('\n');
        sb.append("createdAt=").append(createdAt).append('\n');
        sb.append("prevHash=").append(prevHash).append('\n');
        sb.append("reversesJournalEntryId=").append(reversesJournalEntryId == null ? "" : reversesJournalEntryId).append('\n');
        sb.append("description=").append(escape(nullToEmpty(description))).append('\n');
        sb.append("postings_count=").append(postings.size()).append('\n');

        for (CanonicalPosting p : sortPostings(postings)) {
            sb.append("posting=")
                    .append(p.accountId()).append('|')
                    .append(p.currency()).append('|')
                    .append(p.amount().toPlainString()).append('|')
                    .append(escape(nullToEmpty(p.memo())))
                    .append('\n');
        }

        return sb.toString();
    }

    private static List<CanonicalPosting> sortPostings(List<CanonicalPosting> postings) {
        List<CanonicalPosting> sorted = new ArrayList<>(postings);
        sorted.sort(Comparator
                .comparing((CanonicalPosting p) -> p.accountId().toString())
                .thenComparing(CanonicalPosting::currency)
                .thenComparing(p -> p.amount().toPlainString())
                .thenComparing(p -> nullToEmpty(p.memo()))
        );
        return sorted;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
