package com.aleksastajic.ledger.journal;

import java.util.UUID;

public record PostingResponse(
        UUID accountId,
        String currency,
        String amount,
        String memo
) {
}
