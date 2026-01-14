package com.aleksastajic.ledger.api.problem;

public record ApiFieldError(
        String field,
        String message
) {
}
