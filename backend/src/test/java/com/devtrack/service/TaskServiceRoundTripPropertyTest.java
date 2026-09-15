package com.devtrack.service;

import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 1 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 1: Write-then-read round trip preserves values
 *
 * <p>The property: for any valid write request (a create or a full edit), reading the resulting
 * task back by its id returns exactly the field values that were written, with any omitted optional
 * field resolved to its default (status {@code TODO}, priority {@code MEDIUM}, description {@code ""})
 * and the title trimmed by the service.
 *
 * <p><strong>Why property-based testing?</strong> The create/edit round trip must hold across a huge
 * input space: titles of any length and surrounding whitespace, descriptions up to 2000 characters,
 * and every combination of the enum values (or their omission). Rather than hand-pick a few
 * examples, jqwik generates many inputs (at least 100 per property, see {@code tries}) and checks the
 * invariant on each, surfacing edge cases example tests would miss.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy, this
 * property tests <em>our</em> service logic (trimming, defaulting, mapping), not the database engine.
 * {@link InMemoryTaskRepository} is a tiny in-memory stand-in for {@code TaskRepository} that assigns
 * ids the way MySQL's {@code AUTO_INCREMENT} would, so the test is fast and needs no external MySQL.
 *
 * <p><strong>Validates: Requirements 1.1, 3.1, 4.1, 4.2</strong>
 */
class TaskServiceRoundTripPropertyTest {

    /**
     * Property 1 for the CREATE path.
     *
     * <p>After {@code create(request)}, reading the task back by its assigned id returns the trimmed
     * title, the description (or {@code ""} when omitted), the status (or {@code TODO} when omitted),
     * and the priority (or {@code MEDIUM} when omitted).
     */
    @Property(tries = 200)
    void createThenReadPreservesWrittenValues(
            @ForAll("validTitles") String rawTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus status,
            @ForAll("optionalPriorities") TaskPriority priority) {

        // Fresh service + fake repository per generated case, so tasks never leak between runs.
        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        CreateTaskRequest request = new CreateTaskRequest(rawTitle, description, status, priority);
        TaskResponse created = service.create(request);

        // Read the task back by the id the service assigned, then compare field by field.
        TaskResponse readBack = service.getById(created.id());

        assertThat(readBack.id()).isEqualTo(created.id());
        assertThat(readBack.title()).isEqualTo(rawTitle.trim());
        assertThat(readBack.description()).isEqualTo(expectedDescription(description));
        assertThat(readBack.status()).isEqualTo(expectedStatus(status));
        assertThat(readBack.priority()).isEqualTo(expectedPriority(priority));
    }

    /**
     * Property 1 for the full-EDIT path.
     *
     * <p>Starting from an already-created task, {@code update(id, request)} performs a full replace.
     * Reading the task back returns exactly the edited values (trimmed title, defaulted optionals),
     * confirming the round trip holds for edits as well as creates (Requirements 4.1, 4.2).
     */
    @Property(tries = 200)
    void updateThenReadPreservesWrittenValues(
            @ForAll("validTitles") String originalTitle,
            @ForAll("validTitles") String editedRawTitle,
            @ForAll("optionalDescriptions") String editedDescription,
            @ForAll("optionalStatuses") TaskStatus editedStatus,
            @ForAll("optionalPriorities") TaskPriority editedPriority) {

        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        // Seed a task to edit. Its original values are irrelevant: a full edit replaces them all.
        TaskResponse created = service.create(
                new CreateTaskRequest(originalTitle, "seed description", TaskStatus.DONE, TaskPriority.HIGH));

        UpdateTaskRequest edit =
                new UpdateTaskRequest(editedRawTitle, editedDescription, editedStatus, editedPriority);
        TaskResponse updated = service.update(created.id(), edit);

        TaskResponse readBack = service.getById(created.id());

        // The id is stable across an edit; the four editable fields equal the (defaulted) edit values.
        assertThat(readBack.id()).isEqualTo(created.id());
        assertThat(readBack.title()).isEqualTo(editedRawTitle.trim());
        assertThat(readBack.description()).isEqualTo(expectedDescription(editedDescription));
        assertThat(readBack.status()).isEqualTo(expectedStatus(editedStatus));
        assertThat(readBack.priority()).isEqualTo(expectedPriority(editedPriority));
        assertThat(updated).isEqualTo(readBack);
    }

    // ---------------------------------------------------------------------
    // Expected-value helpers (mirror the service's defaulting rules)
    // ---------------------------------------------------------------------

    private static String expectedDescription(String requested) {
        return requested != null ? requested : "";
    }

    private static TaskStatus expectedStatus(TaskStatus requested) {
        return requested != null ? requested : TaskStatus.TODO;
    }

    private static TaskPriority expectedPriority(TaskPriority requested) {
        return requested != null ? requested : TaskPriority.MEDIUM;
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Valid titles: values whose <em>trimmed</em> length is 1..150, since that trimmed length is what
     * the service persists and validates. We generate a 1..150 char non-whitespace-only core and pad
     * it with random surrounding whitespace, exercising the service's trimming behaviour directly.
     */
    @Provide
    Arbitrary<String> validTitles() {
        // A core of 1..150 characters, forced to contain at least one non-whitespace character so it
        // does not trim away to empty. We draw from printable non-whitespace plus interior spaces.
        Arbitrary<String> core = Arbitraries.strings()
                .withCharRange('!', '~')      // printable ASCII, no whitespace
                .ofMinLength(1)
                .ofMaxLength(150);

        Arbitrary<String> padding = Arbitraries.strings()
                .withChars(' ', '\t')
                .ofMaxLength(10);

        return Combinators.combine(padding, core, padding)
                .as((lead, body, trail) -> lead + body + trail)
                // Keep only titles whose trimmed length stays within the valid 1..150 bound.
                .filter(s -> {
                    int len = s.trim().length();
                    return len >= 1 && len <= 150;
                });
    }

    /**
     * Optional descriptions: either {@code null} (omitted -> defaults to {@code ""}) or a string of
     * 0..2000 characters, covering the valid description range including the empty string.
     */
    @Provide
    Arbitrary<String> optionalDescriptions() {
        Arbitrary<String> present = Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(2000);
        // Mix in null (omitted) so the default-to-"" branch is exercised too.
        return Arbitraries.oneOf(present, Arbitraries.just(null));
    }

    /** Optional status: any valid enum value or {@code null} (omitted -> defaults to TODO). */
    @Provide
    Arbitrary<TaskStatus> optionalStatuses() {
        return Arbitraries.oneOf(Arbitraries.of(TaskStatus.class), Arbitraries.just(null));
    }

    /** Optional priority: any valid enum value or {@code null} (omitted -> defaults to MEDIUM). */
    @Provide
    Arbitrary<TaskPriority> optionalPriorities() {
        return Arbitraries.oneOf(Arbitraries.of(TaskPriority.class), Arbitraries.just(null));
    }
}
