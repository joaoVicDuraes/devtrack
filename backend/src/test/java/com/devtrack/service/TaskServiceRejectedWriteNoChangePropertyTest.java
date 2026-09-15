package com.devtrack.service;

import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import jakarta.validation.ConstraintViolationException;

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
 * Property-based test for Property 6 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 6: A rejected or invalid write does not change
 * stored state
 *
 * <p>The property (from design.md): for any write request (create, edit, change-status,
 * change-priority) that is rejected for a validation reason, the set of stored tasks and their field
 * values are identical before and after the request; in particular no id is assigned on a rejected
 * create.
 *
 * <p><strong>Why this deserves its own property, distinct from Properties 3 and 4.</strong> Those
 * properties prove that an individual invalid <em>title</em> (Property 3) or over-long
 * <em>description</em> (Property 4) is rejected. This property is about the <em>side effect</em> of a
 * rejection: it guarantees the rejection is <em>atomic</em> — the entire store, not just "the one
 * task we were touching", is byte-for-byte identical afterward (Requirement 8.11). To make that
 * meaningful we seed the repository with several tasks first, snapshot the whole store, then issue a
 * rejected write and assert the whole snapshot still holds. A create that partially wrote and then
 * failed, or an edit that corrupted a neighbouring task, would be caught here but not by Properties
 * 3/4.
 *
 * <p><strong>Which rejection does the service itself own?</strong> Reading {@link TaskServiceImpl},
 * the write path the <em>service</em> rejects on its own is the trimmed-title rule
 * ({@code validateAndTrimTitle} throws {@link ConstraintViolationException} when the trimmed title is
 * blank or longer than 150). That covers create (Requirement 1.10) and full-edit (Requirement 8.11)
 * at the service layer, so this test drives those two paths with an invalid title and checks the
 * store is untouched.
 *
 * <p><strong>What about change-status / change-priority with a missing or invalid enum (Requirements
 * 5.4, 6.4)?</strong> Those requests carry an enum value that is validated <em>before</em> the
 * service runs: {@code ChangeStatusRequest}/{@code ChangePriorityRequest} declare the field
 * {@code @NotNull}, and an unknown enum string can never even be deserialized into the request
 * (Jackson rejects it as a malformed body). By the time {@link TaskServiceImpl#changeStatus} /
 * {@link TaskServiceImpl#changePriority} run, the enum is guaranteed non-null and valid, so the
 * service has no invalid-enum path to exercise — the rejection happens at the controller's
 * {@code @Valid}/parse step (Requirement 8.5) and the service is never reached, which by definition
 * leaves the store unchanged. This test therefore covers what the service is responsible for (the
 * title-rejection write paths) and documents, here, why the enum cases live at the web layer rather
 * than the service layer.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy this
 * property tests <em>our</em> service logic (that a rejection writes nothing), not the database
 * engine. {@link InMemoryTaskRepository} is the same in-memory stand-in used by the other service
 * property tests; snapshotting it via {@code findAllByOrderByIdAsc()} lets us compare the full stored
 * state before and after, and {@code count()} confirms no id was assigned on a rejected create.
 *
 * <p><strong>Validates: Requirements 1.10, 5.4, 6.4, 8.11</strong>
 */
class TaskServiceRejectedWriteNoChangePropertyTest {

    /**
     * Property 6 for the rejected-CREATE path (Requirements 1.10, 8.11).
     *
     * <p>Seed the store with several valid tasks and snapshot it. Then attempt a create with an
     * invalid title (blank after trimming, or trimmed length &gt; 150) — everything else valid so the
     * title is the sole reason to reject. The service must throw {@link ConstraintViolationException}
     * and leave the store identical: same task count (so no new id was ever assigned) and the same
     * ordered field values.
     */
    @Property(tries = 200)
    void rejectedCreateLeavesStoredStateUnchanged(
            @ForAll("seedTaskLists") List<CreateTaskRequest> seeds,
            @ForAll("invalidTitles") String invalidTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus status,
            @ForAll("optionalPriorities") TaskPriority priority) {

        // Fresh service + fake repository per generated case, so state never leaks between runs.
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        seedStore(service, seeds);
        List<TaskResponse> before = service.listAll();

        CreateTaskRequest rejected =
                new CreateTaskRequest(invalidTitle, description, status, priority);

        assertThatThrownBy(() -> service.create(rejected))
                .isInstanceOf(ConstraintViolationException.class);

        // The whole store is unchanged: no partial write, and — crucially — no id was assigned,
        // which shows up as the count and the ordered snapshot being exactly what they were.
        assertThat(repository.count())
                .as("a rejected create must not add a task or assign an id")
                .isEqualTo(seeds.size());
        assertThat(service.listAll())
                .as("a rejected create must leave every stored task's fields unchanged")
                .isEqualTo(before);
    }

    /**
     * Property 6 for the rejected-EDIT path (Requirement 8.11).
     *
     * <p>Seed several tasks and snapshot the store. Then attempt a full edit of one existing task
     * with an invalid title. The service must throw {@link ConstraintViolationException} and leave
     * the store identical — proving the rejected edit neither corrupts the target task nor disturbs
     * any of its neighbours.
     */
    @Property(tries = 200)
    void rejectedEditLeavesStoredStateUnchanged(
            @ForAll("seedTaskLists") List<CreateTaskRequest> seeds,
            @ForAll("invalidTitles") String invalidTitle,
            @ForAll("optionalDescriptions") String editedDescription,
            @ForAll("optionalStatuses") TaskStatus editedStatus,
            @ForAll("optionalPriorities") TaskPriority editedPriority) {

        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        // Guarantee at least one task exists to edit: prepend a known-valid seed to the generated set.
        List<CreateTaskRequest> allSeeds = withGuaranteedTask(seeds);
        seedStore(service, allSeeds);

        List<TaskResponse> before = service.listAll();
        // Edit the first stored task (any existing id works; the title rule fails before any write).
        Long targetId = before.get(0).id();

        UpdateTaskRequest rejected =
                new UpdateTaskRequest(invalidTitle, editedDescription, editedStatus, editedPriority);

        assertThatThrownBy(() -> service.update(targetId, rejected))
                .isInstanceOf(ConstraintViolationException.class);

        assertThat(repository.count())
                .as("a rejected edit must not change the number of stored tasks")
                .isEqualTo(allSeeds.size());
        assertThat(service.listAll())
                .as("a rejected edit must leave every stored task's fields unchanged")
                .isEqualTo(before);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Persists each seed request through the service so the store holds real, valid tasks. */
    private static void seedStore(TaskService service, List<CreateTaskRequest> seeds) {
        for (CreateTaskRequest seed : seeds) {
            service.create(seed);
        }
    }

    /**
     * Returns a seed list guaranteed to contain at least one task, by prepending a known-valid
     * request to the generated list. Used by the edit path, which needs an existing id to target.
     */
    private static List<CreateTaskRequest> withGuaranteedTask(List<CreateTaskRequest> seeds) {
        CreateTaskRequest guaranteed =
                new CreateTaskRequest("seed task", "seed description", TaskStatus.TODO, TaskPriority.LOW);
        List<CreateTaskRequest> all = new java.util.ArrayList<>();
        all.add(guaranteed);
        all.addAll(seeds);
        return all;
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Lists of 0..5 valid create requests used to seed the store. Varying the size (including empty)
     * exercises the "no tasks yet" case as well as multi-task stores, so the "whole store unchanged"
     * guarantee is checked against neighbours, not just a single task.
     */
    @Provide
    Arbitrary<List<CreateTaskRequest>> seedTaskLists() {
        return validCreateRequests().list().ofMinSize(0).ofMaxSize(5);
    }

    /** A fully-valid create request: valid title, and any valid optional fields. */
    @Provide
    Arbitrary<CreateTaskRequest> validCreateRequests() {
        return Combinators.combine(
                        validTitles(), optionalDescriptions(), optionalStatuses(), optionalPriorities())
                .as(CreateTaskRequest::new);
    }

    /**
     * Invalid titles: every way a title can fail the "trimmed length 1–150" rule — {@code null},
     * whitespace-only (trims to length 0), or a body whose trimmed length exceeds 150. Mirrors the
     * generator used by the title-validation property so both rejection branches are exercised.
     */
    @Provide
    Arbitrary<String> invalidTitles() {
        Arbitrary<String> blank = Arbitraries.strings()
                .withChars(' ', '\t')
                .ofMinLength(0)
                .ofMaxLength(20);

        Arbitrary<String> tooLongBody = Arbitraries.strings()
                .withCharRange('!', '~')      // printable ASCII, no whitespace
                .ofMinLength(151)
                .ofMaxLength(300);
        Arbitrary<String> padding = Arbitraries.strings()
                .withChars(' ', '\t')
                .ofMaxLength(10);
        Arbitrary<String> tooLong = Combinators.combine(padding, tooLongBody, padding)
                .as((lead, body, trail) -> lead + body + trail)
                .filter(s -> s.trim().length() > 150);

        return Arbitraries.oneOf(Arbitraries.just(null), blank, tooLong);
    }

    /**
     * Valid titles: trimmed length 1..150. A non-whitespace core padded with random surrounding
     * whitespace, filtered to stay within the trimmed bound.
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

    /** Optional descriptions: {@code null} (omitted) or a string of 0..2000 characters. */
    @Provide
    Arbitrary<String> optionalDescriptions() {
        Arbitrary<String> present = Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(2000);
        return Arbitraries.oneOf(Arbitraries.just(null), present);
    }

    /** Optional status: {@code null} (omitted) or any valid enum value. */
    @Provide
    Arbitrary<TaskStatus> optionalStatuses() {
        return Arbitraries.oneOf(Arbitraries.just(null), Arbitraries.of(TaskStatus.class));
    }

    /** Optional priority: {@code null} (omitted) or any valid enum value. */
    @Provide
    Arbitrary<TaskPriority> optionalPriorities() {
        return Arbitraries.oneOf(Arbitraries.just(null), Arbitraries.of(TaskPriority.class));
    }
}
