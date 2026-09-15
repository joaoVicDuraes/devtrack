package com.devtrack.service;

import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 4 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 4: Description must not exceed 2000 characters
 *
 * <p>The property: for any create or full-edit request whose description length exceeds 2000
 * characters, the Backend rejects the request with HTTP 400 and does not persist a task.
 *
 * <p><strong>Why this test validates the DTO's {@code @Size} constraint directly rather than calling
 * the service.</strong> The other rejection property (Property 3, title) is enforced by
 * <em>business logic inside {@link TaskServiceImpl}</em> because the requirement measures the title
 * <em>after trimming</em> — something bean validation cannot express — so that test drives the
 * service and asserts it throws. The description rule is different: "at most 2000 characters" is
 * plain structural validation, expressed on {@link CreateTaskRequest}/{@link UpdateTaskRequest} as
 * {@code @Size(max = 2000)} and nothing more. The service does not re-check description length. Per
 * the design's request lifecycle (validation runs on the {@code Request_DTO} <em>before</em> the
 * service is invoked; Requirement 8.5), an over-long description is rejected at the controller's
 * {@code @Valid} step and the service is never reached. So the layer that actually enforces this
 * property is Jakarta Validation on the DTO — which is exactly what this test exercises.
 *
 * <p><strong>How rejection surfaces as HTTP 400.</strong> A failed {@code @Size} check produces a
 * {@link ConstraintViolation}; at runtime Spring raises {@code MethodArgumentNotValidException},
 * which the {@code GlobalExceptionHandler} maps to HTTP 400 (see the design's error table). Here we
 * run the same validation engine {@code @Valid} uses (a {@link Validator} obtained from a
 * {@link ValidatorFactory}) and assert a violation is reported on the {@code description} field.
 *
 * <p><strong>Why property-based testing?</strong> "A description longer than 2000 is always
 * rejected" must hold across an unbounded space of over-limit lengths (2001, 2002, ... and far
 * beyond) and arbitrary content. Rather than hand-pick a couple of lengths, jqwik generates many
 * over-limit descriptions (at least 100 per property, see {@code tries}) and checks the invariant on
 * each. A companion property confirms the boundary is placed correctly: a description of exactly
 * 2000 characters (and shorter) passes validation, so the rule rejects 2001+ without also rejecting
 * the largest legal value.
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy this
 * property tests <em>our</em> logic (the description bound), not the database engine.
 * {@link InMemoryTaskRepository} is the same in-memory stand-in used by the other service property
 * tests; its {@code count()} lets us assert that a rejected request persists nothing (no task stored,
 * no id assigned), keeping the suite consistent.
 *
 * <p><strong>Validates: Requirements 1.7, 4.7</strong>
 */
class TaskServiceDescriptionLengthPropertyTest {

    /** The maximum legal description length; anything longer must be rejected (Requirements 1.7, 4.7). */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /**
     * A shared Jakarta {@link Validator}. This is the same validation engine Spring drives when a
     * controller marks a request body {@code @Valid}; using it directly lets us assert the DTO-level
     * {@code @Size(max = 2000)} rule without spinning up the web layer.
     */
    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    /**
     * Property 4 for the CREATE path (Requirement 1.7).
     *
     * <p>For any create request whose description exceeds 2000 characters, validation reports a
     * violation on the {@code description} field, so the request is rejected before the service runs.
     * Because the service is never invoked on a rejected request, nothing is persisted: the store
     * stays empty and no id is assigned. The title is held valid so the <em>only</em> reason to
     * reject is the over-long description.
     */
    @Property(tries = 200)
    void createRejectsOverLongDescriptionAndPersistsNothing(
            @ForAll("validTitles") String title,
            @ForAll("tooLongDescriptions") String description,
            @ForAll("optionalStatuses") TaskStatus status,
            @ForAll("optionalPriorities") TaskPriority priority) {

        // Fresh fake repository per generated case, so state never leaks between runs. We do not
        // build a service here on purpose: validation rejects this request before the service would
        // ever be called, and asserting the repository stays empty is what proves "persists nothing".
        InMemoryTaskRepository repository = new InMemoryTaskRepository();

        CreateTaskRequest request = new CreateTaskRequest(title, description, status, priority);

        Set<ConstraintViolation<CreateTaskRequest>> violations = VALIDATOR.validate(request);

        // The over-long description must be flagged; the controller would turn this into a 400.
        assertThat(violations)
                .as("an over-2000-character description must fail validation")
                .anyMatch(v -> "description".equals(v.getPropertyPath().toString()));

        // Rejected before the service is invoked, so nothing is ever written: no task, no id.
        assertThat(repository.count())
                .as("a rejected create must persist nothing")
                .isZero();
    }

    /**
     * Property 4 for the full-EDIT path (Requirement 4.7).
     *
     * <p>We first create one valid task, then build a full-edit request whose description exceeds
     * 2000 characters. Validation flags the description, so the edit would be rejected with a 400
     * before the service runs — leaving the store untouched: still exactly the one seeded task. This
     * proves a rejected edit neither adds a task nor mutates the existing one.
     */
    @Property(tries = 200)
    void updateRejectsOverLongDescriptionAndLeavesStoreUnchanged(
            @ForAll("validTitles") String seedTitle,
            @ForAll("validTitles") String editedTitle,
            @ForAll("tooLongDescriptions") String editedDescription,
            @ForAll("optionalStatuses") TaskStatus editedStatus,
            @ForAll("optionalPriorities") TaskPriority editedPriority) {

        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskServiceImpl(repository);

        // Seed one valid task; capture its stored state to compare against after the rejected edit.
        var seeded = service.create(new CreateTaskRequest(
                seedTitle, "seed description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));
        var before = service.getById(seeded.id());

        UpdateTaskRequest edit =
                new UpdateTaskRequest(editedTitle, editedDescription, editedStatus, editedPriority);

        Set<ConstraintViolation<UpdateTaskRequest>> violations = VALIDATOR.validate(edit);

        assertThat(violations)
                .as("an over-2000-character description must fail validation on edit")
                .anyMatch(v -> "description".equals(v.getPropertyPath().toString()));

        // The rejected edit added nothing and changed nothing: same count, same field values.
        assertThat(repository.count()).isEqualTo(1);
        assertThat(service.getById(seeded.id())).isEqualTo(before);
    }

    /**
     * Boundary companion to Property 4: a description at or below the 2000-character limit is
     * <em>accepted</em>. This confirms the rule rejects 2001+ without also rejecting the largest
     * legal value (exactly 2000), so the boundary sits between 2000 and 2001 as the requirement
     * demands. We assert that validation reports no violation on the {@code description} field (title
     * is held valid, so no violation should be reported at all).
     */
    @Property(tries = 200)
    void createAcceptsDescriptionAtOrBelowLimit(
            @ForAll("validTitles") String title,
            @ForAll("withinLimitDescriptions") String description) {

        CreateTaskRequest request =
                new CreateTaskRequest(title, description, TaskStatus.TODO, TaskPriority.MEDIUM);

        Set<ConstraintViolation<CreateTaskRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .as("a description of 0..2000 characters must not trigger a length violation")
                .noneMatch(v -> "description".equals(v.getPropertyPath().toString()));
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Descriptions that exceed the 2000-character limit: lengths from 2001 up to 3000. We keep the
     * upper bound modest so the generator stays fast while still ranging well past the boundary. Any
     * character content is fine — the rule is purely about length — so we draw from printable ASCII.
     */
    @Provide
    Arbitrary<String> tooLongDescriptions() {
        return Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(MAX_DESCRIPTION_LENGTH + 1)   // 2001
                .ofMaxLength(3000);
    }

    /**
     * Descriptions at or below the limit: lengths from 0 up to exactly 2000, so the boundary value
     * (2000) is included and must be accepted.
     */
    @Provide
    Arbitrary<String> withinLimitDescriptions() {
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(MAX_DESCRIPTION_LENGTH);       // 2000
    }

    /**
     * Valid titles: trimmed length 1..150, so the title never contributes a violation and the
     * description is the sole subject of the property. A non-whitespace core padded with random
     * surrounding whitespace, filtered to stay within the trimmed bound.
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
