package com.devtrack.model;

/**
 * The current state of a {@link Task}.
 *
 * <p>Using an {@code enum} (instead of free-text {@code String}) makes the set of allowed
 * values ("must be one of TODO, IN_PROGRESS, DONE") enforceable by the type system and by
 * Jackson when it parses incoming JSON: an unknown value simply fails to deserialize, which
 * the backend turns into an HTTP 400 response.
 *
 * <p>Requirements: 1.2 (defaults to TODO on create), 1.8 (only these values are accepted).
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
