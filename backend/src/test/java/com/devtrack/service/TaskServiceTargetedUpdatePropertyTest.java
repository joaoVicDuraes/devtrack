package com.devtrack.service;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 7 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 7: Targeted updates change only their target field
 *
 * <p>The property (from design.md): for any existing task, a change-status request updates only the
 * status (leaving title, description, priority unchanged) and a change-priority request updates only
 * the priority (leaving title, description, status unchanged); the change persists to subsequent
 * reads.
 *
 * <p><strong>Why this deserves its own property.</strong> The two targeted endpoints exist precisely
 * so a client can change one field without resending — and thereby risking clobbering — the others
 * (see design.md, "Why status and priority have dedicated endpoints"). This property pins down the
 * two halves of that contract: exactly one field moves to the requested value, and the other three
 * are byte-for-byte identical to what they were. Checking it across the whole input space (any
 * starting field values, any requested enum value, including a "change" to the same value the task
 * already had) is what makes it a property rather than a couple of hand-picked examples.
 *
 * <p><strong>Why compare against the created task rather than recompute expected values?</strong> The
 * service trims the title and defaults omitted optionals on create. Instead of duplicating those
 * rules here, the test creates the task first, captures the resulting {@link TaskResponse} as the
 * source of truth for the "before" values, and then asserts the untouched fields still equal those
 * captured values after the targeted update. That keeps the test focused on Property 7 (only the
 * target field changed) and independent of the create-time defaulting rules covered by Properties 1
 * and 2.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy this
 * property tests <em>our</em> service logic (that a targeted update touches only its one field and
 * persists), not the database engine. {@link InMemoryTaskRepository} is the same in-memory stand-in
 * the other service property tests use; reading the task back through {@code getById} after the
 * update exercises the persistence round trip without any external MySQL.
 *
 * <p><strong>Validates: Requirements 5.1, 5.2, 6.1, 6.2</strong>
 */
class TaskServiceTargetedUpdatePropertyTest {

    /**
     * Property 7 for the change-STATUS path (Requirements 5.1, 5.2).
     *
     * <p>Create a task, then apply {@code changeStatus} with an arbitrary valid status. The returned
     * task must carry the requested status while its title, description, and priority match the
     * task's pre-change values exactly; reading the task back by id must show the same, confirming
     * the change persisted (Requirement 5.1) and the other fields were left untouched (Requirement
     * 5.2).
     */
    @Property(tries = 200)
    void changeStatusUpdatesOnlyStatusAndPersists(
            @ForAll("validTitles") String rawTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus initialStatus,
            @ForAll("optionalPriorities") TaskPriority initialPriority,
            @ForAll TaskStatus requestedStatus) {

        // Fresh service + fake repository per generated case, so tasks never leak between runs.
        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        // Seed an existing task; its stored (trimmed/defaulted) values are the "before" baseline.
        TaskResponse existing =
                service.create(new CreateTaskRequest(rawTitle, description, initialStatus, initialPriority));

        TaskResponse updated =
                service.changeStatus(existing.id(), new ChangeStatusRequest(requestedStatus));

        // Only the status moved to the requested value; the other three fields are unchanged.
        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.status()).isEqualTo(requestedStatus);
        assertThat(updated.title()).isEqualTo(existing.title());
        assertThat(updated.description()).isEqualTo(existing.description());
        assertThat(updated.priority()).isEqualTo(existing.priority());

        // The change persists to a subsequent read (Requirement 5.1).
        TaskResponse readBack = service.getById(existing.id());
        assertThat(readBack).isEqualTo(updated);
    }

    /**
     * Property 7 for the change-PRIORITY path (Requirements 6.1, 6.2).
     *
     * <p>Create a task, then apply {@code changePriority} with an arbitrary valid priority. The
     * returned task must carry the requested priority while its id, title, description, and status
     * match the pre-change values exactly; reading the task back by id must show the same, confirming
     * the updated priority persists on subsequent reads (Requirement 6.2) and the other fields were
     * left unchanged (Requirement 6.1).
     */
    @Property(tries = 200)
    void changePriorityUpdatesOnlyPriorityAndPersists(
            @ForAll("validTitles") String rawTitle,
            @ForAll("optionalDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus initialStatus,
            @ForAll("optionalPriorities") TaskPriority initialPriority,
            @ForAll TaskPriority requestedPriority) {

        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        TaskResponse existing =
                service.create(new CreateTaskRequest(rawTitle, description, initialStatus, initialPriority));

        TaskResponse updated =
                service.changePriority(existing.id(), new ChangePriorityRequest(requestedPriority));

        // Only the priority moved to the requested value; the other fields are unchanged.
        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.priority()).isEqualTo(requestedPriority);
        assertThat(updated.title()).isEqualTo(existing.title());
        assertThat(updated.description()).isEqualTo(existing.description());
        assertThat(updated.status()).isEqualTo(existing.status());

        // The updated priority persists to a subsequent read (Requirement 6.2).
        TaskResponse readBack = service.getById(existing.id());
        assertThat(readBack).isEqualTo(updated);
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    //
    // These mirror the generators used by the other service property tests so the "existing task"
    // covers the full valid input space: any trimmed-valid title, any (or omitted) description, and
    // any (or omitted) status/priority. The requested status/priority use jqwik's default enum
    // generation (@ForAll on the enum type), which draws every constant — including one equal to the
    // task's current value, exercising the "change to the same value" edge case.
    // ---------------------------------------------------------------------

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
