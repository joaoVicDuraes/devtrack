package com.devtrack.exception;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.devtrack.dto.ErrorResponse;

/**
 * The single, centralized place that turns exceptions thrown anywhere in the request-handling flow
 * into the uniform {@link ErrorResponse} body with the correct HTTP status code.
 *
 * <p><strong>Why one advice class?</strong> Without it, every controller method would need its own
 * try/catch to translate failures into HTTP responses, and the shapes would inevitably drift apart.
 * By concentrating all error translation here, the {@link com.devtrack.controller.TaskController}
 * stays free of any error handling and the Frontend always parses one predictable body
 * (Requirements 8.7&ndash;8.10).
 *
 * <p><strong>Junior-dev note &mdash; {@code @RestControllerAdvice}:</strong> this is a Spring class
 * whose methods run <em>after</em> a controller (or the framework, while parsing a request) throws
 * an exception. Each method is annotated with {@code @ExceptionHandler(SomeException.class)} and
 * returns the body + status for that error type. Think of it as a shared {@code catch} block for the
 * whole API. "Rest" in the name means the returned values are serialized straight to JSON (like a
 * {@code @RestController}), so we can return either an {@link ErrorResponse} or a
 * {@link ResponseEntity} wrapping one.
 *
 * <p><strong>Security note &mdash; no leaking internals (Requirements 8.8, 8.10).</strong> None of
 * the handlers below put the exception's stack trace, message, or class name into the response. For
 * parse errors, mismatched ids, and server errors we return fixed, generic sentences. Exposing
 * internal class names or stack traces would hand an attacker a map of the implementation and could
 * reveal sensitive detail, so the client only ever sees a clean, human-readable summary.
 *
 * <p>Requirements: 8.7, 8.8, 8.9, 8.10, 1.11, 3.2, 3.3, 4.4, 5.3, 6.3, 7.2, 7.3.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles a DTO that failed Jakarta Validation (e.g. a blank {@code title} or a {@code null}
     * required enum): HTTP 400 whose body lists <em>every</em> failed field and its reason, not just
     * the first one (Requirements 8.7, 8.9; Property 12).
     *
     * <p><strong>Junior-dev note &mdash; where this comes from:</strong> when a request-body DTO is
     * annotated {@code @Valid} on a controller method and one or more constraints fail, Spring raises
     * {@link MethodArgumentNotValidException} <em>before</em> the controller method body runs. That
     * exception carries a {@link org.springframework.validation.BindingResult} describing each field
     * that failed. We read those results and map each one into an
     * {@link ErrorResponse.FieldError} so the client can highlight all invalid fields at once.
     *
     * <p><strong>Why import two different {@code FieldError} types?</strong> Spring's own
     * {@link org.springframework.validation.FieldError} represents a validation failure it detected;
     * our {@link ErrorResponse.FieldError} is the small DTO we expose in the JSON body. This method's
     * job is to translate the former into the latter, so we import both and rely on their fully- or
     * partially-qualified names to keep them distinct.
     *
     * @param ex the validation failure raised by the {@code @Valid} check
     * @return HTTP 400 with a per-field breakdown of what failed
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // getFieldErrors() returns one entry per failed field. We map each to our public FieldError
        // shape, keeping only the field name and the constraint's message (never any internal detail).
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), messageOf(error)))
                .toList();

        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed for one or more fields",
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles an unreadable request body: malformed/empty JSON, or a JSON value that cannot be
     * converted (most notably an invalid enum such as {@code "status":"NOPE"}). HTTP 400 with a
     * fixed, generic parse message (Requirements 1.11, 8.8).
     *
     * <p><strong>Junior-dev note &mdash; why invalid enums land here:</strong> when JSON like
     * {@code "status":"NOPE"} arrives, Jackson (the JSON library) fails to turn {@code "NOPE"} into a
     * {@code TaskStatus} while it is still <em>reading</em> the body, before {@code @Valid} runs.
     * Spring wraps that failure as {@link HttpMessageNotReadableException}, the same exception used
     * for outright malformed JSON. So both cases share this one handler and one generic message. We
     * deliberately do <strong>not</strong> echo the offending value or the parser's internal message,
     * to avoid leaking implementation detail (8.8).
     *
     * @param ex the parse failure raised while reading the request body
     * @return HTTP 400 with a generic "could not be parsed" message and no field list
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Request body is missing or could not be parsed",
                List.of()
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles a path variable that cannot be converted to its declared type &mdash; in practice a
     * non-numeric id such as {@code /tasks/abc} that cannot become a {@code Long}. HTTP 400 with a
     * generic "invalid identifier" message (Requirements 3.3, 7.3).
     *
     * <p><strong>Junior-dev note:</strong> Spring tries to convert the {@code {id}} path segment into
     * the {@code Long} parameter before the controller method runs. When that conversion fails it
     * raises {@link MethodArgumentTypeMismatchException}. As with the parse handler, we return a fixed
     * message rather than echoing the bad value.
     *
     * @param ex the conversion failure raised for the offending path variable
     * @return HTTP 400 with a generic invalid-identifier message
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid identifier in request path",
                List.of()
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles a task operation that targeted an id which does not exist. HTTP 404 (Requirements 3.2,
     * 4.4, 5.3, 6.3, 7.2).
     *
     * <p>This is the one place the service's {@link TaskNotFoundException} is translated to an HTTP
     * status, which is exactly why the service can throw a single exception type for every "missing
     * id" path and stay unaware of HTTP. The exception's message (e.g. {@code "Task not found: 42"})
     * is safe to surface: it contains only the id the client already sent, never internal detail.
     *
     * @param ex the not-found signal raised by the service layer
     * @return HTTP 404 describing the missing task
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TaskNotFoundException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles any persistence-layer failure &mdash; for example MySQL being unavailable. HTTP 500
     * with a fixed, generic message (Requirement 8.10).
     *
     * <p><strong>Junior-dev note &mdash; {@link DataAccessException}:</strong> Spring Data JPA wraps
     * the many vendor- and driver-specific database exceptions into this single unchecked hierarchy.
     * Catching the base type here means we cover every persistence failure without listing each
     * concrete subclass. Because such failures often carry SQL fragments, connection URLs, or driver
     * class names in their messages, we must <strong>not</strong> forward the exception's own message;
     * we return a deliberately vague sentence and let server-side logging (Spring logs the stack trace
     * by default) hold the real diagnostic detail (8.10).
     *
     * @param ex the database/persistence failure
     * @return HTTP 500 with a generic "operation could not be completed" message
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "The operation could not be completed. Please try again later.",
                List.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Extracts a safe, human-readable message for a single failed field, falling back to a neutral
     * sentence if the constraint did not supply one.
     *
     * @param error the Spring validation error for one field
     * @return the constraint's default message, or a generic fallback when it is absent
     */
    private static String messageOf(FieldError error) {
        String message = error.getDefaultMessage();
        return (message != null && !message.isBlank()) ? message : "is invalid";
    }
}
