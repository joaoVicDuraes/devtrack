package com.devtrack.service;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.exception.TaskNotFoundException;
import com.devtrack.model.Task;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;
import com.devtrack.repository.TaskRepository;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default implementation of {@link TaskService}: the home of DevTrack's task business rules.
 *
 * <p>In the Controller &rarr; Service &rarr; Repository architecture this class is the only component
 * that talks to the {@link TaskRepository} (Requirement 8.3), and it maps between the JPA
 * {@link Task} entity and the API's {@link TaskResponse} DTO so the entity never leaks past this
 * boundary (Requirement 8.4).
 *
 * <p>All seven operations declared by {@link TaskService} are implemented here:
 * create and full-edit (task 5.3), and list/get/status/priority/delete (task 5.4). Every write
 * method is {@code @Transactional}; the read methods are marked {@code @Transactional(readOnly = true)}.
 *
 * <p><strong>Why {@code @Service}?</strong> It marks this class as a Spring-managed bean in the
 * service layer, so Spring discovers it during component scanning and injects it wherever a
 * {@link TaskService} is required (for example, into the controller added in task 7).
 *
 * <p>Requirements: 8.3, 8.4, 2.3, 3.1.
 */
@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    /**
     * Constructor injection of the repository.
     *
     * <p><strong>Why constructor injection (rather than field injection with {@code @Autowired})?</strong>
     * The dependency is {@code final}, which makes it required and immutable: the object cannot exist
     * in a half-initialized state, and it is trivial to supply a repository directly in a unit test
     * without any Spring container. When a class has a single constructor, Spring autowires it
     * automatically, so no annotation is needed here.
     *
     * @param taskRepository the Spring Data JPA repository used to persist and read tasks
     */
    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // ---------------------------------------------------------------------
    // Interface methods (stubbed until tasks 5.3 and 5.4)
    // ---------------------------------------------------------------------

    /**
     * Creates a new task from the request (Requirement 1.1).
     *
     * <p>The title is trimmed and its <em>trimmed</em> length is validated to be within 1&ndash;150
     * characters (Requirements 1.5, 1.6); the omitted optional fields fall back to their defaults
     * &mdash; status &rarr; {@code TODO}, priority &rarr; {@code MEDIUM}, description &rarr;
     * {@code ""} (Requirements 1.2, 1.3, 1.4). The trimmed title is what gets persisted.
     *
     * <p><strong>Why {@code @Transactional}?</strong> It wraps the whole method in one database
     * transaction. If the title check throws, or the save fails for any reason, Spring rolls the
     * transaction back so nothing is written &mdash; no half-created task and no id is assigned
     * (Requirements 1.10, 8.11). On a clean run the transaction commits when the method returns.
     *
     * @param request the validated create-task request
     * @return the stored task, including its database-assigned id
     */
    @Override
    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        String trimmedTitle = validateAndTrimTitle(request.title());

        Task task = new Task();
        applyRequest(task, trimmedTitle, request.description(), request.status(), request.priority());

        // save() returns the managed entity, which now carries the id MySQL assigned.
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    /**
     * Lists every stored task in ascending-id order (Requirement 2.1).
     *
     * <p>The ordering is delegated to the repository's derived query
     * {@code findAllByOrderByIdAsc()} rather than sorted here, so the guarantee lives in one place.
     * Each entity is mapped to a {@link TaskResponse} so the JPA entity never leaks past the service
     * boundary.
     *
     * <p><strong>Why {@code @Transactional(readOnly = true)}?</strong> This method only reads, so we
     * mark the transaction read-only. That lets the persistence provider skip dirty-checking (it
     * knows nothing will be written), which is a small, self-documenting optimisation. It is not
     * required for correctness on a single {@code findAll}, but it clearly signals intent.
     *
     * @return all tasks as response DTOs, ordered by ascending id
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listAll() {
        return taskRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Fetches a single task by id (Requirement 3.1).
     *
     * <p>If no task has the given id, a {@link TaskNotFoundException} is thrown; the global exception
     * handler (task 8) maps that to HTTP 404 (Requirement 3.2).
     *
     * @param id the id of the task to fetch
     * @return the matching task as a response DTO
     * @throws TaskNotFoundException if no task has the given id
     */
    @Override
    @Transactional(readOnly = true)
    public TaskResponse getById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return toResponse(task);
    }

    /**
     * Fully replaces an existing task (a "PUT"): overwrites all four editable fields with the
     * request values, applying the same trimming and defaulting as {@link #create(CreateTaskRequest)}
     * (Requirements 4.1, 4.2, 4.3).
     *
     * <p>The task is loaded by id first; if none exists a {@link TaskNotFoundException} is thrown,
     * which the global handler maps to HTTP 404 (Requirement 4.4). The title is trimmed and its
     * trimmed length validated to 1&ndash;150 before anything is written (Requirements 4.5, 4.6).
     *
     * <p><strong>Why {@code @Transactional}?</strong> Same reason as {@link #create}: the load,
     * mutate, and save run in one transaction. If validation fails or the save errors, the
     * transaction rolls back and the previously stored task is left untouched (Requirement 8.11).
     * Because the loaded entity is managed within this transaction, JPA would flush its changes on
     * commit even without the explicit {@code save}; we call {@code save} anyway to make the write
     * intent obvious and to return the persisted result.
     *
     * @param id      the id of the task to replace
     * @param request the validated edit-task request
     * @return the updated task
     * @throws TaskNotFoundException if no task has the given id
     */
    @Override
    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        String trimmedTitle = validateAndTrimTitle(request.title());
        applyRequest(task, trimmedTitle, request.description(), request.status(), request.priority());

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    /**
     * Targeted update of a task's status only (Requirement 5.1).
     *
     * <p>This is deliberately <em>not</em> a full replace: it loads the existing task and overwrites
     * <strong>only</strong> the {@code status} field, leaving title, description, and priority
     * exactly as they were (Requirement 5.2). That is the whole point of a PATCH-style operation vs.
     * the PUT-style {@link #update}: the client sends just the one field it wants to change.
     *
     * <p>If no task has the given id a {@link TaskNotFoundException} is thrown (Requirement 5.3),
     * which the global handler maps to HTTP 404.
     *
     * <p><strong>Why {@code @Transactional}?</strong> The load-mutate-save runs in one transaction;
     * if the save fails it rolls back and the stored task is left untouched (Requirement 8.11).
     *
     * @param id      the id of the task to update
     * @param request the validated status-change request (its {@code status} is guaranteed non-null)
     * @return the updated task
     * @throws TaskNotFoundException if no task has the given id
     */
    @Override
    @Transactional
    public TaskResponse changeStatus(Long id, ChangeStatusRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        // Only the status changes; every other field is intentionally left as-is.
        task.setStatus(request.status());

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    /**
     * Targeted update of a task's priority only (Requirement 6.1).
     *
     * <p>Mirror image of {@link #changeStatus}: it loads the task and overwrites <strong>only</strong>
     * the {@code priority} field, preserving title, description, and status (Requirement 6.2). Throws
     * {@link TaskNotFoundException} when the id is absent (Requirement 6.3).
     *
     * @param id      the id of the task to update
     * @param request the validated priority-change request (its {@code priority} is guaranteed non-null)
     * @return the updated task
     * @throws TaskNotFoundException if no task has the given id
     */
    @Override
    @Transactional
    public TaskResponse changePriority(Long id, ChangePriorityRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        // Only the priority changes; every other field is intentionally left as-is.
        task.setPriority(request.priority());

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    /**
     * Deletes a task by id (Requirement 7.1).
     *
     * <p>We check existence first and throw {@link TaskNotFoundException} when the id is absent
     * (Requirement 7.2) so that deleting a missing task is reported as a 404 rather than silently
     * succeeding. Spring Data's own {@code deleteById} is a no-op for a missing id, which would hide
     * the error, so the explicit {@code existsById} check is what gives us the not-found behaviour.
     *
     * <p><strong>Why {@code @Transactional}?</strong> The existence check and the delete run in one
     * transaction, so they see a consistent view of the data and either both apply or neither does.
     *
     * @param id the id of the task to delete
     * @throws TaskNotFoundException if no task has the given id
     */
    @Override
    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }

    // ---------------------------------------------------------------------
    // Mapping helpers (this task)
    // ---------------------------------------------------------------------

    /**
     * Maps a persisted {@link Task} entity to the outgoing {@link TaskResponse} DTO.
     *
     * <p>This copies all five fields (id, title, description, status, priority) so every task view
     * the API returns is complete (Requirements 2.3, 3.1). Doing the mapping here — rather than
     * serializing the entity directly — keeps the wire format decoupled from the JPA persistence
     * model (Requirement 8.4). Both {@code listAll} and {@code getById} (task 5.4), plus the write
     * operations, will funnel their results through this single helper so the mapping lives in one
     * place.
     *
     * @param task the stored entity to convert
     * @return a response DTO carrying the entity's five fields
     */
    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority()
        );
    }

    /**
     * Applies request-supplied field values onto a {@link Task} entity, resolving omitted optional
     * fields to their defaults.
     *
     * <p>The create and full-edit paths share the same contract — replace title/description/status/
     * priority and default anything omitted (status &rarr; {@code TODO}, priority &rarr;
     * {@code MEDIUM}, description &rarr; {@code ""}). Because {@code CreateTaskRequest} and
     * {@code UpdateTaskRequest} are distinct record types that nonetheless carry identical fields,
     * this helper takes the raw values instead of a specific DTO type, so both callers (added in
     * task 5.3) can reuse it without duplicating the defaulting logic.
     *
     * <p><strong>Note:</strong> title <em>trimming</em> and length validation are deliberately left
     * to task 5.3; this helper only writes the provided title through. It is added here alongside
     * {@link #toResponse(Task)} so the entity&harr;DTO mapping lives together.
     *
     * @param task        the entity to populate (new or existing)
     * @param title       the requested title (written as-is; trimming happens in task 5.3)
     * @param description the requested description, or {@code null} to default to {@code ""}
     * @param status      the requested status, or {@code null} to default to {@code TODO}
     * @param priority    the requested priority, or {@code null} to default to {@code MEDIUM}
     */
    private void applyRequest(Task task, String title, String description,
                              TaskStatus status, TaskPriority priority) {
        task.setTitle(title);
        task.setDescription(description != null ? description : "");
        task.setStatus(status != null ? status : TaskStatus.TODO);
        task.setPriority(priority != null ? priority : TaskPriority.MEDIUM);
    }

    /** Maximum allowed title length after trimming (Requirements 1.6, 4.6). */
    private static final int MAX_TITLE_LENGTH = 150;

    /**
     * Trims the title and enforces the business rule that its <em>trimmed</em> length is 1&ndash;150
     * characters, shared by both create and full edit (Requirements 1.5, 1.6, 4.5, 4.6).
     *
     * <p><strong>Why re-check here when the DTO already has {@code @NotBlank}/{@code @Size}?</strong>
     * Those annotations run against the <em>raw</em> string: {@code @NotBlank} catches null/blank and
     * {@code @Size(max = 150)} bounds the raw length. But the requirement measures the title
     * <em>after</em> trimming, so a value like {@code "   "} padded with 148 leading spaces plus 3
     * real characters could slip past {@code @Size} yet still be wrong once trimmed &mdash; and a
     * title of only spaces trims to empty. This trimmed-length rule is genuine business logic, so it
     * belongs in the service where it can be tested in one place.
     *
     * <p><strong>Why throw {@link ConstraintViolationException}?</strong> The design routes every
     * validation failure to HTTP 400 through the {@code GlobalExceptionHandler} (task 8). The design's
     * error table only lists framework exceptions ({@code MethodArgumentNotValidException}, etc.),
     * so for a service-detected validation failure we throw the Jakarta Validation exception that
     * carries the same "constraint violated" meaning. That keeps the semantics honest (this really is
     * a bean-validation-style rule) and gives task 8 a clear, validation-typed exception to map to a
     * 400 rather than reusing a generic {@code IllegalArgumentException}.
     *
     * @param rawTitle the title as supplied in the request (never trimmed yet)
     * @return the trimmed title, guaranteed to be 1&ndash;150 characters
     * @throws ConstraintViolationException if the trimmed title is blank or longer than 150 characters
     */
    private String validateAndTrimTitle(String rawTitle) {
        String trimmed = rawTitle == null ? "" : rawTitle.trim();
        if (trimmed.isEmpty()) {
            throw new ConstraintViolationException("title must not be blank after trimming", null);
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ConstraintViolationException(
                    "title must be at most " + MAX_TITLE_LENGTH + " characters after trimming", null);
        }
        return trimmed;
    }
}
