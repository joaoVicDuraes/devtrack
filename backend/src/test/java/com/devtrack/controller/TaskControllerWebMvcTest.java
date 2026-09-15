package com.devtrack.controller;

import com.devtrack.dto.ChangePriorityRequest;
import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.dto.TaskResponse;
import com.devtrack.dto.UpdateTaskRequest;
import com.devtrack.model.TaskPriority;
import com.devtrack.model.TaskStatus;
import com.devtrack.service.TaskService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer (HTTP contract) tests for {@link TaskController}.
 *
 * <p>Feature: devtrack-task-management, task 7.2 — the design's "Web-layer tests
 * (controller / {@code @WebMvcTest})" section. These tests exercise the controller through a real
 * (but mock) servlet request/response cycle to verify the <em>HTTP contract and wiring</em>: the
 * URL-to-method routing, the {@code @Valid} short-circuit, request-body parsing, path-variable
 * conversion, and the status code each endpoint returns. They deliberately do <strong>not</strong>
 * touch a database.
 *
 * <p><strong>Why {@code @WebMvcTest(TaskController.class)}?</strong> This slice annotation boots only
 * the Spring MVC infrastructure for the named controller (routing, message converters/Jackson, the
 * validation adapter) instead of the whole application context. No JPA, no repository, no MySQL are
 * loaded, so the tests are fast and focus purely on the web adapter. It auto-configures a
 * {@link MockMvc} we can use to fire requests without a running server.
 *
 * <p><strong>Why a mocked {@link TaskService}?</strong> The controller depends on the service, and in
 * a web slice that collaborator is not present, so we supply a Mockito mock via {@code @MockBean}.
 * A mock lets us (a) stub the happy-path return values to assert the mapped status code, and
 * (b) prove <em>negative</em> facts about wiring — most importantly that when validation fails the
 * service is <em>never</em> reached (Requirement 8.5). A mock records interactions, so
 * {@code verifyNoInteractions(...)} / {@code verify(..., never())} can assert exactly that.
 *
 * <p><strong>Scope note (the {@code GlobalExceptionHandler} does not exist yet, task 8).</strong>
 * Without a custom {@code @RestControllerAdvice}, Spring MVC's built-in exception handling still
 * produces the correct <em>status codes</em> for the error cases here: a failed {@code @Valid} check
 * raises {@code MethodArgumentNotValidException} &rarr; 400, an unparseable/invalid-enum body raises
 * {@code HttpMessageNotReadableException} &rarr; 400, and a non-numeric path id raises
 * {@code MethodArgumentTypeMismatchException} &rarr; 400. So these tests assert on <strong>status
 * codes and service interaction only</strong>. The error <em>body</em> shape (the uniform
 * {@code ErrorResponse}) is task 8's responsibility and is verified by task 8's tests, so nothing
 * here asserts on the response body.
 *
 * <p><strong>Validates: Requirements 1.11, 3.3, 5.4, 6.4, 7.3, 8.5, 10.1, 10.2</strong>
 */
@WebMvcTest(TaskController.class)
class TaskControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Jackson's {@code ObjectMapper} is auto-configured by the web slice; we reuse the same one the
     * controller uses to serialize request bodies to JSON, so our test payloads match the wire format
     * the controller actually parses.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The controller's collaborator, replaced by a Mockito mock in the slice context. Stubbed for
     * happy paths and inspected (verify/never) for the "service is not called" assertions.
     */
    @MockBean
    private TaskService taskService;

    /** A fully-populated response the mock returns for happy-path stubs. */
    private static TaskResponse sampleResponse() {
        return new TaskResponse(1L, "Write report", "some notes", TaskStatus.TODO, TaskPriority.MEDIUM);
    }

    // =====================================================================
    // 1. Correct status codes per endpoint (happy paths)
    //    Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1
    // =====================================================================

    @Nested
    @DisplayName("happy-path status codes per endpoint")
    class HappyPathStatusCodes {

        @Test
        @DisplayName("POST /tasks with a valid body returns 201 and invokes service.create")
        void postCreatesReturns201AndInvokesService() throws Exception {
            when(taskService.create(any(CreateTaskRequest.class))).thenReturn(sampleResponse());

            CreateTaskRequest body = new CreateTaskRequest("Write report", "some notes", null, null);

            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            // Wiring check: a valid body must actually reach the service exactly once.
            verify(taskService, times(1)).create(any(CreateTaskRequest.class));
        }

        @Test
        @DisplayName("GET /tasks returns 200")
        void listReturns200() throws Exception {
            when(taskService.listAll()).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/tasks"))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).listAll();
        }

        @Test
        @DisplayName("GET /tasks/{id} returns 200")
        void getByIdReturns200() throws Exception {
            when(taskService.getById(1L)).thenReturn(sampleResponse());

            mockMvc.perform(get("/tasks/1"))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).getById(1L);
        }

        @Test
        @DisplayName("PUT /tasks/{id} with a valid body returns 200")
        void updateReturns200() throws Exception {
            when(taskService.update(eq(1L), any(UpdateTaskRequest.class))).thenReturn(sampleResponse());

            UpdateTaskRequest body =
                    new UpdateTaskRequest("Edited title", "desc", TaskStatus.DONE, TaskPriority.HIGH);

            mockMvc.perform(put("/tasks/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).update(eq(1L), any(UpdateTaskRequest.class));
        }

        @Test
        @DisplayName("PATCH /tasks/{id}/status with a valid body returns 200")
        void changeStatusReturns200() throws Exception {
            when(taskService.changeStatus(eq(1L), any(ChangeStatusRequest.class)))
                    .thenReturn(sampleResponse());

            ChangeStatusRequest body = new ChangeStatusRequest(TaskStatus.IN_PROGRESS);

            mockMvc.perform(patch("/tasks/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).changeStatus(eq(1L), any(ChangeStatusRequest.class));
        }

        @Test
        @DisplayName("PATCH /tasks/{id}/priority with a valid body returns 200")
        void changePriorityReturns200() throws Exception {
            when(taskService.changePriority(eq(1L), any(ChangePriorityRequest.class)))
                    .thenReturn(sampleResponse());

            ChangePriorityRequest body = new ChangePriorityRequest(TaskPriority.HIGH);

            mockMvc.perform(patch("/tasks/1/priority")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).changePriority(eq(1L), any(ChangePriorityRequest.class));
        }

        @Test
        @DisplayName("DELETE /tasks/{id} returns 204")
        void deleteReturns204() throws Exception {
            // delete is void; stub it to do nothing so the happy path returns 204 No Content.
            doNothing().when(taskService).delete(1L);

            mockMvc.perform(delete("/tasks/1"))
                    .andExpect(status().isNoContent());

            verify(taskService, times(1)).delete(1L);
        }
    }

    // =====================================================================
    // 2. Validation failure returns 400 AND the service is never called
    //    Requirement 8.5 (validation runs before, and short-circuits, the service)
    // =====================================================================

    @Nested
    @DisplayName("validation failure short-circuits before the service (Req 8.5)")
    class ValidationShortCircuits {

        @Test
        @DisplayName("POST /tasks with a blank title returns 400 and never calls the service")
        void blankTitleIsRejectedAndServiceNotCalled() throws Exception {
            // Blank title violates @NotBlank on CreateTaskRequest; @Valid must reject before delegation.
            CreateTaskRequest body = new CreateTaskRequest("   ", "some notes", null, null);

            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            // The core of Req 8.5: an invalid request must NOT reach the business layer at all.
            // verifyNoInteractions proves the mock recorded zero calls of any kind.
            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("POST /tasks with a missing title returns 400 and never calls the service")
        void missingTitleIsRejectedAndServiceNotCalled() throws Exception {
            // Well-formed JSON but the required title field is absent -> @NotBlank fails.
            String jsonMissingTitle = "{\"description\":\"note\"}";

            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMissingTitle))
                    .andExpect(status().isBadRequest());

            verify(taskService, never()).create(any());
            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("PATCH /tasks/{id}/status with null status returns 400 and never calls the service")
        void nullStatusIsRejectedAndServiceNotCalled() throws Exception {
            // status is @NotNull; an explicit null must be rejected before changeStatus runs.
            String jsonNullStatus = "{\"status\":null}";

            mockMvc.perform(patch("/tasks/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonNullStatus))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }
    }

    // =====================================================================
    // 3. Malformed / missing request body -> 400
    //    Requirement 1.11 (unparseable body is rejected)
    // =====================================================================

    @Nested
    @DisplayName("malformed or missing request body returns 400 (Req 1.11)")
    class MalformedBody {

        @Test
        @DisplayName("POST /tasks with malformed JSON returns 400")
        void malformedJsonReturns400() throws Exception {
            // "{" is not valid JSON; Jackson fails while reading -> HttpMessageNotReadableException -> 400.
            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("POST /tasks with an empty body returns 400")
        void emptyBodyReturns400() throws Exception {
            // No body at all: there is nothing to bind to the required @RequestBody -> 400.
            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }
    }

    // =====================================================================
    // 4. Malformed path id -> 400
    //    Requirements 3.3, 7.3 (a non-numeric id cannot be interpreted)
    // =====================================================================

    @Nested
    @DisplayName("malformed path id returns 400 (Reqs 3.3, 7.3)")
    class MalformedPathId {

        @Test
        @DisplayName("GET /tasks/abc returns 400")
        void nonNumericIdOnGetReturns400() throws Exception {
            // "abc" cannot convert to the Long path variable -> MethodArgumentTypeMismatchException -> 400.
            mockMvc.perform(get("/tasks/abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("DELETE /tasks/abc returns 400 (Req 7.3)")
        void nonNumericIdOnDeleteReturns400() throws Exception {
            // Same conversion failure on the delete route; the service must not be reached.
            mockMvc.perform(delete("/tasks/abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }
    }

    // =====================================================================
    // 5. Invalid enum value in body -> 400 (spot-check of Property 5)
    //    Requirements 5.4, 6.4 (status/priority must be a valid enum value)
    // =====================================================================

    @Nested
    @DisplayName("invalid enum value in body returns 400 (spot-check Property 5)")
    class InvalidEnumValue {

        @Test
        @DisplayName("PATCH /tasks/{id}/status with an unknown status returns 400")
        void invalidStatusEnumReturns400() throws Exception {
            // "NOPE" is not a TaskStatus constant; Jackson fails to deserialize the enum while reading
            // the body -> HttpMessageNotReadableException -> 400, before the service is consulted.
            mockMvc.perform(patch("/tasks/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"NOPE\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("PATCH /tasks/{id}/priority with an unknown priority returns 400")
        void invalidPriorityEnumReturns400() throws Exception {
            mockMvc.perform(patch("/tasks/1/priority")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"priority\":\"URGENT\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("POST /tasks with an invalid status enum returns 400")
        void invalidStatusEnumOnCreateReturns400() throws Exception {
            // Enum deserialization failure on create is likewise a parse error -> 400.
            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"ok\",\"status\":\"NOPE\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(taskService);
        }
    }

    // =====================================================================
    // 6. An Authorization header behaves identically to no header
    //    Requirements 10.1, 10.2 (no auth is required; a credential is ignored, not validated)
    // =====================================================================

    @Nested
    @DisplayName("Authorization header is ignored, not required (Reqs 10.1, 10.2)")
    class AuthorizationHeaderIgnored {

        @Test
        @DisplayName("GET /tasks succeeds with an Authorization header, exactly as without one")
        void authorizationHeaderDoesNotChangeSuccess() throws Exception {
            when(taskService.listAll()).thenReturn(List.of(sampleResponse()));

            // Sending a bearer token must not cause rejection and must not be validated:
            // the same 200 we get with no header (asserted in HappyPathStatusCodes.listReturns200).
            mockMvc.perform(get("/tasks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer some-token"))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).listAll();
        }

        @Test
        @DisplayName("POST /tasks with an Authorization header still returns 201")
        void authorizationHeaderOnCreateStillSucceeds() throws Exception {
            when(taskService.create(any(CreateTaskRequest.class))).thenReturn(sampleResponse());

            CreateTaskRequest body = new CreateTaskRequest("Write report", "some notes", null, null);

            mockMvc.perform(post("/tasks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer some-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            verify(taskService, times(1)).create(any(CreateTaskRequest.class));
        }
    }
}
