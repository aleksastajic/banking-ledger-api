package com.aleksastajic.ledger.journal;

import com.aleksastajic.ledger.ledger.LedgerCanonical;
import com.aleksastajic.ledger.ledger.LedgerWriter;
import com.aleksastajic.ledger.ledger.crypto.Sha256;
import com.aleksastajic.ledger.ledger.db.IdempotencyRequestEntity;
import com.aleksastajic.ledger.ledger.db.IdempotencyRequestRepository;
import com.aleksastajic.ledger.ledger.db.JournalEntryEntity;
import com.aleksastajic.ledger.ledger.db.JournalEntryRepository;
import com.aleksastajic.ledger.ledger.db.PostingEntity;
import com.aleksastajic.ledger.ledger.db.PostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class JournalEntryService {

    private final LedgerWriter ledgerWriter;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;

    public JournalEntryService(
            LedgerWriter ledgerWriter,
            JournalEntryRepository journalEntryRepository,
            PostingRepository postingRepository,
            IdempotencyRequestRepository idempotencyRequestRepository
    ) {
        this.ledgerWriter = ledgerWriter;
        this.journalEntryRepository = journalEntryRepository;
        this.postingRepository = postingRepository;
        this.idempotencyRequestRepository = idempotencyRequestRepository;
    }

    public JournalEntryResponse create(UUID clientId, String idempotencyKey, CreateJournalEntryRequest request) {
        return ledgerWriter.createJournalEntry(clientId, idempotencyKey, request);
    }

    public JournalEntryResponse getById(UUID id) {
        JournalEntryEntity entry = journalEntryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Journal entry not found"));

        return new JournalEntryResponse(
                entry.getId(),
                entry.getSeqNo(),
                entry.getCreatedAt(),
                entry.getReversesJournalEntryId(),
                entry.getPrevHash(),
                entry.getEntryHash()
        );
    }

    public List<PostingResponse> listPostings(UUID journalEntryId) {
        // Ensure 404 if entry does not exist
        if (!journalEntryRepository.existsById(journalEntryId)) {
            throw new ResponseStatusException(NOT_FOUND, "Journal entry not found");
        }

        return postingRepository.findByJournalEntry_IdOrderById(journalEntryId).stream()
                .map(this::toResponse)
                .toList();
    }

    public JournalEntryResponse reverse(UUID originalJournalEntryId, UUID clientId, String idempotencyKey, String descriptionOverride) {
        // A reversal is represented as a new journal entry whose postings negate the original,
        // and which links back to the original via reversesJournalEntryId.
        JournalEntryEntity originalEntry = journalEntryRepository.findById(originalJournalEntryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Journal entry not found"));

        List<PostingEntity> originalPostings = postingRepository.findByJournalEntry_IdOrderById(originalJournalEntryId);
        if (originalPostings.size() < 2) {
            throw new ResponseStatusException(CONFLICT, "Cannot reverse a journal entry with fewer than 2 postings");
        }

        String trimmedDescription = descriptionOverride == null ? null : descriptionOverride.trim();
        String description = (trimmedDescription == null || trimmedDescription.isBlank())
                ? "Reversal of " + originalEntry.getId()
                : trimmedDescription;

        List<CreateJournalEntryRequest.PostingRequest> reversedPostings = originalPostings.stream()
                .map(p -> {
                    BigDecimal negAmount = p.getAmount().negate().setScale(4, RoundingMode.UNNECESSARY);
                    return new CreateJournalEntryRequest.PostingRequest(
                            p.getAccountId(),
                            p.getCurrency(),
                            negAmount.toPlainString(),
                            p.getMemo()
                    );
                })
                .toList();

        CreateJournalEntryRequest reversalRequest = new CreateJournalEntryRequest(description, reversedPostings);

        // Preserve idempotency semantics without relying on a DB unique-constraint violation.
        List<LedgerCanonical.CanonicalPosting> canonicalPostings = reversedPostings.stream()
            .map(p -> new LedgerCanonical.CanonicalPosting(p.accountId(), p.currency(), new BigDecimal(p.amount()), p.memo()))
            .toList();

        String requestHash = Sha256.hex(LedgerCanonical.canonicalRequest(
            clientId,
            idempotencyKey,
            reversalRequest.description(),
            originalJournalEntryId,
            canonicalPostings
        ));

        IdempotencyRequestEntity existingIdem = idempotencyRequestRepository
            .findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
            .orElse(null);

        if (existingIdem != null) {
            if (!existingIdem.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(CONFLICT, "Idempotency key reused with a different request");
            }
            JournalEntryEntity existingEntry = existingIdem.getJournalEntry();
            if (existingEntry == null) {
            throw new ResponseStatusException(CONFLICT, "Idempotent request is still in progress");
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

        // If the original was already reversed with a different idempotency key, return a clean 409.
        // This avoids a noisy DB unique-constraint violation during normal sequential requests/tests.
        if (journalEntryRepository.existsByReversesJournalEntryId(originalJournalEntryId)) {
            throw new ResponseStatusException(CONFLICT, "Journal entry has already been reversed");
        }

        return ledgerWriter.createJournalEntry(
                clientId,
                idempotencyKey,
            reversalRequest,
                originalJournalEntryId
        );
    }

    private PostingResponse toResponse(PostingEntity p) {
        return new PostingResponse(
                p.getAccountId(),
                p.getCurrency(),
                p.getAmount().toPlainString(),
                p.getMemo()
        );
    }
}
