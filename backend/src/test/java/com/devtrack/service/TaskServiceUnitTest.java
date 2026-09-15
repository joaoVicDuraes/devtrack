package com.devtrack.service;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.exception.TaskNotFoundException;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Example-based (JUnit 5) unit tests for {@link TaskServiceImpl}.
 *
 * <p>Feature: devtrack-task-management, task 5.15 — the design's "Unit tests (example-based)"
 * section. Where the jqwik property tests assert an invariant holds across an unbounded input space,
 * these tests pin down a handful of <em>concrete, representative</em> cases so the exact behaviour is
 * documented and easy to read: defaulting, title trimming, entity&rarr;{@link TaskResponse} mapping,
 * and {@link TaskNotFoundException} on missing ids.
 *
 * <p><strong>Why the same {@link InMemoryTaskRepository} fake rather than a Mockito mock?</strong>
 * The task instruction says to isolate the service from a real repository, and this project already
 * ships a hand-written in-memory stand-in for exactly that purpose (used by every service property
 * test). Reusing it keeps the suite consistent and tests genuine round-trip behaviour: a write
 * followed by a read returns the stored object, which a bare mock cannot do without extra stubbing.
 * The service under test only ever talks to the {@code TaskRepository} interface, so the fake is a
 * faithful substitute — the Controller &rarr; Service &rarr; Repository boundary is respected.
 *
 * <p><strong>Validates: Requirements 1.2, 1.3, 1.4, 1.6, 3.2</strong>
 */
class TaskServiceUnitTest {

    /**
     * Builds a fresh service backed by a fresh in-memory repository.
     *
     * <p>A new instance per test keeps cases independent: no stored task from one test can leak into
     * another. This mirrors how the property tests construct their fixtures.
     */
    private TaskService newService() {
        return new TaskServiceImpl(new InMemoryTaskRepository());
    }

    // ---------------------------------------------------------------------
    // Defaulting: omitted optional fields fall back to their defaults
    // (Requirements 1.2 status->TODO, 1.3 priority->MEDIUM, 1.4 description->"")
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("defaulting of omitted optional fields")
    class Defaulting {

        @Test
        @DisplayName("omitted status defaults to TODO (Req 1.2)")
        void omittedStatusDefaultsToTodo() {
            TaskService service = newService();

            // status is null (omitted); the other optionals are supplied so status is the only default.
            TaskResponse created = service.create(
                    new CreateTaskRequest("Write report", "some notes", null, TaskPriority.HIGH));

            assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        }

        @Test
        @DisplayName("omitted priority defaults to MEDIUM (Req 1.3)")
        void omittedPriorityDefaultsToMedium() {
            TaskService service = newService();

            // priority is null (omitted).
            TaskResponse created = service.create(
                    new CreateTaskRequest("Write report", "some notes", TaskStatus.IN_PROGRESS, null));

            assertThat(created.priority()).isEqualTo(TaskPriority.MEDIUM);
        }

        @Test
        @DisplayName("omitted description defaults to empty string (Req 1.4)")
        void omittedDescriptionDefaultsToEmptyString() {
            TaskService service = newService();

            // description is null (omitted); it must be stored as "" rather than null.
            TaskResponse created = service.create(
                    new CreateTaskRequest("Write report", null, TaskStatus.DONE, TaskPriority.LOW));

            assertThat(created.description()).isEqualTo("");
        }

        @Test
        @DisplayName("all optional fields omitted resolve to TODO / MEDIUM / \"\" together (Reqs 1.2, 1.3, 1.4)")
        void allOptionalFieldsOmittedTakeDefaults() {
            TaskService service = newService();

            // Only the required title is supplied; every optional field is omitted at once.
            TaskResponse created = service.create(
                    new CreateTaskRequest("Just a title", null, null, null));

            assertThat(created.status()).isEqualTo(TaskStatus.TODO);
            assertThat(created.priority()).isEqualTo(TaskPriority.MEDIUM);
            assertThat(created.description()).isEqualTo("");
        }

        @Test
        @DisplayName("supplied optional values are kept, not overridden by defaults")
        void suppliedOptionalValuesAreKept() {
            TaskService service = newService();

            // When the caller does supply the optionals, defaulting must not clobber them.
            TaskResponse created = service.create(new CreateTaskRequest(
                    "Ship it", "release notes", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

            assertThat(created.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(created.priority()).isEqualTo(TaskPriority.HIGH);
            assertThat(created.description()).isEqualTo("release notes");
        }

        @Test
        @DisplayName("full edit applies the same defaulting to omitted fields (Req 4.3 via 1.2/1.3/1.4)")
        void fullEditAppliesSameDefaulting() {
            TaskService service = newService();
            // Seed a task with fully populated, non-default values.
            TaskResponse seeded = service.create(new CreateTaskRequest(
                    "Original", "original description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

            // A full edit that omits the optionals must reset them to defaults (full replace).
            TaskResponse edited = service.update(seeded.id(),
                    new UpdateTaskRequest("Edited title", null, null, null));

            assertThat(edited.status()).isEqualTo(TaskStatus.TODO);
            assertThat(edited.priority()).isEqualTo(TaskPriority.MEDIUM);
            assertThat(edited.description()).isEqualTo("");
            assertThat(edited.title()).isEqualTo("Edited title");
        }
    }

    // ---------------------------------------------------------------------
    // Title trimming: leading/trailing whitespace is removed before storing,
    // and the 1-150 bound is judged on the trimmed length (Req 1.6)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("title trimming")
    class TitleTrimming {

        @Test
        @DisplayName("leading/trailing whitespace is trimmed before storing")
        void titleIsTrimmedBeforeStoring() {
            TaskService service = newService();

            TaskResponse created = service.create(
                    new CreateTaskRequest("  spaced title  ", null, null, null));

            // The stored title is the trimmed form, and it round-trips through a read.
            assertThat(created.title()).isEqualTo("spaced title");
            assertThat(service.getById(created.id()).title()).isEqualTo("spaced title");
        }

        @Test
        @DisplayName("a title of exactly 150 characters after trimming is accepted (boundary, Req 1.6)")
        void titleOf150AfterTrimmingIsAccepted() {
            TaskService service = newService();
            String title150 = "a".repeat(150);

            // Padded with whitespace so acceptance depends on the *trimmed* length being 150.
            TaskResponse created = service.create(
                    new CreateTaskRequest("   " + title150 + "   ", null, null, null));

            assertThat(created.title()).isEqualTo(title150);
            assertThat(created.title()).hasSize(150);
        }

        @Test
        @DisplayName("a title whose trimmed length exceeds 150 is rejected (boundary, Req 1.6)")
        void titleLongerThan150AfterTrimmingIsRejected() {
            TaskService service = newService();
            String title151 = "a".repeat(151);

            // 151 non-whitespace characters: trimming cannot bring it within bound, so it is rejected.
            assertThatThrownBy(() -> service.create(
                    new CreateTaskRequest(title151, null, null, null)))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("a title that is only whitespace trims to empty and is rejected (Req 1.6 lower bound)")
        void whitespaceOnlyTitleIsRejected() {
            TaskService service = newService();

            assertThatThrownBy(() -> service.create(
                    new CreateTaskRequest("     ", null, null, null)))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("full edit also trims the title before storing (Req 4.6 via 1.6)")
        void fullEditTrimsTitle() {
            TaskService service = newService();
            TaskResponse seeded = service.create(
                    new CreateTaskRequest("Original", null, null, null));

            TaskResponse edited = service.update(seeded.id(),
                    new UpdateTaskRequest("\t padded edit \t", "d", TaskStatus.DONE, TaskPriority.LOW));

            assertThat(edited.title()).isEqualTo("padded edit");
        }
    }

    // ---------------------------------------------------------------------
    // TaskNotFoundException for missing ids on every id-targeted operation
    // (Requirement 3.2 for get; same exception backs 4.4, 5.3, 6.3, 7.2)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("TaskNotFoundException on a missing id")
    class MissingId {

        // An id the empty repository will never contain.
        private static final long ABSENT_ID = 999L;

        @Test
        @DisplayName("getById throws for a missing id (Req 3.2)")
        void getByIdThrowsWhenMissing() {
            TaskService service = newService();

            assertThatThrownBy(() -> service.getById(ABSENT_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("update throws for a missing id")
        void updateThrowsWhenMissing() {
            TaskService service = newService();

            assertThatThrownBy(() -> service.update(ABSENT_ID,
                    new UpdateTaskRequest("valid title", null, null, null)))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("changeStatus throws for a missing id")
        void changeStatusThrowsWhenMissing() {
            TaskService service = newService();

            assertThatThrownBy(() -> service.changeStatus(ABSENT_ID,
                    new ChangeStatusRequest(TaskStatus.DONE)))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("changePriority throws for a missing id")
        void changePriorityThrowsWhenMissing() {
            TaskService service = newService();

            assertThatThrownBy(() -> service.changePriority(ABSENT_ID,
                    new ChangePriorityRequest(TaskPriority.HIGH)))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("delete throws for a missing id")
        void deleteThrowsWhenMissing() {
            TaskService service = newService();

            assertThatThrownBy(() -> service.delete(ABSENT_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------
    // entity -> TaskResponse mapping produces all five fields correctly
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("entity to TaskResponse mapping")
    class Mapping {

        @Test
        @DisplayName("all five fields are mapped from a created/read task")
        void mapsAllFiveFields() {
            TaskService service = newService();

            TaskResponse created = service.create(new CreateTaskRequest(
                    "Design review", "check the API contract", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

            // id is assigned on create; the four editable fields carry the supplied values.
            assertThat(created.id()).isNotNull();
            assertThat(created.title()).isEqualTo("Design review");
            assertThat(created.description()).isEqualTo("check the API contract");
            assertThat(created.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(created.priority()).isEqualTo(TaskPriority.HIGH);

            // Reading it back returns an identical DTO, confirming the mapping is stable across a read.
            assertThat(service.getById(created.id())).isEqualTo(created);
        }
    }
}
