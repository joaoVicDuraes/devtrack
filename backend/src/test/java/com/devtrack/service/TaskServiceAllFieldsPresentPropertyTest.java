package com.devtrack.service;

import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: devtrack-task-management, Property 11: Every task view includes all five fields

/**
 * Property-based test for Property 11 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 11: Every task view includes all five fields
 *
 * <p>The property: for any task returned by a list or get operation, the {@link TaskResponse}
 * includes the id, title, description, status, and priority — each equal to the task's stored value.
 * In other words, no task view the API hands back is ever partial: every one of the five fields is
 * present (non-null) and carries the value the service persisted.
 *
 * <p><strong>Why property-based testing?</strong> "Every view is complete" must hold no matter what
 * was stored: any valid title (with surrounding whitespace the service trims), any description
 * including the empty string, and every combination of the status/priority enums (or their omission,
 * which the service defaults). Hand-picked examples could easily miss a mapping bug that only shows
 * up for, say, an omitted description or a particular enum value. jqwik instead generates many task
 * shapes (at least 100 per property, see {@code tries}) and checks the invariant on each, through
 * <em>both</em> read paths — {@code getById} and {@code listAll}.
 *
 * <p><strong>What "stored value" means here.</strong> The service applies defaulting and trimming on
 * write (title trimmed; description &rarr; {@code ""}, status &rarr; {@code TODO}, priority &rarr;
 * {@code MEDIUM} when omitted). The {@link TaskResponse} returned by {@code create} therefore already
 * reflects exactly what was stored, so it is the natural source of truth: we assert that the same
 * response comes back through {@code getById} and appears in {@code listAll}, and — independently —
 * that none of the five fields is null. Comparing whole records (records give us value-based
 * {@code equals}) checks all five fields at once; the explicit non-null checks document that
 * "includes" means "present", catching a hypothetical mapping that dropped a field to null.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy, this
 * property tests <em>our</em> service logic (the entity&rarr;DTO mapping in {@code toResponse}), not
 * the database engine. {@link InMemoryTaskRepository} is the same in-memory stand-in used by the
 * other service property tests, so the suite stays fast, dependency-free, and consistent.
 *
 * <p><strong>Validates: Requirements 2.3, 3.1</strong>
 */
class TaskServiceAllFieldsPresentPropertyTest {

    /**
     * Property 11 via the GET path (Requirement 3.1).
     *
     * <p>Create an arbitrary task, then read it back with {@code getById}. The returned
     * {@link TaskResponse} must equal the one {@code create} returned (same five field values) and
     * every one of its five fields must be non-null.
     */
    @Property(tries = 200)
    void getByIdReturnsAllFiveFieldsEqualToStoredValues(
            @ForAll("validTitles") String rawTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus status,
            @ForAll("optionalPriorities") TaskPriority priority) {

        // Fresh service + fake repository per generated case, so tasks never leak between runs.
        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        // The response create() returns already reflects the stored (trimmed/defaulted) values.
        TaskResponse stored = service.create(new CreateTaskRequest(rawTitle, description, status, priority));

        TaskResponse view = service.getById(stored.id());

        assertAllFieldsPresentAndEqual(view, stored);
    }

    /**
     * Property 11 via the LIST path (Requirement 2.3).
     *
     * <p>Create an arbitrary set of tasks, then for each task found in {@code listAll()} assert all
     * five fields are present and equal to what was stored. We match each listed view to its stored
     * counterpart by id, so this holds regardless of how many tasks exist or their order.
     */
    @Property(tries = 200)
    void listAllReturnsAllFiveFieldsEqualToStoredValues(
            @ForAll("taskRequestLists") List<CreateTaskRequest> requests) {

        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        // Create each requested task, remembering the stored response the service returned.
        List<TaskResponse> stored = new ArrayList<>();
        for (CreateTaskRequest request : requests) {
            stored.add(service.create(request));
        }

        List<TaskResponse> listed = service.listAll();

        // Same number of views as tasks stored, and each listed view is a complete, faithful copy.
        assertThat(listed).hasSameSizeAs(stored);
        for (TaskResponse view : listed) {
            TaskResponse match = stored.stream()
                    .filter(s -> s.id().equals(view.id()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "listAll() returned a task with unknown id " + view.id()));
            assertAllFieldsPresentAndEqual(view, match);
        }
    }

    // ---------------------------------------------------------------------
    // Shared assertion
    // ---------------------------------------------------------------------

    /**
     * Asserts a returned view carries all five fields, each present (non-null) and equal to the
     * stored value. The whole-record equality check covers all five field values at once (records
     * compare by value); the per-field non-null checks make "includes the field" explicit.
     */
    private static void assertAllFieldsPresentAndEqual(TaskResponse view, TaskResponse stored) {
        assertThat(view.id()).isNotNull().isEqualTo(stored.id());
        assertThat(view.title()).isNotNull().isEqualTo(stored.title());
        assertThat(view.description()).isNotNull().isEqualTo(stored.description());
        assertThat(view.status()).isNotNull().isEqualTo(stored.status());
        assertThat(view.priority()).isNotNull().isEqualTo(stored.priority());
        // Redundant-but-clear: the two views are identical across all five fields.
        assertThat(view).isEqualTo(stored);
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Sets of create requests of varying size (0..30), so the list path is checked from the empty
     * store up to thirty tasks. Naming a {@code List} provider directly is what jqwik expects when
     * the parameter type is a {@code List}.
     */
    @Provide
    Arbitrary<List<CreateTaskRequest>> taskRequestLists() {
        return taskRequests().list().ofMinSize(0).ofMaxSize(30);
    }

    /**
     * A single valid create request: a valid title plus optional description, status, and priority
     * (each may be omitted as {@code null}, exercising the service's defaulting).
     */
    @Provide
    Arbitrary<CreateTaskRequest> taskRequests() {
        return Combinators.combine(
                        validTitles(),
                        optionalDescriptions(),
                        optionalStatuses(),
                        optionalPriorities())
                .as(CreateTaskRequest::new);
    }

    /**
     * Valid titles: values whose trimmed length is 1..150, matching what the service persists. A
     * non-whitespace core padded with random surrounding whitespace, filtered to stay in bounds.
     */
    @Provide
    Arbitrary<String> validTitles() {
        Arbitrary<String> core = Arbitraries.strings()
                .withCharRange('!', '~')      // printable ASCII, no whitespace
                .ofMinLength(1)
                .ofMaxLength(150);
        Arbitrary<String> padding = Arbitraries.strings()
                .withChars(' ', '\t')
                .ofMaxLength(10);
        return Combinators.combine(padding, core, padding)
                .as((lead, body, trail) -> lead + body + trail)
                .filter(s -> {
                    int len = s.trim().length();
                    return len >= 1 && len <= 150;
                });
    }

    /** Optional descriptions: {@code null} (omitted -> defaults to {@code ""}) or 0..2000 chars. */
    @Provide
    Arbitrary<String> optionalDescriptions() {
        Arbitrary<String> present = Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(2000);
        return Arbitraries.oneOf(Arbitraries.just(null), present);
    }

    /** Optional status: {@code null} (omitted -> defaults to TODO) or any valid enum value. */
    @Provide
    Arbitrary<TaskStatus> optionalStatuses() {
        return Arbitraries.oneOf(Arbitraries.just(null), Arbitraries.of(TaskStatus.class));
    }

    /** Optional priority: {@code null} (omitted -> defaults to MEDIUM) or any valid enum value. */
    @Provide
    Arbitrary<TaskPriority> optionalPriorities() {
        return Arbitraries.oneOf(Arbitraries.just(null), Arbitraries.of(TaskPriority.class));
    }
}
