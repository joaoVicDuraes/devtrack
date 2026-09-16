package com.devtrack.controller;

import com.devtrack.dto.ErrorResponse;
import com.devtrack.exception.GlobalExceptionHandler;
import com.devtrack.service.TaskService;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for Property 12 of the DevTrack design.
 *
 * <p>Feature: devtrack-task-management, Property 12: A validation error reports every failed field
 *
 * <p>The property: for any request that violates validation on a <em>set</em> of fields, the 400
 * error {@code Response_DTO} names <em>exactly</em> that set of fields, not merely the first one
 * (Requirements 8.7 and 8.9).
 *
 * <p><strong>Why this is a web-layer property, not a service property.</strong> Requirements 8.7
 * and 8.9 are about the shape of the HTTP <em>error body</em>: a failed {@code @Valid} check must
 * produce a 400 whose {@link ErrorResponse#errors()} list contains one entry per failed field. That
 * behaviour lives entirely in the request pipeline — Jakarta Validation raises a
 * {@code MethodArgumentNotValidException} carrying <em>all</em> field violations, and the
 * {@link GlobalExceptionHandler} maps every one of them into an {@link ErrorResponse.FieldError}.
 * So the property has to be checked through an actual request/validation/handler cycle, which is
 * what {@link MockMvc} gives us here.
 *
 * <p><strong>Why a standalone {@link MockMvc} (not {@code @WebMvcTest}).</strong> jqwik drives each
 * case on its own test engine, so a Spring-injected {@code MockMvc} field is not available the way
 * it is in a plain JUnit web-slice test. Instead we assemble the exact same collaborators by hand
 * once, in {@link #mockMvc()}: the real {@link TaskController}, wired to a Mockito-mocked
 * {@link TaskService}, with the real {@link GlobalExceptionHandler} registered as controller advice.
 * That is enough to exercise {@code @Valid} on the request body and the handler that builds the
 * error body — no database or full application context is needed. The service is a mock purely so
 * the controller can be constructed; validation fails before it, so the mock is never called
 * (asserted below), which also re-confirms Requirement 8.5 on this path.
 *
 * <p><strong>How the property is exercised.</strong> {@code CreateTaskRequest} carries exactly two
 * fields that Jakarta Validation can flag: {@code title} ({@code @NotBlank} + {@code @Size(max=150)})
 * and {@code description} ({@code @Size(max=2000)}). (An invalid {@code status}/{@code priority}
 * enum fails earlier, at JSON parse time, and is a different 400 path — the design routes it through
 * {@code HttpMessageNotReadableException}, not the per-field validation handler — so it is out of
 * scope for this property.) For each generated non-empty subset of {@code {title, description}} we
 * build a body where precisely those fields are invalid and the rest are valid, POST it, and assert
 * the set of field names in the 400 body equals exactly the generated subset. Ranging over every
 * non-empty subset is what proves "names exactly that set" — including the multi-field case that
 * distinguishes 8.9 from "report only the first failure".
 *
 * <p><strong>Validates: Requirements 8.7, 8.9</strong>
 */
class GlobalExceptionHandlerFailedFieldsPropertyTest {

    /** Reused to serialize generated bodies and deserialize the error response body. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Builds the request pipeline under test: the real controller + real global exception handler,
     * with a mocked service the controller depends on but validation never lets us reach.
     *
     * <p>Returns the mock alongside the {@link MockMvc} so a test can assert the service was never
     * invoked (validation short-circuits before delegation, Requirement 8.5).
     */
    private record Pipeline(MockMvc mockMvc, TaskService serviceMock) {}

    private static Pipeline pipeline() {
        TaskService serviceMock = Mockito.mock(TaskService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(serviceMock))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        return new Pipeline(mockMvc, serviceMock);
    }

    /**
     * Property 12: a create request that is invalid on a chosen set of fields yields a 400 whose
     * error body names exactly that set of fields — no more, no fewer.
     *
     * @param failedFields the non-empty set of fields to make invalid in this generated case
     */
    @Property(tries = 200)
    void validationErrorNamesExactlyEveryFailedField(
            @ForAll("nonEmptyFailedFieldSets") Set<String> failedFields) throws Exception {

        Pipeline pipeline = pipeline();

        // Build a JSON body where precisely the chosen fields are invalid and the others are valid,
        // so the ONLY validation failures are the ones we intend for this case.
        String body = buildBody(failedFields);

        MvcResult result = pipeline.mockMvc()
                .perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse error = OBJECT_MAPPER.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);

        // The set of field names in the error body must equal exactly the set we made invalid.
        Set<String> reportedFields = error.errors().stream()
                .map(ErrorResponse.FieldError::field)
                .collect(Collectors.toSet());

        assertThat(reportedFields).isEqualTo(failedFields);

        // Every reported field must also carry a (non-blank) reason for the failure (8.7).
        assertThat(error.errors())
                .allSatisfy(fieldError -> assertThat(fieldError.message()).isNotBlank());

        // Validation must have short-circuited before the business layer (Requirement 8.5).
        verifyNoInteractions(pipeline.serviceMock());
    }

    /**
     * Assembles a {@code CreateTaskRequest} JSON body. Each field named in {@code failedFields} is
     * given a deliberately invalid value; the remaining fields are given valid values so they do not
     * contribute extra entries to the error list.
     *
     * <ul>
     *   <li>{@code title} invalid: blank ({@code ""}) so {@code @NotBlank} fails.</li>
     *   <li>{@code description} invalid: a 2001-character string so {@code @Size(max=2000)} fails.</li>
     * </ul>
     */
    private static String buildBody(Set<String> failedFields) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("title", failedFields.contains("title") ? "" : "A valid title");
        json.put("description",
                failedFields.contains("description") ? "x".repeat(2001) : "A valid description");

        return OBJECT_MAPPER.writeValueAsString(json);
    }

    // ---------------------------------------------------------------------
    // Generator (jqwik @Provide method)
    // ---------------------------------------------------------------------

    /**
     * Every non-empty subset of the two validation-carrying fields on {@code CreateTaskRequest}:
     * {@code {title}}, {@code {description}}, and {@code {title, description}}. Ranging over all of
     * these — especially the two-field case — is what exercises "report all failed fields, not just
     * the first" (Requirement 8.9).
     */
    @Provide
    Arbitrary<Set<String>> nonEmptyFailedFieldSets() {
        return Arbitraries.of(
                Set.of("title"),
                Set.of("description"),
                Set.of("title", "description"));
    }
}
