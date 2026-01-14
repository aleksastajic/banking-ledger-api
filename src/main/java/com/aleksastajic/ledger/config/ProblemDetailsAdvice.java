package com.aleksastajic.ledger.config;

import com.aleksastajic.ledger.api.problem.ApiFieldError;
import com.aleksastajic.ledger.api.problem.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ProblemDetailsAdvice {

    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    private static final String TYPE_VALIDATION = "urn:problem:validation";
    private static final String TYPE_CONFLICT = "urn:problem:conflict";
    private static final String TYPE_BAD_REQUEST = "urn:problem:bad-request";
    private static final String TYPE_INTERNAL = "urn:problem:internal";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiProblem> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ApiFieldError(err.getField(), err.getDefaultMessage()))
                .toList();

        return problem(
                HttpStatus.BAD_REQUEST,
                ApiProblem.of(
                        TYPE_VALIDATION,
                        "Validation failed",
                        HttpStatus.BAD_REQUEST.value(),
                        "One or more fields are invalid",
                        instance(request),
                        fieldErrors
                )
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiProblem> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();

        return problem(
                HttpStatus.BAD_REQUEST,
                ApiProblem.of(
                        TYPE_VALIDATION,
                        "Validation failed",
                        HttpStatus.BAD_REQUEST.value(),
                        "One or more fields are invalid",
                        instance(request),
                        fieldErrors
                )
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiProblem> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiProblem.of(
                        TYPE_BAD_REQUEST,
                        "Missing required header",
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        instance(request)
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiProblem> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiProblem.of(
                        TYPE_BAD_REQUEST,
                        "Malformed request",
                        HttpStatus.BAD_REQUEST.value(),
                        "Request body is invalid or unreadable",
                        instance(request)
                )
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiProblem> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String mostSpecificMessage = ex.getMostSpecificCause() == null ? null : ex.getMostSpecificCause().getMessage();
        String detail = "Request could not be completed due to a conflict";

        // Provide a stable, user-friendly message for known constraints.
        if (mostSpecificMessage != null && mostSpecificMessage.contains("journal_entries_reverses_unique")) {
            detail = "Journal entry has already been reversed";
        }

        return problem(
                HttpStatus.CONFLICT,
                ApiProblem.of(
                        TYPE_CONFLICT,
                        "Conflict",
                        HttpStatus.CONFLICT.value(),
                        detail,
                        instance(request)
                )
        );
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiProblem> handleErrorResponseException(
            ErrorResponseException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String title = ex.getBody() != null && ex.getBody().getTitle() != null
                ? ex.getBody().getTitle()
                : status.getReasonPhrase();

        return problem(
                status,
                ApiProblem.of(
                        "about:blank",
                        title,
                        status.value(),
                        ex.getMessage(),
                        instance(request)
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleUnhandled(
            Exception ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiProblem.of(
                        TYPE_INTERNAL,
                        "Internal Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred",
                        instance(request)
                )
        );
    }

    private static String instance(HttpServletRequest request) {
        return request.getRequestURI();
    }

    private static ResponseEntity<ApiProblem> problem(HttpStatus status, ApiProblem problem) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, status);
    }
}
