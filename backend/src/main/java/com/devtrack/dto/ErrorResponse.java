package com.devtrack.dto;

import java.util.List;

/**
 * The consistent JSON shape the API returns for every error (Requirement 8.7).
 *
 * <p>Giving all failures one predictable envelope means clients can handle errors uniformly instead
 * of guessing at ad-hoc shapes. The global exception handler (added in a later task) builds one of
 * these for validation failures, not-found errors, parse errors, and unexpected server errors.
 *
 * <ul>
 *   <li>{@code status} — the HTTP status code (e.g. 400, 404, 500), duplicated in the body so it is
 *       visible even when only the payload is inspected.</li>
 *   <li>{@code message} — a short, human-readable summary of what went wrong.</li>
 *   <li>{@code errors} — the per-field validation problems. It is empty (or {@code null}) for
 *       errors that are not field-specific, such as a 404.</li>
 * </ul>
 *
 * <p>{@code FieldError} is a <em>nested record</em>: a small record declared inside another type.
 * Nesting it keeps this closely-related "one field's problem" shape scoped to where it is used
 * instead of adding a separate top-level file. Each entry pairs the offending {@code field} name
 * with the {@code message} explaining why it failed, which is what lets a validation response list
 * every failed field at once (Requirement 8.9, wired up later).
 *
 * <p>Requirements: 8.4, 8.7.
 */
public record ErrorResponse(
        int status,
        String message,
        List<FieldError> errors
) {

    /**
     * A single field-level validation problem: which field failed and why.
     */
    public record FieldError(
            String field,
            String message
    ) {
    }
}
