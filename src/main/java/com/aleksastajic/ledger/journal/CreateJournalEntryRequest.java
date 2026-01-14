package com.aleksastajic.ledger.journal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateJournalEntryRequest(
        String description,
        @NotNull @Size(min = 2) @Valid List<PostingRequest> postings
) {

    public record PostingRequest(
            @NotNull UUID accountId,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank String amount,
            String memo
    ) {
    }
}
