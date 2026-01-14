package com.aleksastajic.ledger.api.problem;

import java.util.List;

public record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        List<ApiFieldError> fieldErrors
) {
    public static ApiProblem of(
            String type,
            String title,
            int status,
            String detail,
            String instance
    ) {
        return new ApiProblem(type, title, status, detail, instance, null);
    }

    public static ApiProblem of(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            List<ApiFieldError> fieldErrors
    ) {
        return new ApiProblem(type, title, status, detail, instance, fieldErrors);
    }
}
