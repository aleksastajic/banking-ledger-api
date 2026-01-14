package com.aleksastajic.ledger.journal;

import com.aleksastajic.ledger.ledger.LedgerWriter;
import com.aleksastajic.ledger.ledger.db.JournalEntryEntity;
import com.aleksastajic.ledger.ledger.db.JournalEntryRepository;
import com.aleksastajic.ledger.ledger.db.PostingEntity;
import com.aleksastajic.ledger.ledger.db.PostingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalEntryServiceReversalTest {

    @Test
    void reverse_buildsNegatingPostings_andCallsLedgerWriter() {
        LedgerWriter ledgerWriter = mock(LedgerWriter.class);
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        PostingRepository postingRepository = mock(PostingRepository.class);

        JournalEntryService service = new JournalEntryService(ledgerWriter, journalEntryRepository, postingRepository);

        UUID originalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID clientId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String idempotencyKey = "rev-1";

        JournalEntryEntity originalEntry = new JournalEntryEntity(
                originalId,
                1L,
                Instant.parse("2026-01-14T00:00:00Z"),
                "orig",
                null,
                "prev",
                "hash",
                "sha-256"
        );

        when(journalEntryRepository.findById(originalId)).thenReturn(Optional.of(originalEntry));

        PostingEntity p1 = new PostingEntity(
                UUID.randomUUID(),
                originalEntry,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "EUR",
                new BigDecimal("10.0000"),
                "m1",
                Instant.parse("2026-01-14T00:00:00Z")
        );
        PostingEntity p2 = new PostingEntity(
                UUID.randomUUID(),
                originalEntry,
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "EUR",
                new BigDecimal("-10.0000"),
                null,
                Instant.parse("2026-01-14T00:00:00Z")
        );

        when(postingRepository.findByJournalEntry_IdOrderById(originalId)).thenReturn(List.of(p1, p2));

        UUID reversalId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(ledgerWriter.createJournalEntry(eq(clientId), eq(idempotencyKey), any(), eq(originalId)))
                .thenReturn(new JournalEntryResponse(reversalId, 2L, Instant.parse("2026-01-14T00:00:00Z"), originalId, "x", "y"));

        JournalEntryResponse response = service.reverse(originalId, clientId, idempotencyKey, null);
        assertThat(response.id()).isEqualTo(reversalId);

        ArgumentCaptor<CreateJournalEntryRequest> requestCaptor = ArgumentCaptor.forClass(CreateJournalEntryRequest.class);
        verify(ledgerWriter).createJournalEntry(eq(clientId), eq(idempotencyKey), requestCaptor.capture(), eq(originalId));

        CreateJournalEntryRequest sent = requestCaptor.getValue();
        assertThat(sent.description()).isEqualTo("Reversal of " + originalId);
        assertThat(sent.postings()).hasSize(2);

        assertThat(sent.postings().get(0).accountId()).isEqualTo(p1.getAccountId());
        assertThat(sent.postings().get(0).currency()).isEqualTo("EUR");
        assertThat(sent.postings().get(0).amount()).isEqualTo("-10.0000");
        assertThat(sent.postings().get(0).memo()).isEqualTo("m1");

        assertThat(sent.postings().get(1).accountId()).isEqualTo(p2.getAccountId());
        assertThat(sent.postings().get(1).currency()).isEqualTo("EUR");
        assertThat(sent.postings().get(1).amount()).isEqualTo("10.0000");
        assertThat(sent.postings().get(1).memo()).isNull();
    }

    @Test
    void reverse_missingOriginal_returns404() {
        LedgerWriter ledgerWriter = mock(LedgerWriter.class);
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        PostingRepository postingRepository = mock(PostingRepository.class);

        JournalEntryService service = new JournalEntryService(ledgerWriter, journalEntryRepository, postingRepository);

        UUID originalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(journalEntryRepository.findById(originalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reverse(originalId, UUID.randomUUID(), "k", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }
}
