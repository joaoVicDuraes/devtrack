package com.devtrack.service;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.exception.TaskNotFoundException;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for Property 8 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 8: Operations on an absent id return 404
 *
 * <p>The property (from design.md): for any well-formed id that is not currently stored, a get,
 * edit, change-status, change-priority, or delete request returns HTTP 404 and leaves storage
 * unchanged.
 *
 * <p><strong>Why this deserves its own property.</strong> Every "missing id" requirement (3.2, 4.4,
 * 5.3, 6.3, 7.2) is served by the same mechanism: the service throws {@link TaskNotFoundException},
 * which the {@code GlobalExceptionHandler} (task 8) maps to HTTP 404. This property proves that
 * mechanism holds for <em>all five</em> read/write/delete operations, across the whole space of
 * well-formed-but-absent ids (never-created ids and an id that was deleted), and — just as
 * importantly — that a not-found operation is a pure lookup with <em>no side effect</em>: the set of
 * stored tasks and their field values are byte-for-byte identical afterward. Checking that
 * side-effect-free guarantee over many generated stores and ids is what makes this a property rather
 * than five hand-picked examples.
 *
 * <p><strong>How "well-formed but absent" ids are generated.</strong> The service takes a
 * {@code Long} id, so a well-formed id is simply a non-null {@code Long}; "malformed id" (e.g. the
 * path {@code /tasks/abc}) is a web-layer concern rejected before the service runs (Requirement 3.3),
 * not a service-layer case. To guarantee an id is <em>absent</em>, the test seeds the store with a
 * known set of tasks (whose ids the {@link InMemoryTaskRepository} assigns as 1, 2, 3, …) and then
 * derives ids that provably fall outside that set: ids at or beyond {@code seeds.size() + 1}, plus
 * zero and negative ids (well-formed {@code Long}s that MySQL AUTO_INCREMENT never assigns). This
 * covers the two flavours Requirement 7.2 calls out — an id that never existed and an id that could
 * be a stale reference — without depending on any particular id value existing.
 *
 * <p><strong>Why compare the whole store before and after?</strong> "Leaves storage unchanged" is a
 * claim about the entire repository, not just the id we probed. The test snapshots
 * {@code listAll()} (ordered, all fields) and the {@code count()} before each operation and asserts
 * both are identical afterward, so a delete that somehow removed a neighbour, or an update that
 * mutated another task, would be caught here.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy this
 * property tests <em>our</em> service logic (missing-id detection and its lack of side effects), not
 * the database engine. {@link InMemoryTaskRepository} is the same in-memory stand-in the other
 * service property tests use, and its ids mirror MySQL's AUTO_INCREMENT so "absent id" reasoning is
 * sound.
 *
 * <p><strong>Validates: Requirements 3.2, 4.4, 5.3, 6.3, 7.2</strong>
 */
class TaskServiceAbsentIdNotFoundPropertyTest {

    /**
     * Property 8 for every operation that targets an id (Requirements 3.2, 4.4, 5.3, 6.3, 7.2).
     *
     * <p>Seed the store with an arbitrary set of valid tasks and snapshot it. Then, against an id
     * that is provably not stored, invoke each of the five id-targeting operations — getById,
     * update, changeStatus, changePriority, delete — and assert each throws
     * {@link TaskNotFoundException} (the 404 signal) and leaves the whole store unchanged.
     */
    @Property(tries = 200)
    void operationsOnAbsentIdThrowNotFoundAndLeaveStoreUnchanged(
            @ForAll("seedTaskLists") List<CreateTaskRequest> seeds,
            @ForAll("absentIdOffsets") long absentIdOffset) {

        // Fresh service + fake repository per generated case, so state never leaks between runs.
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        for (CreateTaskRequest seed : seeds) {
            service.create(seed);
        }

        // Ids 1..seeds.size() are the only ones assigned. Derive an id guaranteed to be absent:
        //  - offsets >= 1 land at seeds.size() + offset (never existed, above the high-water mark);
        //  - offset 0 gives id 0, and negative offsets give negative ids — both well-formed Long
        //    values that AUTO_INCREMENT never hands out. This covers "never existed" ids without
        //    relying on any specific id being present.
        long absentId = absentIdOffset >= 1 ? seeds.size() + absentIdOffset : absentIdOffset;

        // Snapshot the full store BEFORE the not-found operations.
        long countBefore = repository.count();
        List<TaskResponse> before = service.listAll();

        // A representative, fully-valid request body for the write operations. The id is absent, so
        // the not-found check must fire before any of these values could be written.
        UpdateTaskRequest editBody =
                new UpdateTaskRequest("a valid title", "a description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH);
        ChangeStatusRequest statusBody = new ChangeStatusRequest(TaskStatus.DONE);
        ChangePriorityRequest priorityBody = new ChangePriorityRequest(TaskPriority.LOW);

        // get-task on an absent id -> 404 (Requirement 3.2)
        assertThatThrownBy(() -> service.getById(absentId))
                .as("getById on an absent id must signal not-found")
                .isInstanceOf(TaskNotFoundException.class);

        // edit-task on an absent id -> 404 (Requirement 4.4)
        assertThatThrownBy(() -> service.update(absentId, editBody))
                .as("update on an absent id must signal not-found")
                .isInstanceOf(TaskNotFoundException.class);

        // change-status on an absent id -> 404 (Requirement 5.3)
        assertThatThrownBy(() -> service.changeStatus(absentId, statusBody))
                .as("changeStatus on an absent id must signal not-found")
                .isInstanceOf(TaskNotFoundException.class);

        // change-priority on an absent id -> 404 (Requirement 6.3)
        assertThatThrownBy(() -> service.changePriority(absentId, priorityBody))
                .as("changePriority on an absent id must signal not-found")
                .isInstanceOf(TaskNotFoundException.class);

        // delete-task on a well-formed but non-stored id -> 404 (Requirement 7.2)
        assertThatThrownBy(() -> service.delete(absentId))
                .as("delete on an absent id must signal not-found")
                .isInstanceOf(TaskNotFoundException.class);

        // After all five not-found operations the whole store is byte-for-byte identical: none of
        // them wrote, removed, or mutated anything.
        assertThat(repository.count())
                .as("not-found operations must not change the number of stored tasks")
                .isEqualTo(countBefore);
        assertThat(service.listAll())
                .as("not-found operations must leave every stored task's fields unchanged")
                .isEqualTo(before);
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Lists of 0..5 valid create requests used to seed the store. Varying the size (including empty)
     * exercises both the "no tasks yet" store — where every id is absent — and multi-task stores,
     * so the "whole store unchanged" guarantee is checked against real neighbours too.
     */
    @Provide
    Arbitrary<List<CreateTaskRequest>> seedTaskLists() {
        return validCreateRequests().list().ofMinSize(0).ofMaxSize(5);
    }

    /**
     * Offsets used to derive a provably-absent id from the seeded store size.
     *
     * <p>Values 1..1000 produce ids strictly above the highest assigned id ("never existed"); 0
     * produces id 0; and -1000..-1 produce negative ids. Ids 0 and negatives are well-formed
     * {@code Long} values that a MySQL AUTO_INCREMENT column never assigns, so they are guaranteed
     * absent regardless of how many tasks were seeded.
     */
    @Provide
    Arbitrary<Long> absentIdOffsets() {
        return Arbitraries.longs().between(-1000L, 1000L);
    }

    /** A fully-valid create request: valid title, and any valid optional fields. */
    @Provide
    Arbitrary<CreateTaskRequest> validCreateRequests() {
        return Combinators.combine(
                        validTitles(), optionalDescriptions(), optionalStatuses(), optionalPriorities())
                .as(CreateTaskRequest::new);
    }

    /**
     * Valid titles: values whose <em>trimmed</em> length is 1..150 (what the service persists). A
     * non-whitespace core padded with random surrounding whitespace, filtered to stay within bounds.
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
