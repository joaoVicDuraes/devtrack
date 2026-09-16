import { useState } from 'react';
import StatusControl from './StatusControl.jsx';
import PriorityControl from './PriorityControl.jsx';

// TaskForm: the create/edit form for a Task.
// (Requirements 9.4 submit create, 9.5 block a blank title client-side,
//  9.6 submit an edit, 9.12 functional component.)
//
// This one component serves BOTH "create" and "edit" flows. Which mode it is in
// depends only on whether the parent passes an `initialTask`:
//   - no initialTask  -> create mode, fields start at new-task defaults
//   - with initialTask -> edit mode, fields are prefilled from that task
//
// Design boundaries (kept deliberately narrow):
//   - It does NOT import taskApi or make any HTTP calls. The parent (App) owns
//     the network request. This keeps API communication out of the visual
//     components (Requirement 9.11) and makes the form easy to test in isolation.
//   - Its only job is to render inputs, validate the title on the client, and
//     hand a clean data object up to the parent via `onSubmit`.
//
// Client-side title validation (Requirement 9.5) is "defense in depth": we trim
// the title and refuse to submit a blank/whitespace-only one, showing an inline
// message instead of firing a request that the backend would reject anyway. The
// backend still enforces the same rule authoritatively - we never trust the
// client alone.
//
// Props:
//   - initialTask: optional task to prefill the form for editing. Shape:
//       { title, description, status, priority }. When omitted, the form starts
//       as a blank new task (status TODO, priority MEDIUM).
//   - onSubmit:    callback invoked with the cleaned form data when the title is
//       valid: { title (trimmed), description, status, priority }.
//   - submitLabel: optional text for the submit button (e.g. "Add" vs "Save").
//   - disabled:    optional flag to disable inputs while a request is in flight.

// New-task defaults, matching the backend defaults for omitted fields
// (Requirements 1.2 status -> TODO, 1.3 priority -> MEDIUM, 1.4 description -> "").
const NEW_TASK = {
  title: '',
  description: '',
  status: 'TODO',
  priority: 'MEDIUM',
};

function TaskForm({ initialTask, onSubmit, submitLabel = 'Save', disabled = false }) {
  // Each field is a separate piece of controlled state. We seed them from
  // `initialTask` when editing, otherwise from the new-task defaults. Using the
  // `?? NEW_TASK.x` fallback means a partially-populated initialTask (missing,
  // say, a priority) still gets a sensible default rather than `undefined`.
  const [title, setTitle] = useState(initialTask?.title ?? NEW_TASK.title);
  const [description, setDescription] = useState(
    initialTask?.description ?? NEW_TASK.description,
  );
  const [status, setStatus] = useState(initialTask?.status ?? NEW_TASK.status);
  const [priority, setPriority] = useState(
    initialTask?.priority ?? NEW_TASK.priority,
  );

  // Holds the inline validation message. Empty string means "no error".
  const [titleError, setTitleError] = useState('');

  const handleSubmit = (event) => {
    // Stop the browser's default full-page form submission so React stays in
    // control (this is a single-page app, not a classic server round trip).
    event.preventDefault();

    // Trim once, then decide based on the trimmed value. `.trim()` removes
    // leading/trailing whitespace, so "   " becomes "" and is treated as empty.
    const trimmedTitle = title.trim();

    if (trimmedTitle.length === 0) {
      // Blank/whitespace-only title: show the inline message and DO NOT call
      // onSubmit, so no API request is made (Requirement 9.5).
      setTitleError('Title is required.');
      return;
    }

    // Valid: clear any previous error and hand the cleaned data to the parent.
    // We submit the trimmed title so the value we send matches what we validated.
    setTitleError('');
    onSubmit({
      title: trimmedTitle,
      description,
      status,
      priority,
    });
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div>
        <label htmlFor="task-title">Title</label>
        <input
          id="task-title"
          type="text"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          disabled={disabled}
          // Link the input to its error message for screen readers, and only
          // when an error is actually present.
          aria-invalid={titleError ? 'true' : undefined}
          aria-describedby={titleError ? 'task-title-error' : undefined}
        />
        {/* Inline validation message (Requirement 9.5). role="alert" makes
            assistive tech announce it as soon as it appears. */}
        {titleError && (
          <p id="task-title-error" role="alert">
            {titleError}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="task-description">Description</label>
        <textarea
          id="task-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          disabled={disabled}
        />
      </div>

      <div>
        <label htmlFor="task-status">Status</label>
        {/* Reuse the shared enum controls instead of re-listing options here. */}
        <StatusControl
          id="task-status"
          value={status}
          onChange={setStatus}
          disabled={disabled}
        />
      </div>

      <div>
        <label htmlFor="task-priority">Priority</label>
        <PriorityControl
          id="task-priority"
          value={priority}
          onChange={setPriority}
          disabled={disabled}
        />
      </div>

      <button type="submit" disabled={disabled}>
        {submitLabel}
      </button>
    </form>
  );
}

export default TaskForm;
