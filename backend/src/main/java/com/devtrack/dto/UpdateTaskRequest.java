package com.devtrack.dto;

import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming data for a full edit of a task (the body of {@code PUT /tasks/{id}}).
 *
 * <p>A full edit ("PUT") replaces all four editable fields at once, so its shape and validation are
 * intentionally identical to {@link CreateTaskRequest}: a required, length-bounded {@code title},
 * an optional length-bounded {@code description}, and nullable {@code status} / {@code priority}
 * that fall back to defaults when omitted.
 *
 * <p>We keep this as a <em>separate</em> record rather than reusing {@code CreateTaskRequest}, even
 * though they look the same today. Create and update are distinct API operations, and giving each
 * its own type means one can evolve later (say, update gains a field create does not) without
 * silently changing the other. The small duplication buys clarity and independence.
 *
 * <p>See {@link CreateTaskRequest} for what records are and what the validation annotations do.
 *
 * <p>Requirements: 8.4, 4.5, 4.6, 4.7.
 */
public record UpdateTaskRequest(

        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority
) {
}
