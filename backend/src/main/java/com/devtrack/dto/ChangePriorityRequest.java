package com.devtrack.dto;

import com.devtrack.model.TaskPriority;

import jakarta.validation.constraints.NotNull;

/**
 * Incoming data for a targeted priority change (the body of {@code PATCH /tasks/{id}/priority}).
 *
 * <p>Like {@link ChangeStatusRequest}, this carries a single enum field and requires it to be
 * present via {@code @NotNull}. {@code @NotNull} (not {@code @NotBlank}) is correct because the
 * field is an enum rather than a string.
 *
 * <p>Requirements: 8.4, 6.4.
 */
public record ChangePriorityRequest(

        @NotNull(message = "priority is required")
        TaskPriority priority
) {
}
