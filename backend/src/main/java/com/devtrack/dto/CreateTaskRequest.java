package com.devtrack.dto;

import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming data for creating a task (the body of {@code POST /tasks}).
 *
 * <p>This is a Java <em>record</em>. A record is a compact, immutable data carrier: the single
 * header line below declares the fields, and the compiler generates the constructor, the accessor
 * methods ({@code title()}, {@code description()}, ...), plus {@code equals}/{@code hashCode}/
 * {@code toString} for us. Records are a natural fit for DTOs because a DTO is "just data with no
 * behaviour" and we never want to mutate a request object after it arrives.
 *
 * <p><strong>Why a DTO instead of the {@code Task} entity?</strong> (Requirement 8.4) The entity
 * is our internal persistence model — it carries the database id and is tied to JPA. If we let the
 * HTTP layer bind directly onto the entity, callers could try to set fields they should not
 * control (like {@code id}), and any change to the database shape would leak into the public API.
 * A dedicated request DTO gives the API its own stable, intentional contract that is decoupled
 * from how we happen to store data.
 *
 * <p><strong>The validation annotations</strong> come from {@code jakarta.validation.constraints}.
 * When a controller marks this parameter {@code @Valid}, Spring checks these rules before our code
 * runs and turns any violation into an HTTP 400, so invalid input never reaches the service.
 * <ul>
 *   <li>{@code @NotBlank} — the value must be present and contain at least one non-whitespace
 *       character (so {@code null}, {@code ""}, and {@code "   "} are all rejected). Used on the
 *       required title.</li>
 *   <li>{@code @Size(max = ...)} — bounds the string length so oversized input is rejected up front
 *       rather than failing later at the database column limit.</li>
 * </ul>
 *
 * <p>Note we do <em>not</em> trim the title here. The "trim, then require 1–150 characters" rule is
 * business logic and lives in the service layer; these annotations only carry the raw structural
 * validation. {@code status} and {@code priority} are nullable on purpose: when omitted, the
 * service applies the defaults (TODO / MEDIUM).
 *
 * <p>Requirements: 8.4, 1.5, 1.6, 1.7.
 */
public record CreateTaskRequest(

        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority
) {
}
