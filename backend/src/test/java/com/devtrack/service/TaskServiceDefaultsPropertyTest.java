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
 * Property-based test for Property 2 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 2: Omitted optional fields take their defaults
 *
 * <p>The property: for any create or full-edit request that omits status, description, or priority,
 * the stored task has status {@code TODO}, description {@code ""} (the empty string), and priority
 * {@code MEDIUM} respectively for each omitted field, while every field that <em>was</em> supplied
 * keeps its supplied value.
 *
 * <p><strong>Why property-based testing?</strong> "Omitted fields take defaults" must hold for every
 * combination of present/absent optional fields (2^3 = 8 combinations) crossed with every enum value
 * a present field might carry and every description a present field might hold. Rather than hand-pick
 * a few of those, jqwik generates many cases (at least 100 per property, see {@code tries}) and
 * checks the invariant on each. Each generated case independently varies <em>which</em> fields are
 * omitted (via {@code null}) and <em>what</em> the supplied ones contain, so the defaulting rule is
 * exercised field by field rather than all-or-nothing.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy, this
 * property tests <em>our</em> service logic (the defaulting rules), not the database engine.
 * {@link InMemoryTaskRepository} is a tiny in-memory stand-in for {@code TaskRepository} that assigns
 * ids the way MySQL's {@code AUTO_INCREMENT} would, so the test is fast and needs no external MySQL.
 * It is the same fake used by the Property 1 round-trip test, keeping the suite consistent.
 *
 * <p><strong>Validates: Requirements 1.2, 1.3, 1.4, 4.3</strong>
 */
class TaskServiceDefaultsPropertyTest {

    /**
     * Property 2 for the CREATE path (Requirements 1.2, 1.3, 1.4).
     *
     * <p>For a create request, any omitted optional field is stored as its default and any supplied
     * one is stored as given. We read the task back by its assigned id and assert each field against
     * its expected (defaulted-or-supplied) value.
     */
    @Property(tries = 200)
    void createDefaultsOmittedOptionalFields(
            @ForAll("validTitles") String rawTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus status,
            @ForAll("optionalPriorities") TaskPriority priority) {

        // Fresh service + fake repository per generated case, so tasks never leak between runs.
        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        CreateTaskRequest request = new CreateTaskRequest(rawTitle, description, status, priority);
        TaskResponse created = service.create(request);

        // Read the task back by the id the service assigned, then check the defaulting field by field.
        TaskResponse readBack = service.getById(created.id());

        assertThat(readBack.description()).isEqualTo(expectedDescription(description));
        assertThat(readBack.status()).isEqualTo(expectedStatus(status));
        assertThat(readBack.priority()).isEqualTo(expectedPriority(priority));
    }

    /**
     * Property 2 for the full-EDIT path (Requirement 4.3).
     *
     * <p>A full edit replaces all four fields and applies the same defaulting as create. Crucially,
     * we first seed the task with <em>non-default</em> values (IN_PROGRESS / a real description /
     * HIGH), so if the edit failed to default an omitted field the stale non-default value would be
     * observed and the assertion would fail. This proves the omitted field is reset to its default
     * rather than silently retaining the prior value.
     */
    @Property(tries = 200)
    void updateDefaultsOmittedOptionalFields(
            @ForAll("validTitles") String seedTitle,
            @ForAll("validTitles") String editedTitle,
            @ForAll("optionalDescriptions") String editedDescription,
            @ForAll("optionalStatuses") TaskStatus editedStatus,
            @ForAll("optionalPriorities") TaskPriority editedPriority) {

        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        // Seed with deliberately non-default optional values so an un-defaulted omission would show up
        // as a leftover non-default value (and fail the assertions below).
        TaskResponse created = service.create(new CreateTaskRequest(
                seedTitle, "seed description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

        UpdateTaskRequest edit =
                new UpdateTaskRequest(editedTitle, editedDescription, editedStatus, editedPriority);
        service.update(created.id(), edit);

        TaskResponse readBack = service.getById(created.id());

        assertThat(readBack.description()).isEqualTo(expectedDescription(editedDescription));
        assertThat(readBack.status()).isEqualTo(expectedStatus(editedStatus));
        assertThat(readBack.priority()).isEqualTo(expectedPriority(editedPriority));
    }

    // ---------------------------------------------------------------------
    // Expected-value helpers (mirror the service's defaulting rules)
    // ---------------------------------------------------------------------

    /** Omitted description (null) defaults to the empty string; otherwise the supplied value stands. */
    private static String expectedDescription(String requested) {
        return requested != null ? requested : "";
    }

    /** Omitted status (null) defaults to TODO; otherwise the supplied value stands. */
    private static TaskStatus expectedStatus(TaskStatus requested) {
        return requested != null ? requested : TaskStatus.TODO;
    }

    /** Omitted priority (null) defaults to MEDIUM; otherwise the supplied value stands. */
    private static TaskPriority expectedPriority(TaskPriority requested) {
        return requested != null ? requested : TaskPriority.MEDIUM;
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Valid titles: values whose <em>trimmed</em> length is 1..150, since that is what the service
     * persists and validates. The title itself is not the subject of this property, but it must be
     * valid so that {@code create}/{@code update} succeed and we can observe the defaulted optionals.
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

    /**
     * Optional descriptions: {@code null} (omitted -> defaults to {@code ""}) mixed with present
     * strings of 0..2000 characters. Weighting toward null makes the "omitted" branch common, since
     * that is precisely the case this property is about.
     */
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
