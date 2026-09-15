package com.devtrack.service;

import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
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
 * Property-based test for Property 9 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 9: Delete makes a task absent
 *
 * <p>The property (from design.md): for any existing task, deleting it returns HTTP 204 and every
 * subsequent get for that id returns HTTP 404. At the service level — the layer this test exercises —
 * that means: after {@code delete(id)} succeeds, a subsequent {@code getById(id)} throws
 * {@link TaskNotFoundException} (the signal the {@code GlobalExceptionHandler} maps to 404), and no
 * task with that id remains in storage.
 *
 * <p><strong>Why this deserves its own property.</strong> Deletion is only "correct" if the task is
 * genuinely gone afterward, for <em>every</em> task regardless of its field values or how many
 * siblings share the store. Rather than delete one hand-picked task, jqwik seeds the store with an
 * arbitrary set of valid tasks, picks an arbitrary one of them, deletes it, and checks the
 * after-state — surfacing edge cases (deleting the only task, the first, or the last) that fixed
 * examples would miss. The complementary "delete on an <em>absent</em> id returns 404" case is
 * Property 8's job, so this test focuses purely on the removal of an <em>existing</em> task.
 *
 * <p><strong>What "absent afterward" is checked to mean.</strong> Three independent observations,
 * each of which would fail if delete were a no-op or removed the wrong row:
 * <ul>
 *   <li>{@code getById(deletedId)} now throws {@link TaskNotFoundException};</li>
 *   <li>the surviving tasks are exactly the ones we did <em>not</em> delete (same ids, same fields,
 *       same order) — so a delete that removed a neighbour would be caught;</li>
 *   <li>the stored count dropped by exactly one.</li>
 * </ul>
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy this
 * property tests <em>our</em> service logic (that delete removes the targeted task and nothing else),
 * not the database engine. {@link InMemoryTaskRepository} is the same in-memory stand-in the other
 * service property tests use, and its ids mirror MySQL's {@code AUTO_INCREMENT}, so the seeded ids
 * (1, 2, 3, …) are predictable and a "pick an existing id" strategy is sound.
 *
 * <p><strong>Validates: Requirements 7.1</strong>
 */
class TaskServiceDeleteMakesAbsentPropertyTest {

    /**
     * Property 9 (Requirement 7.1).
     *
     * <p>Seed the store with a non-empty set of valid tasks, choose one of them, delete it, then
     * assert the task is absent: {@code getById} throws {@link TaskNotFoundException}, the store no
     * longer contains that id while every other task is untouched, and the count dropped by one.
     */
    @Property(tries = 200)
    void deletingAnExistingTaskMakesItAbsent(
            @ForAll("nonEmptySeedLists") List<CreateTaskRequest> seeds,
            @ForAll("selectionSeed") int selectionSeed) {

        // Fresh service + fake repository per generated case, so state never leaks between runs.
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        // Create the seed tasks; their assigned ids (1..n from the fake's AUTO_INCREMENT) let us
        // pick a known-existing target below.
        for (CreateTaskRequest seed : seeds) {
            service.create(seed);
        }

        // Snapshot the store before deleting so we can compute the exact expected survivors.
        List<TaskResponse> before = service.listAll();
        long countBefore = repository.count();

        // Pick one existing task to delete. Math.floorMod keeps the index in range for any int
        // (including negatives), so every generated selectionSeed maps to a real, stored task.
        int targetIndex = Math.floorMod(selectionSeed, before.size());
        long deletedId = before.get(targetIndex).id();

        // The operation under test: deleting an existing task. It returns void; the HTTP 204 the
        // design speaks of is the controller's translation of this successful, body-less return.
        service.delete(deletedId);

        // 1) A subsequent get for that id signals not-found (the 404 the design requires).
        assertThatThrownBy(() -> service.getById(deletedId))
                .as("after delete, getById on the removed id must signal not-found")
                .isInstanceOf(TaskNotFoundException.class);

        // 2) Exactly that task is gone; every other task is byte-for-byte unchanged and still ordered.
        List<TaskResponse> expectedSurvivors = before.stream()
                .filter(task -> task.id() != deletedId)
                .toList();
        assertThat(service.listAll())
                .as("delete must remove only the targeted task and leave the rest unchanged")
                .isEqualTo(expectedSurvivors);

        // 3) The stored count dropped by exactly one.
        assertThat(repository.count())
                .as("delete must reduce the stored task count by exactly one")
                .isEqualTo(countBefore - 1);
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Lists of 1..5 valid create requests used to seed the store. The list is non-empty because this
     * property is about deleting an <em>existing</em> task; varying the size (including a single-task
     * store) exercises deleting the only task as well as one of several neighbours.
     */
    @Provide
    Arbitrary<List<CreateTaskRequest>> nonEmptySeedLists() {
        return validCreateRequests().list().ofMinSize(1).ofMaxSize(5);
    }

    /**
     * An arbitrary int used to select which seeded task to delete. It is reduced modulo the store
     * size at use, so any value (including negatives) maps to a valid index and every position —
     * first, middle, last — gets exercised across runs.
     */
    @Provide
    Arbitrary<Integer> selectionSeed() {
        return Arbitraries.integers();
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
