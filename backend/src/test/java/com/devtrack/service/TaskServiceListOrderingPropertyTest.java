package com.devtrack.service;

import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: devtrack-task-management, Property 10: Listing returns all tasks ordered by ascending id

/**
 * Property-based test for Property 10 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 10: Listing returns all tasks ordered by ascending
 * id
 *
 * <p>The property: for any set of created tasks, {@code listAll()} returns a collection containing
 * every stored task exactly once, in strictly ascending id order. It also covers Requirement 2.2:
 * listing while no task exists returns an empty collection.
 *
 * <p><strong>Why property-based testing?</strong> "Listing returns everything, once each, in id
 * order" must hold no matter how many tasks were created or in what shape. Hand-picked examples
 * (two tasks, three tasks) can miss ordering bugs that only show up at particular sizes. jqwik
 * instead generates task sets of many sizes (0..30 here), so both the empty case and larger lists
 * are exercised on every run (at least 100 per property, see {@code tries}).
 *
 * <p><strong>Why a fake repository instead of MySQL?</strong> Per the design's testing strategy, this
 * property tests <em>our</em> service logic (that {@code listAll} returns the repository's
 * ascending-id result mapped to DTOs), not the database engine. {@link InMemoryTaskRepository}
 * assigns ids the way MySQL's {@code AUTO_INCREMENT} would (monotonically increasing from 1) and
 * implements {@code findAllByOrderByIdAsc()} by sorting on id, so it faithfully stands in for the
 * real repository while keeping the test fast and dependency-free.
 *
 * <p><strong>Validates: Requirements 2.1, 2.2</strong>
 */
class TaskServiceListOrderingPropertyTest {

    /**
     * Property 10 (Requirement 2.1): create an arbitrary set of tasks, then assert {@code listAll()}
     * returns exactly those tasks — each exactly once — in strictly ascending id order.
     *
     * <p>We capture every {@link TaskResponse} returned by {@code create} as the source of truth for
     * what "should be present". The listing must equal that set (same ids, same field values) and,
     * independently, its ids must be strictly increasing so ordering is verified even if two tasks
     * happened to share other field values.
     */
    @Property(tries = 200)
    void listAllReturnsEveryTaskExactlyOnceInAscendingIdOrder(
            @ForAll("taskRequestLists") List<CreateTaskRequest> requests) {

        // Fresh service + fake repository per generated case, so tasks never leak between runs.
        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        // Create each requested task, remembering the response the service returned for each.
        List<TaskResponse> created = new ArrayList<>();
        for (CreateTaskRequest request : requests) {
            created.add(service.create(request));
        }

        List<TaskResponse> listed = service.listAll();

        // Every created task appears exactly once — same count, same elements regardless of order.
        assertThat(listed)
                .hasSameSizeAs(created)
                .containsExactlyInAnyOrderElementsOf(created);

        // The listing is in strictly ascending id order: each id is greater than the one before it.
        List<Long> ids = listed.stream().map(TaskResponse::id).toList();
        assertThat(ids).isSorted();
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i)).isGreaterThan(ids.get(i - 1));
        }
    }

    /**
     * Property 10 boundary / Requirement 2.2: with no task ever created, {@code listAll()} returns an
     * empty collection (never {@code null}). This case has no varying input, so it is an
     * {@code @Example} rather than a {@code @Property}; the size-0..30 generator above also exercises
     * the empty store, but stating it explicitly documents Requirement 2.2 directly.
     */
    @Example
    void listAllOnEmptyStoreReturnsEmptyCollection() {
        TaskService service = new TaskServiceImpl(new InMemoryTaskRepository());

        assertThat(service.listAll()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Generators (jqwik @Provide methods)
    // ---------------------------------------------------------------------

    /**
     * Sets of create requests of varying size (0..30), so the property is checked from the empty
     * store up to lists of thirty tasks. Naming a {@code List} provider directly (rather than a
     * single-element provider plus {@code @Size}) is what jqwik expects when the parameter type is a
     * {@code List}: it resolves the named provider against the parameter type as a whole.
     */
    @Provide
    Arbitrary<List<CreateTaskRequest>> taskRequestLists() {
        return taskRequests().list().ofMinSize(0).ofMaxSize(30);
    }

    /**
     * A single valid create request: a valid title (trimmed length 1..150) plus optional description,
     * status, and priority (each may be omitted as {@code null}). Only the shape matters for this
     * property — we care that whatever is stored comes back once, in id order — so field values vary
     * freely.
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
