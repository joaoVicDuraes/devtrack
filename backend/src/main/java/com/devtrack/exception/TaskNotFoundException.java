package com.devtrack.exception;

/**
 * Thrown by the service layer when a task operation targets a {@code Task_Id} that is not stored.
 *
 * <p>Instead of returning {@code null} or an {@code Optional} up to the controller, the service
 * throws this exception when a task is missing. That keeps the controller thin: it never has to
 * check for "not found" itself. A single {@code GlobalExceptionHandler} (added in a later task)
 * catches this one exception type and turns it into an HTTP 404 response in one place, which is why
 * the same exception backs every "missing id" requirement (3.2, 4.4, 5.3, 6.3, 7.2).
 *
 * <p><strong>Why extend {@link RuntimeException} (an <em>unchecked</em> exception)?</strong> Checked
 * exceptions (those extending {@code Exception}) force every caller to declare or catch them, which
 * would clutter the service and controller signatures. A missing task is an exceptional condition we
 * want to handle centrally, not something each caller should be forced to handle locally, so an
 * unchecked exception is the simpler, cleaner fit. Spring's own data-access exceptions follow the
 * same convention.
 *
 * <p>Requirements: 8.1, 8.3.
 */
public class TaskNotFoundException extends RuntimeException {

    /**
     * Builds the exception from the id that could not be found, producing a message such as
     * {@code "Task not found: 42"}.
     *
     * @param id the requested {@code Task_Id} that does not correspond to a stored task
     */
    public TaskNotFoundException(Long id) {
        super("Task not found: " + id);
    }
}
