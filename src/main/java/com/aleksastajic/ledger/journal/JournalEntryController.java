package com.aleksastajic.ledger.journal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/journal-entries")
@Tag(name = "Journal")
@Validated
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    public JournalEntryController(JournalEntryService journalEntryService) {
        this.journalEntryService = journalEntryService;
    }

    @PostMapping
    @Operation(summary = "Create journal entry")
    public ResponseEntity<JournalEntryResponse> create(
            @RequestHeader("X-Client-Id") @NotNull UUID clientId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateJournalEntryRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        JournalEntryResponse created = journalEntryService.create(clientId, idempotencyKey, request);

        URI location = uriBuilder.path("/journal-entries/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

        @PostMapping("/{id}/reversal")
        @Operation(summary = "Create a reversal journal entry")
        public ResponseEntity<JournalEntryResponse> reverse(
            @PathVariable UUID id,
            @RequestHeader("X-Client-Id") @NotNull UUID clientId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody(required = false) ReverseJournalEntryRequest request,
            UriComponentsBuilder uriBuilder
        ) {
        JournalEntryResponse created = journalEntryService.reverse(
            id,
            clientId,
            idempotencyKey,
            request == null ? null : request.description()
        );

        URI location = uriBuilder.path("/journal-entries/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
        }

    @GetMapping("/{id}")
    @Operation(summary = "Get journal entry by id")
    public JournalEntryResponse getById(@PathVariable UUID id) {
        return journalEntryService.getById(id);
    }

    @GetMapping("/{id}/postings")
    @Operation(summary = "List postings for a journal entry")
    public PostingListResponse listPostings(@PathVariable UUID id) {
        return new PostingListResponse(journalEntryService.listPostings(id));
    }

    public record PostingListResponse(java.util.List<PostingResponse> postings) {
    }
}
