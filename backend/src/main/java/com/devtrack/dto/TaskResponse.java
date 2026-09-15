package com.devtrack.dto;

import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

/**
 * Outgoing representation of a task returned by the API.
 *
 * <p>This is the counterpart to the request DTOs: it is what the service maps a {@code Task} entity
 * into before the controller sends it back as JSON. Every task view exposes exactly these five
 * fields (Requirements 2.3, 3.1), so the client always receives a complete, predictable shape.
 *
 * <p><strong>Why not just return the {@code Task} entity?</strong> (Requirement 8.4) Same reasoning
 * as for requests: the entity is our internal, JPA-managed persistence model. Returning it directly
 * couples the wire format to the database and risks serializing internal or lazy-loaded state.
 * A dedicated response record lets us decide deliberately what the API exposes — here, the id and
 * the four editable fields, and nothing more.
 *
 * <p>Requirements: 8.4, 2.3, 3.1.
 */
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority
) {
}
