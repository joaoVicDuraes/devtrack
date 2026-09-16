package com.devtrack.controller;

import com.devtrack.dto.ChangeStatusRequest;
import com.devtrack.dto.CreateTaskRequest;
import com.devtrack.exception.GlobalExceptionHandler;
import com.devtrack.service.TaskService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer (HTTP contract) tests for the <em>error body</em> that {@link GlobalExceptionHandler}
 * produces for {@link TaskController}.
 *
 * <p>Feature: devtrack-task-management, task 8.3 — the design's "@WebMvcTest tests for error
 * mapping" section. Where task 7.2's {@link TaskControllerWebMvcTest} asserts only the error
 * <em>status codes</em> (which Spring's built-in handling already produces), this suite asserts on
 * the uniform {@link com.devtrack.dto.ErrorResponse} <em>body</em> that our advice builds: the
 * per-field list for a multi-field validation failure, the generic 500 for a persistence failure,
 * and the 400 for an invalid enum. It also proves that no internal implementation detail (stack
 * traces, exception class names, SQL) leaks into any of those bodies.
 *
 * <p><strong>Why {@code @Import(GlobalExceptionHandler.class)} on top of the slice?</strong>
 * {@code @WebMvcTest(TaskController.class)} boots only the MVC infrastructure for the named
 * controller. A {@code @RestControllerAdvice} is normally picked up by the slice, but task 7.2's
 * tests never asserted on the body, so they could pass even if the advice were absent. Because this
 * suite asserts the exact {@code ErrorResponse} shape, we import the advice <em>explicitly</em> so
 * the test genuinely exercises our mapping rather than Spring's default error handling. This makes
 * the intent obvious and the test independent of slice auto-detection behaviour.
 *
 * <p><strong>Why a mocked {@link TaskService}?</strong> Same reason as the sibling web test: in a
 * web slice the collaborator is absent, so we supply a Mockito mock via {@code @MockBean}. Here the
 * mock also lets us <em>inject a failure</em> — stubbing the service to throw
 * {@link DataAccessException} — so we can verify the 500 mapping without a real (broken) database.
 *
 * <p><strong>Validates: Requirements 8.7, 8.9, 8.10, 5.4, 6.4</strong>
 */
@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerErrorMappingWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    // =====================================================================
    // 1. A body invalid on multiple fields -> 400 listing every failed field
    //    Requirements 8.7, 8.9 (complements Property 12)
    // =====================================================================

    @Nested
    @DisplayName("multi-field validation failure lists every failed field (Reqs 8.7, 8.9)")
    class MultiFieldValidation {

        @Test
        @DisplayName("POST /tasks with a blank title AND an over-length description returns 400 "
                + "with both fields in the errors array")
        void multipleInvalidFieldsAreAllReported() throws Exception {
            // Two distinct constraints fail on two distinct fields:
            //  - title is blank        -> violates @NotBlank on CreateTaskRequest.title
            //  - description > 2000    -> violates @Size(max = 2000) on CreateTaskRequest.description
            // The advice must report BOTH, not just the first one (Requirement 8.9 / Property 12).
            String overLongDescription = "x".repeat(2001);
            CreateTaskRequest body = new CreateTaskRequest("   ", overLongDescription, null, null);

            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    // The body echoes the status code (Requirement 8.7 — uniform envelope).
                    .andExpect(jsonPath("$.status").value(400))
                    // Every failed field is present; order is not guaranteed, so match unordered.
                    .andExpect(jsonPath("$.errors", hasSize(2)))
                    .andExpect(jsonPath("$.errors[*].field",
                            containsInAnyOrder("title", "description")));

            // Validation short-circuits before the business layer.
            verifyNoInteractions(taskService);
        }
    }

    // =====================================================================
    // 2. Persistence failure (service throws DataAccessException) -> 500 generic body
    //    Requirement 8.10 (no stack traces / internal class names)
    // =====================================================================

    @Nested
    @DisplayName("persistence failure maps to a generic 500 with no leaked internals (Req 8.10)")
    class PersistenceFailure {

        @Test
        @DisplayName("POST /tasks returns 500 with a generic message when the service throws "
                + "DataAccessException")
        void dataAccessExceptionMapsToGeneric500() throws Exception {
            // Simulate the database being unavailable. DataAccessResourceFailureException is a real
            // subclass of Spring's DataAccessException, so it exercises the base-type handler exactly
            // as a live persistence failure would. We give it a message that DOES contain sensitive
            // detail so we can prove that detail is NOT forwarded to the client.
            String leakyInternalMessage =
                    "jdbc:mysql://localhost:3306/devtrack connection refused "
                            + "(com.mysql.cj.jdbc.exceptions.CommunicationsException)";
            when(taskService.create(any(CreateTaskRequest.class)))
                    .thenThrow(new DataAccessResourceFailureException(leakyInternalMessage));

            CreateTaskRequest body = new CreateTaskRequest("Write report", "some notes", null, null);

            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    // The client sees the fixed, generic sentence, never the exception's own message.
                    .andExpect(jsonPath("$.message")
                            .value("The operation could not be completed. Please try again later."))
                    // No per-field detail for a server error.
                    .andExpect(jsonPath("$.errors", hasSize(0)))
                    // Explicitly assert none of the leaked internals appear anywhere in the body.
                    .andExpect(jsonPath("$.message", not(containsString("jdbc"))))
                    .andExpect(jsonPath("$.message", not(containsString("mysql"))))
                    .andExpect(jsonPath("$.message", not(containsString("Exception"))))
                    .andExpect(jsonPath("$.message", not(containsString("3306"))));
        }
    }

    // =====================================================================
    // 3. Invalid enum value in the JSON body -> 400
    //    Requirements 5.4, 6.4 (status/priority must be a valid enum value)
    // =====================================================================

    @Nested
    @DisplayName("invalid enum value in the body maps to 400 (Reqs 5.4, 6.4)")
    class InvalidEnum {

        @Test
        @DisplayName("PATCH /tasks/{id}/status with an unknown status returns 400 with a generic "
                + "parse body and no leaked internals")
        void invalidStatusEnumMapsToGeneric400() throws Exception {
            // "NOPE" is not a TaskStatus constant. Jackson fails to deserialize the enum while READING
            // the body, so Spring raises HttpMessageNotReadableException (before @Valid runs). Our
            // advice maps that to a fixed, generic 400 parse message.
            mockMvc.perform(patch("/tasks/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"NOPE\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("Request body is missing or could not be parsed"))
                    .andExpect(jsonPath("$.errors", hasSize(0)))
                    // The offending value / parser internals must not be echoed back (Req 8.8).
                    .andExpect(jsonPath("$.message", not(containsString("NOPE"))))
                    .andExpect(jsonPath("$.message", not(containsString("Exception"))));

            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("POST /tasks with an invalid priority enum returns 400 with the generic parse body")
        void invalidPriorityEnumOnCreateMapsToGeneric400() throws Exception {
            // Same parse-time enum failure on create; body still has a valid title so the ONLY problem
            // is the unparseable enum, confirming the parse path (not the @Valid path) is taken.
            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"ok\",\"priority\":\"URGENT\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("Request body is missing or could not be parsed"))
                    .andExpect(jsonPath("$.errors", hasSize(0)));

            verifyNoInteractions(taskService);
        }
    }
}
