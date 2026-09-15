package com.devtrack.service;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.exception.TaskNotFoundException;

import java.util.List;

/**
 * The business-logic layer for tasks, sitting between the controller and the repository.
 *
 * <p>In the Controller &rarr; Service &rarr; Repository architecture, the controller delegates every
 * task operation here (Requirement 8.1) and this layer is the only one that talks to the
 * {@code TaskRepository} (Requirement 8.3). All the rules live here: defaulting omitted fields,
 * trimming the title, applying targeted status/priority updates, and deciding when a task is
 * missing. The controller and repository stay free of business logic as a result.
 *
 * <p><strong>Why an interface?</strong> Declaring the contract as an interface separates <em>what</em>
 * the service does from <em>how</em> it does it (the implementation comes in a later task). It also
 * makes the service easy to substitute in tests. Spring injects whichever class implements this
 * interface wherever a {@code TaskService} is required.
 *
 * <p><strong>Why DTOs in and out (never the entity)?</strong> (Requirement 8.4) Each method accepts a
 * request DTO and returns a {@link TaskResponse}, so the JPA {@code Task} entity never leaks past the
 * service boundary. That keeps the API's wire format decoupled from the internal persistence model.
 *
 * <p>Requirements: 8.1, 8.3, 8.4.
 */
public interface TaskService {

    /**
     * Creates a new task from the request, applying defaults for any omitted optional fields
     * (status &rarr; {@code TODO}, priority &rarr; {@code MEDIUM}, description &rarr; {@code ""}).
     *
     * @param request the validated create-task request
     * @return the stored task, including its newly assigned id
     */
    TaskResponse create(CreateTaskRequest request);

    /**
     * Returns every stored task ordered by ascending id (Requirement 2.1). The list is empty when no
     * task exists.
     *
     * @return all tasks as response DTOs, in ascending-id order
     */
    List<TaskResponse> listAll();

    /**
     * Retrieves a single task by its id.
     *
     * @param id the id of the task to retrieve
     * @return the matching task
     * @throws TaskNotFoundException if no task has the given id
     */
    TaskResponse getById(Long id);

    /**
     * Fully replaces an existing task's title, description, status, and priority with the values in
     * the request, applying the same defaulting rules as {@link #create(CreateTaskRequest)} for any
     * omitted optional field.
     *
     * @param id      the id of the task to update
     * @param request the validated edit-task request
     * @return the updated task
     * @throws TaskNotFoundException if no task has the given id
     */
    TaskResponse update(Long id, UpdateTaskRequest request);

    /**
     * Changes only the status of an existing task, leaving its title, description, and priority
     * unchanged (Requirement 5.2).
     *
     * @param id      the id of the task to update
     * @param request the validated change-status request
     * @return the updated task
     * @throws TaskNotFoundException if no task has the given id
     */
    TaskResponse changeStatus(Long id, ChangeStatusRequest request);

    /**
     * Changes only the priority of an existing task, leaving its title, description, and status
     * unchanged (Requirement 6.1).
     *
     * @param id      the id of the task to update
     * @param request the validated change-priority request
     * @return the updated task
     * @throws TaskNotFoundException if no task has the given id
     */
    TaskResponse changePriority(Long id, ChangePriorityRequest request);

    /**
     * Deletes the task with the given id.
     *
     * @param id the id of the task to delete
     * @throws TaskNotFoundException if no task has the given id
     */
    void delete(Long id);
}
