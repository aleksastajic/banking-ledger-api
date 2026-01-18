package com.aleksastajic.ledger.journal;

import com.aleksastajic.ledger.api.problem.ApiProblem;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Conflict (idempotency conflict or insufficient funds)",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            )
        })
    public ResponseEntity<JournalEntryResponse> create(
            @Parameter(
                name = "X-Client-Id",
                description = "Client identifier for idempotency scoping.",
                required = true,
                example = "11111111-1111-1111-1111-111111111111"
            )
            @RequestHeader("X-Client-Id") @NotNull UUID clientId,

            @Parameter(
                name = "Idempotency-Key",
                description = "Idempotency key for safely retrying the same request. Reusing the key with a different payload returns 409.",
                required = true,
                example = "idem-123"
            )
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
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Conflict (already reversed / idempotency conflict)",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            )
        })
        public ResponseEntity<JournalEntryResponse> reverse(
            @PathVariable UUID id,
            @Parameter(
                name = "X-Client-Id",
                description = "Client identifier for idempotency scoping.",
                required = true,
                example = "11111111-1111-1111-1111-111111111111"
            )
            @RequestHeader("X-Client-Id") @NotNull UUID clientId,

            @Parameter(
                name = "Idempotency-Key",
                description = "Idempotency key for safely retrying the same reversal request.",
                required = true,
                example = "idem-reversal-123"
            )
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
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            )
        })
    public JournalEntryResponse getById(@PathVariable UUID id) {
        return journalEntryService.getById(id);
    }

    @GetMapping("/{id}/postings")
    @Operation(summary = "List postings for a journal entry")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(
                responseCode = "404",
                description = "Not Found",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))
            )
        })
    public PostingListResponse listPostings(@PathVariable UUID id) {
        return new PostingListResponse(journalEntryService.listPostings(id));
    }

    public record PostingListResponse(java.util.List<PostingResponse> postings) {
    }
}
