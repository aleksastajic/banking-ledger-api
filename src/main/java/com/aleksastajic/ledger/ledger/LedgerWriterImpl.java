package com.aleksastajic.ledger.ledger;

import com.aleksastajic.ledger.accounts.AccountEntity;
import com.aleksastajic.ledger.accounts.AccountRepository;
import com.aleksastajic.ledger.accounts.AccountType;
import com.aleksastajic.ledger.journal.CreateJournalEntryRequest;
import com.aleksastajic.ledger.journal.JournalEntryResponse;
import com.aleksastajic.ledger.ledger.crypto.Sha256;
import com.aleksastajic.ledger.ledger.db.IdempotencyRequestEntity;
import com.aleksastajic.ledger.ledger.db.IdempotencyRequestRepository;
import com.aleksastajic.ledger.ledger.db.JournalEntryEntity;
import com.aleksastajic.ledger.ledger.db.JournalEntryRepository;
import com.aleksastajic.ledger.ledger.db.LedgerChainHeadEntity;
import com.aleksastajic.ledger.ledger.db.LedgerChainHeadRepository;
import com.aleksastajic.ledger.ledger.db.PostingEntity;
import com.aleksastajic.ledger.ledger.db.PostingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerWriterImpl implements LedgerWriter {

    private static final String HASH_ALGO = "SHA-256";

    private final AccountRepository accountRepository;
    private final LedgerChainHeadRepository chainHeadRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;

    private final Clock clock;

    @Autowired
    public LedgerWriterImpl(
            AccountRepository accountRepository,
            LedgerChainHeadRepository chainHeadRepository,
            JournalEntryRepository journalEntryRepository,
            PostingRepository postingRepository,
            IdempotencyRequestRepository idempotencyRequestRepository
    ) {
        this(accountRepository, chainHeadRepository, journalEntryRepository, postingRepository, idempotencyRequestRepository, Clock.systemUTC());
    }

    LedgerWriterImpl(
            AccountRepository accountRepository,
            LedgerChainHeadRepository chainHeadRepository,
            JournalEntryRepository journalEntryRepository,
            PostingRepository postingRepository,
            IdempotencyRequestRepository idempotencyRequestRepository,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.postingRepository = postingRepository;
        this.idempotencyRequestRepository = idempotencyRequestRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JournalEntryResponse createJournalEntry(UUID clientId, String idempotencyKey, CreateJournalEntryRequest request) {
        return createJournalEntry(clientId, idempotencyKey, request, null);
    }

    @Override
    @Transactional
    public JournalEntryResponse createJournalEntry(UUID clientId, String idempotencyKey, CreateJournalEntryRequest request, UUID reversesJournalEntryId) {
        // When provided, reversesJournalEntryId is persisted on the journal entry and included in
        // canonical hashing/idempotency to prevent a key from being reused to reverse a different entry.
        if (request.postings() == null || request.postings().size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal entry must have at least 2 postings");
        }

        Instant now = Instant.now(clock);
        ParsedPostings parsed = parseAndValidatePostings(request.postings());

        // Canonical request hashing is the single source of truth for idempotency.
        String requestHash = Sha256.hex(canonicalRequest(clientId, idempotencyKey, request, reversesJournalEntryId, parsed));

        IdempotencyRequestEntity idem = new IdempotencyRequestEntity(
                UUID.randomUUID(),
                clientId,
                idempotencyKey,
                requestHash,
                HASH_ALGO,
                null,
                now
        );

        try {
            idempotencyRequestRepository.saveAndFlush(idem);
        } catch (DataIntegrityViolationException e) {
            IdempotencyRequestEntity existing = idempotencyRequestRepository
                    .findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                    .orElseThrow(() -> e);

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with a different request");
            }

            JournalEntryEntity existingEntry = existing.getJournalEntry();
            if (existingEntry == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotent request is still in progress");
            }

            return new JournalEntryResponse(
                    existingEntry.getId(),
                    existingEntry.getSeqNo(),
                    existingEntry.getCreatedAt(),
                    existingEntry.getReversesJournalEntryId(),
                    existingEntry.getPrevHash(),
                    existingEntry.getEntryHash()
            );
        }

        lockAccountsInStableOrder(parsed.accountIds());

        LedgerChainHeadEntity head = chainHeadRepository.findByIdForUpdate(LedgerChainHeadEntity.SINGLETON_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ledger chain head is missing"));

        long seqNo = head.getLastSeqNo() + 1;
        String prevHash = head.getLastHash();

        UUID journalEntryId = UUID.randomUUID();
        String entryHash = Sha256.hex(canonicalEntry(seqNo, now, request.description(), reversesJournalEntryId, prevHash, parsed));

        JournalEntryEntity entry = new JournalEntryEntity(
                journalEntryId,
                seqNo,
                now,
                request.description(),
            reversesJournalEntryId,
                prevHash,
                entryHash,
                HASH_ALGO
        );
        journalEntryRepository.save(entry);

        List<PostingEntity> postings = new ArrayList<>(parsed.postings().size());
        for (ParsedPosting p : parsed.postings()) {
            postings.add(new PostingEntity(
                    UUID.randomUUID(),
                    entry,
                    p.accountId(),
                    p.currency(),
                    p.amount(),
                    p.memo(),
                    now
            ));
        }
        postingRepository.saveAll(postings);

        enforceCustomerNonNegative(parsed.accountIds());

        head.setLastSeqNo(seqNo);
        head.setLastHash(entryHash);
        head.setUpdatedAt(now);
        chainHeadRepository.save(head);

        idem.setJournalEntry(entry);
        idempotencyRequestRepository.save(idem);

        return new JournalEntryResponse(journalEntryId, seqNo, now, reversesJournalEntryId, prevHash, entryHash);
    }

    private void lockAccountsInStableOrder(List<UUID> accountIds) {
        for (UUID accountId : accountIds) {
            AccountEntity account = accountRepository.findWithLockById(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown account: " + accountId));
            if (account.getAccountType() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Account has no type: " + accountId);
            }
        }
    }

    private void enforceCustomerNonNegative(List<UUID> accountIds) {
        List<PostingRepository.AccountBalanceRow> balances = postingRepository.aggregateBalances(accountIds);

        for (PostingRepository.AccountBalanceRow row : balances) {
            if (!AccountType.CUSTOMER.name().equals(row.getAccountType())) {
                continue;
            }

            if (row.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient funds for account " + row.getAccountId() + " (" + row.getCurrency() + ")"
                );
            }
        }
    }

    private static ParsedPostings parseAndValidatePostings(List<CreateJournalEntryRequest.PostingRequest> postings) {
        Map<String, BigDecimal> sumByCurrency = new HashMap<>();
        Set<UUID> accountIdSet = new HashSet<>();
        List<ParsedPosting> out = new ArrayList<>(postings.size());

        for (CreateJournalEntryRequest.PostingRequest p : postings) {
            if (p.accountId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting accountId is required");
            }
            if (p.currency() == null || !p.currency().matches("^[A-Z]{3}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting currency must be 3 uppercase letters");
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(p.amount());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting amount is invalid");
            }

            if (amount.scale() > 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting amount scale must be <= 4");
            }

            amount = amount.setScale(4, RoundingMode.UNNECESSARY);

            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting amount cannot be 0");
            }

            sumByCurrency.merge(p.currency(), amount, BigDecimal::add);
            accountIdSet.add(p.accountId());

            out.add(new ParsedPosting(p.accountId(), p.currency(), amount, p.memo()));
        }

        for (Map.Entry<String, BigDecimal> e : sumByCurrency.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postings must balance to 0 per currency");
            }
        }

        List<UUID> accountIds = accountIdSet.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        return new ParsedPostings(out, accountIds);
    }

    private static String canonicalRequest(UUID clientId, String idempotencyKey, CreateJournalEntryRequest request, UUID reversesJournalEntryId, ParsedPostings parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("clientId=").append(clientId).append('\n');
        sb.append("idempotencyKey=").append(escape(idempotencyKey)).append('\n');
        sb.append("reversesJournalEntryId=").append(reversesJournalEntryId == null ? "" : reversesJournalEntryId).append('\n');
        sb.append("description=").append(escape(nullToEmpty(request.description()))).append('\n');
        sb.append("postings_count=").append(parsed.postings().size()).append('\n');
        for (ParsedPosting p : parsed.postings()) {
            sb.append("posting=")
                    .append(p.accountId()).append('|')
                    .append(p.currency()).append('|')
                    .append(p.amount().toPlainString()).append('|')
                    .append(escape(nullToEmpty(p.memo())))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String canonicalEntry(long seqNo, Instant createdAt, String description, UUID reversesJournalEntryId, String prevHash, ParsedPostings parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("seqNo=").append(seqNo).append('\n');
        sb.append("createdAt=").append(createdAt).append('\n');
        sb.append("prevHash=").append(prevHash).append('\n');
        sb.append("reversesJournalEntryId=").append(reversesJournalEntryId == null ? "" : reversesJournalEntryId).append('\n');
        sb.append("description=").append(escape(nullToEmpty(description))).append('\n');
        sb.append("postings_count=").append(parsed.postings().size()).append('\n');
        for (ParsedPosting p : parsed.postings()) {
            sb.append("posting=")
                    .append(p.accountId()).append('|')
                    .append(p.currency()).append('|')
                    .append(p.amount().toPlainString()).append('|')
                    .append(escape(nullToEmpty(p.memo())))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private record ParsedPostings(List<ParsedPosting> postings, List<UUID> accountIds) {
    }

    private record ParsedPosting(UUID accountId, String currency, BigDecimal amount, String memo) {
    }
}
