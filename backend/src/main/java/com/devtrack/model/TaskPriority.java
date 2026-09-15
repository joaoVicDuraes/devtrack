package com.devtrack.model;

/**
 * The importance level of a {@link Task}.
 *
 * <p>Like {@link TaskStatus}, this is an {@code enum} so the allowed values
 * ("must be one of LOW, MEDIUM, HIGH") are enforced by the type system and by JSON parsing
 * rather than by hand-written checks.
 *
 * <p>Requirements: 1.3 (defaults to MEDIUM on create), 1.9 (only these values are accepted).
 */
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}
