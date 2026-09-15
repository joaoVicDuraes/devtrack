package com.devtrack.dto;

import com.devtrack.model.TaskStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Incoming data for a targeted status change (the body of {@code PATCH /tasks/{id}/status}).
 *
 * <p>Unlike a full edit, this request touches a single field, so it carries only {@code status}.
 *
 * <p>{@code @NotNull} requires the value to be present. We use {@code @NotNull} here rather than
 * {@code @NotBlank} because {@code status} is an enum, not a string: "blank" has no meaning for an
 * enum, we simply need a value to be supplied. (If the client sends a value that is not one of the
 * enum constants, JSON parsing fails first and the request is rejected as malformed.)
 *
 * <p>Requirements: 8.4, 5.4.
 */
public record ChangeStatusRequest(

        @NotNull(message = "status is required")
        TaskStatus status
) {
}
