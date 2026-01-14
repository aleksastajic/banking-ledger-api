package com.aleksastajic.ledger.integrity;

import com.aleksastajic.ledger.ledger.LedgerCanonical;
import com.aleksastajic.ledger.ledger.crypto.Sha256;
import com.aleksastajic.ledger.ledger.db.JournalEntryEntity;
import com.aleksastajic.ledger.ledger.db.JournalEntryRepository;
import com.aleksastajic.ledger.ledger.db.LedgerChainHeadEntity;
import com.aleksastajic.ledger.ledger.db.LedgerChainHeadRepository;
import com.aleksastajic.ledger.ledger.db.PostingEntity;
import com.aleksastajic.ledger.ledger.db.PostingRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerIntegrityService {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final LedgerChainHeadRepository ledgerChainHeadRepository;
    private final Clock clock;

    public LedgerIntegrityService(
            JournalEntryRepository journalEntryRepository,
            PostingRepository postingRepository,
            LedgerChainHeadRepository ledgerChainHeadRepository,
            Clock clock
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.postingRepository = postingRepository;
        this.ledgerChainHeadRepository = ledgerChainHeadRepository;
        this.clock = clock;
    }

    public LedgerIntegrityReport verify() {
        Instant now = Instant.now(clock);

        LedgerChainHeadEntity head = ledgerChainHeadRepository.findById(LedgerChainHeadEntity.SINGLETON_ID)
                .orElse(null);

        List<JournalEntryEntity> entries = journalEntryRepository.findAllByOrderBySeqNoAsc();
        List<LedgerIntegrityReport.Issue> issues = new ArrayList<>();

        UUID lastEntryId = null;

        for (int i = 0; i < entries.size(); i++) {
            JournalEntryEntity e = entries.get(i);
            lastEntryId = e.getId();

            long expectedSeqNo = i + 1L;
            if (e.getSeqNo() != expectedSeqNo) {
                issues.add(new LedgerIntegrityReport.Issue(
                        "SEQ_NO_GAP_OR_DUP",
                        e.getSeqNo(),
                        e.getId(),
                        "seqNo is not contiguous starting at 1",
                        Long.toString(expectedSeqNo),
                        Long.toString(e.getSeqNo())
                ));
            }

            if (i == 0) {
                // We can't prove genesis without extra metadata; we only validate internal linkage.
                // Still, we sanity-check the first entry prevHash matches the expected genesis value.
                if (!GENESIS_HASH.equals(e.getPrevHash())) {
                    issues.add(new LedgerIntegrityReport.Issue(
                            "FIRST_PREV_HASH_UNEXPECTED",
                            e.getSeqNo(),
                            e.getId(),
                            "first entry prevHash is not the expected genesis hash",
                            GENESIS_HASH,
                            e.getPrevHash()
                    ));
                }
            } else {
                String expectedPrevHash = entries.get(i - 1).getEntryHash();
                if (!expectedPrevHash.equals(e.getPrevHash())) {
                    issues.add(new LedgerIntegrityReport.Issue(
                            "PREV_HASH_MISMATCH",
                            e.getSeqNo(),
                            e.getId(),
                            "prevHash does not match previous entryHash",
                            expectedPrevHash,
                            e.getPrevHash()
                    ));
                }
            }

            List<PostingEntity> postings = postingRepository.findByJournalEntry_IdOrderById(e.getId());
            List<LedgerCanonical.CanonicalPosting> canonicalPostings = postings.stream()
                    .map(p -> new LedgerCanonical.CanonicalPosting(p.getAccountId(), p.getCurrency(), p.getAmount(), p.getMemo()))
                    .toList();

            String recomputedEntryHash = Sha256.hex(LedgerCanonical.canonicalEntry(
                    e.getSeqNo(),
                    e.getCreatedAt(),
                    e.getDescription(),
                    e.getReversesJournalEntryId(),
                    e.getPrevHash(),
                    canonicalPostings
            ));

            if (!recomputedEntryHash.equals(e.getEntryHash())) {
                issues.add(new LedgerIntegrityReport.Issue(
                        "ENTRY_HASH_MISMATCH",
                        e.getSeqNo(),
                        e.getId(),
                        "entryHash does not match recomputed hash",
                        recomputedEntryHash,
                        e.getEntryHash()
                ));
            }
        }

        if (head != null) {
            long expectedLastSeqNo = entries.isEmpty() ? 0L : entries.get(entries.size() - 1).getSeqNo();
            String expectedLastHash = entries.isEmpty() ? GENESIS_HASH : entries.get(entries.size() - 1).getEntryHash();

            if (head.getLastSeqNo() != expectedLastSeqNo) {
                issues.add(new LedgerIntegrityReport.Issue(
                        "CHAIN_HEAD_SEQ_MISMATCH",
                        expectedLastSeqNo,
                        lastEntryId,
                        "ledger_chain_head.last_seq_no does not match last entry seq_no",
                        Long.toString(expectedLastSeqNo),
                        Long.toString(head.getLastSeqNo())
                ));
            }

            if (!head.getLastHash().equals(expectedLastHash)) {
                issues.add(new LedgerIntegrityReport.Issue(
                        "CHAIN_HEAD_HASH_MISMATCH",
                        expectedLastSeqNo,
                        lastEntryId,
                        "ledger_chain_head.last_hash does not match last entry hash",
                        expectedLastHash,
                        head.getLastHash()
                ));
            }
        }

        String status = issues.isEmpty() ? "OK" : "FAILED";

        return new LedgerIntegrityReport(
                status,
                entries.size(),
                head == null ? null : head.getLastSeqNo(),
                head == null ? null : head.getLastHash(),
                lastEntryId,
                now,
                issues
        );
    }
}
