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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for Property 3 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 3: Title must be non-blank and within 1–150
 * characters after trimming
 *
 * <p>The property: for any create or full-edit request whose title is missing, whitespace-only, or
 * whose <em>trimmed</em> length falls outside 1–150 characters, the service rejects the request with
 * a validation error and persists nothing (no task stored, no id assigned).
 *
 * <p><strong>Why property-based testing?</strong> "An invalid title is always rejected" must hold
 * across an unbounded space of bad inputs: {@code null}, the empty string, strings of only spaces or
 * tabs of any length, and otherwise-valid text whose trimmed length exceeds 150 (151, 200, ...).
 * Rather than hand-pick a few, jqwik generates many such inputs (at least 100 per property, see
 * {@code tries}) and checks the invariant on each. The generator below deliberately mixes all these
 * shapes so both the "blank after trimming" and "too long after trimming" branches are exercised.
 *
 * <p><strong>How rejection surfaces.</strong> Reading {@link TaskServiceImpl}, the service trims the
 * title and, when the trimmed value is blank or longer than 150, throws a
 * {@link ConstraintViolationException} (Jakarta Validation's own type, which the design maps to
 * HTTP 400). This test asserts on that behaviour directly, since the service is the layer under
 * test — the same trimmed-length rule that a controller would surface as a 400.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy, this
 * property tests <em>our</em> service logic (the title rule), not the database engine.
 * {@link InMemoryTaskRepository} is a tiny in-memory stand-in for {@code TaskRepository}; its
 * {@code count()} lets us assert nothing was written, and it is the same fake used by the other
 * service property tests, keeping the suite consistent.
 *
 * <p><strong>Validates: Requirements 1.5, 1.6, 4.5, 4.6</strong>
 */
class TaskServiceTitleValidationPropertyTest {

    /**
     * Property 3 for the CREATE path (Requirements 1.5, 1.6).
     *
     * <p>Starting from an empty store, a create with an invalid title must throw
     * {@link ConstraintViolationException} and leave the store empty (nothing persisted, no id
     * assigned). The other fields are held valid so the <em>only</em> reason to reject is the title.
     */
    @Property(tries = 200)
    void createRejectsInvalidTitleAndPersistsNothing(
            @ForAll("invalidTitles") String invalidTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus status,
            @ForAll("optionalPriorities") TaskPriority priority) {

        // Fresh service + fake repository per generated case, so state never leaks between runs.
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        CreateTaskRequest request = new CreateTaskRequest(invalidTitle, description, status, priority);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConstraintViolationException.class);

        // Rejection must not persist anything: the store is still empty, so no id was ever assigned.
        assertThat(repository.count()).isZero();
    }

    /**
     * Property 3 for the full-EDIT path (Requirements 4.5, 4.6).
     *
     * <p>We first create one valid task, then attempt a full edit with an invalid title. The edit
     * must be rejected with {@link ConstraintViolationException}, and the store must be untouched:
     * still exactly one task, whose fields equal the originally-seeded (valid) values. This proves a
     * rejected edit neither adds a task nor corrupts the existing one.
     */
    @Property(tries = 200)
    void updateRejectsInvalidTitleAndLeavesTaskUnchanged(
            @ForAll("validTitles") String seedTitle,
            @ForAll("invalidTitles") String invalidTitle,
            @ForAll("optionalDescriptions") String editedDescription,
            @ForAll("optionalStatuses") TaskStatus editedStatus,
            @ForAll("optionalPriorities") TaskPriority editedPriority) {

        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        // Seed one valid task; capture its stored state to compare against after the rejected edit.
        TaskResponse seeded = service.create(new CreateTaskRequest(
                seedTitle, "seed description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));
        TaskResponse before = service.getById(seeded.id());

        UpdateTaskRequest edit =
                new UpdateTaskRequest(invalidTitle, editedDescription, editedStatus, editedPriority);

        assertThatThrownBy(() -> service.update(seeded.id(), edit))
                .isInstanceOf(ConstraintViolationException.class);

        // The rejected edit added nothing and changed nothing: same count, same field values.
        assertThat(repository.count()).isEqualTo(1);
        assertThat(service.getById(seeded.id())).isEqualTo(before);
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Invalid titles: the union of every way a title can fail the "trimmed length 1–150" rule.
     * <ul>
     *   <li>{@code null} — a missing title (the service treats null as blank);</li>
     *   <li>the empty string and whitespace-only strings (spaces/tabs) — trimmed length 0;</li>
     *   <li>titles whose trimmed length exceeds 150 (151 and beyond) — too long.</li>
     * </ul>
     * Mixing these in one generator means each run may pick any failure shape, so both the
     * "blank after trimming" and "too long after trimming" branches of the rule get exercised.
     */
    @Provide
    Arbitrary<String> invalidTitles() {
        // Whitespace-only (including the empty string): trims away to length 0.
        Arbitrary<String> blank = Arbitraries.strings()
                .withChars(' ', '\t')
                .ofMinLength(0)
                .ofMaxLength(20);

        // Non-whitespace bodies of 151..300 characters: trimmed length stays > 150, so too long.
        // Optional surrounding whitespace makes sure trimming is what pushes it over, not raw length.
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

        // Include a bare null so the "missing title" case is covered too.
        return Arbitraries.oneOf(Arbitraries.just(null), blank, tooLong);
    }

    /**
     * Valid titles (used only to seed a task before the edit path): trimmed length 1..150. A
     * non-whitespace core padded with random surrounding whitespace, filtered to stay in bounds.
     */
    @Provide
    Arbitrary<String> validTitles() {
        Arbitrary<String> core = Arbitraries.strings()
                .withCharRange('!', '~')
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
