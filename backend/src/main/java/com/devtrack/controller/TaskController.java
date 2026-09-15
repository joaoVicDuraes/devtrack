package com.devtrack.controller;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.service.TaskService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The HTTP entry point for tasks: the "web adapter" in the Controller &rarr; Service &rarr;
 * Repository architecture.
 *
 * <p>This class only maps HTTP requests to {@link TaskService} calls and shapes the responses
 * (status codes and bodies). It holds <strong>no business logic</strong> and delegates every
 * operation to the service (Requirement 8.1). It talks only to the service interface and
 * <strong>never</strong> touches the repository directly (Requirement 8.2). Keeping the controller
 * this thin means the rules stay testable without the web layer, and the web layer stays testable
 * without a database.
 *
 * <p><strong>Why {@code @RestController}?</strong> It combines {@code @Controller} and
 * {@code @ResponseBody}: Spring discovers this class during component scanning (which is why it
 * lives under {@code com.devtrack}, the base package of the {@code @SpringBootApplication}) and, for
 * every handler method, serializes the returned value straight to the response body as JSON. So
 * returning a {@link TaskResponse} yields a JSON task; returning a {@code List<TaskResponse>} yields
 * a JSON array. There is no view rendering involved.
 *
 * <p><strong>Why {@code @RequestMapping("/tasks")}?</strong> It sets the common URL prefix for every
 * endpoint here, so each method below only declares the part that follows {@code /tasks} (for
 * example {@code /{id}/status}). Declaring the shared segment once keeps the routes consistent and
 * easy to change in one place.
 *
 * <p><strong>Why constructor injection?</strong> The single {@code final} {@link TaskService} field
 * is required and immutable, and Spring autowires the sole constructor automatically (no
 * {@code @Autowired} needed). This mirrors the service's own injection style and keeps the class
 * trivially constructible in a test with a mocked service.
 *
 * <p><strong>Validation and error handling.</strong> Request-body DTOs are marked {@code @Valid} so
 * Jakarta Validation runs <em>before</em> the service is invoked; if validation fails, Spring
 * short-circuits with an exception and the service is never called (Requirement 8.5). This
 * controller does not catch anything: validation failures, malformed bodies, malformed path ids,
 * and {@code TaskNotFoundException} all surface to the central {@code GlobalExceptionHandler}
 * (task 8), which maps them to the correct 4xx/5xx responses. That is why there are no try/catch
 * blocks here.
 *
 * <p>Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 8.2, 8.5.
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    /**
     * Constructor injection of the service the controller delegates to.
     *
     * @param taskService the business-logic layer that performs every task operation
     */
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Creates a task ({@code POST /tasks}).
     *
     * <p>The body is validated ({@code @Valid}) before the service runs; on success we return HTTP
     * 201 Created and the stored task, including its assigned id (Requirement 1.1).
     *
     * <p><strong>Why {@code ResponseEntity} here (rather than {@code @ResponseStatus})?</strong>
     * Create is the one endpoint whose status differs from the default 200, and returning a
     * {@code ResponseEntity} makes the 201 explicit and local to this method. (Either approach is
     * valid; we use {@code @ResponseStatus} on delete below to show the alternative.)
     *
     * @param request the validated create-task request body
     * @return HTTP 201 with the created task as the body
     */
    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists every task in ascending-id order ({@code GET /tasks}); HTTP 200 with a JSON array that
     * is empty when no task exists (Requirement 2.1).
     *
     * @return all tasks as response DTOs
     */
    @GetMapping
    public List<TaskResponse> listAll() {
        return taskService.listAll();
    }

    /**
     * Retrieves one task by id ({@code GET /tasks/{id}}); HTTP 200 with the task (Requirement 3.1).
     *
     * <p>A missing id causes the service to throw {@code TaskNotFoundException}, which the global
     * handler maps to 404; a non-numeric id (e.g. {@code /tasks/abc}) fails path-variable conversion
     * and is mapped to 400 by the same handler.
     *
     * @param id the id of the task to retrieve
     * @return the matching task
     */
    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable Long id) {
        return taskService.getById(id);
    }

    /**
     * Fully edits a task ({@code PUT /tasks/{id}}); HTTP 200 with the updated task (Requirement 4.1).
     *
     * <p>PUT is a full replace: the validated body supplies all four editable fields (the service
     * applies defaulting for any omitted optional field). A missing id surfaces as 404.
     *
     * @param id      the id of the task to replace
     * @param request the validated edit-task request body
     * @return the updated task
     */
    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(id, request);
    }

    /**
     * Changes only a task's status ({@code PATCH /tasks/{id}/status}); HTTP 200 with the updated task
     * (Requirement 5.1).
     *
     * <p>PATCH signals a partial update: only the status changes, every other field is preserved by
     * the service. A missing id surfaces as 404.
     *
     * @param id      the id of the task to update
     * @param request the validated status-change request body
     * @return the updated task
     */
    @PatchMapping("/{id}/status")
    public TaskResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        return taskService.changeStatus(id, request);
    }

    /**
     * Changes only a task's priority ({@code PATCH /tasks/{id}/priority}); HTTP 200 with the updated
     * task (Requirement 6.1).
     *
     * <p>Mirror image of {@link #changeStatus}: only the priority changes, every other field is
     * preserved. A missing id surfaces as 404.
     *
     * @param id      the id of the task to update
     * @param request the validated priority-change request body
     * @return the updated task
     */
    @PatchMapping("/{id}/priority")
    public TaskResponse changePriority(@PathVariable Long id, @Valid @RequestBody ChangePriorityRequest request) {
        return taskService.changePriority(id, request);
    }

    /**
     * Deletes a task ({@code DELETE /tasks/{id}}); HTTP 204 No Content on success (Requirement 7.1).
     *
     * <p><strong>Why {@code @ResponseStatus(NO_CONTENT)} and a {@code void} return?</strong> A
     * successful delete has no body to send back, so 204 (success, no content) is the right code.
     * Annotating the method with {@code @ResponseStatus} lets Spring set that status automatically
     * when the method returns normally, so we can keep the method {@code void} instead of wrapping an
     * empty {@code ResponseEntity}. A missing id makes the service throw {@code TaskNotFoundException}
     * (mapped to 404); a non-numeric id fails conversion and is mapped to 400.
     *
     * @param id the id of the task to delete
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        taskService.delete(id);
    }
}
