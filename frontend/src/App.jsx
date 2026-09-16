import { useEffect, useState } from 'react';
import {
  listTasks,
  createTask,
  updateTask,
  changeStatus,
  changePriority,
  deleteTask,
} from './api/taskApi.js';
import TaskForm from './components/TaskForm.jsx';
import TaskList from './components/TaskList.jsx';

// App: the top-level orchestration component for DevTrack.
// (Requirements 9.1 load+display, 9.2 empty state, 9.3 list-load failure,
//  9.4 create, 9.6 edit, 9.7 change status, 9.8 change priority, 9.9 delete,
//  9.10 mutation error keeps the list, 9.12 functional component.)
//
// WHY App owns all of this:
// The visual components (TaskForm, TaskList, TaskItem, StatusControl,
// PriorityControl) are deliberately "dumb" - they render what they are given
// and report user intent back through callbacks. None of them call the API.
// App is the single place that:
//   1. holds the application state (the task list, plus loading/error flags), and
//   2. calls the taskApi functions and folds their results back into state.
// This is the classic "smart container / dumb presentational" split. Keeping
// the network calls in taskApi and the state in one container keeps API
// communication out of the visual components (Requirement 9.11) and makes the
// data flow easy to follow: state flows DOWN as props, intent flows UP as
// callbacks.
//
// Two DISTINCT kinds of error, tracked separately on purpose:
//   - loadError:     the initial list request failed. In this case we have no
//                    trustworthy data, so we must show the failure message and
//                    render NO task rows (Requirement 9.3).
//   - actionError:   a single mutation (create/edit/status/priority/delete)
//                    failed. Here we already have a good list on screen, so we
//                    show the message but KEEP the previous list unchanged
//                    (Requirement 9.10).
// Collapsing both into one "error" string would make it impossible to honor
// these two different UI rules, which is why they are separate state values.

function App() {
  // The loaded tasks. Empty array is the natural "nothing yet" value and lets
  // TaskList show its "No tasks yet" empty state (Requirement 9.2).
  const [tasks, setTasks] = useState([]);

  // True only while the INITIAL list request is in flight, so we can show a
  // loading indicator and avoid flashing an empty/error state prematurely.
  const [loading, setLoading] = useState(true);

  // Error from the initial list load (Requirement 9.3). When set, we render the
  // failure message and no rows. Null means "the list loaded fine".
  const [loadError, setLoadError] = useState(null);

  // Error from a mutation (Requirement 9.10). When set, we show the message but
  // leave `tasks` as-is. Null means "no pending mutation error".
  const [actionError, setActionError] = useState(null);

  // True while any mutation (create/edit/status/priority/delete) is in flight.
  // We use it to disable the form so the user cannot fire a second create while
  // the first is still running.
  const [submitting, setSubmitting] = useState(false);

  // The task currently being edited, or null when we are in "create" mode.
  // Setting this swaps TaskForm from create mode to edit mode (Requirement 9.6).
  const [editingTask, setEditingTask] = useState(null);

  // --- Initial load --------------------------------------------------------
  // useEffect with an empty dependency array runs ONCE after the first render
  // (the component "mounts"). This is where side effects like data fetching
  // belong in React - we must not fetch during render itself.
  //
  // Note the inner `load` async function: useEffect's callback cannot itself be
  // `async` (an async function returns a Promise, but React expects the effect
  // to return either nothing or a cleanup function). So we define `load` inside
  // and call it. This satisfies Requirement 9.1 (request the list on load).
  useEffect(() => {
    async function load() {
      try {
        const data = await listTasks();
        setTasks(data);
        setLoadError(null);
      } catch (err) {
        // The list failed to load: we have no trustworthy data. Record the
        // message and make sure no stale rows are shown (Requirement 9.3).
        setLoadError(err.message);
        setTasks([]);
      } finally {
        // Whether it succeeded or failed, the initial load is over, so stop
        // showing the loading indicator.
        setLoading(false);
      }
    }

    load();
  }, []);

  // --- Mutation helper -----------------------------------------------------
  // Every mutation follows the same shape: clear the previous action error,
  // mark ourselves as submitting, run the API call, apply its result to state,
  // and on failure surface the message WITHOUT touching the task list. Rather
  // than repeat that boilerplate in five handlers, we centralize it here.
  //
  // `action` is an async function that performs the API call and, on success,
  // is responsible for updating `tasks` via the setter it closes over. This
  // helper only owns the cross-cutting concerns (error + submitting flags), so
  // each caller stays a couple of readable lines.
  async function runMutation(action) {
    setActionError(null);
    setSubmitting(true);
    try {
      await action();
    } catch (err) {
      // A mutation failed. Show the message but deliberately do NOT modify
      // `tasks` here, so the previously displayed list stays intact
      // (Requirement 9.10).
      setActionError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  // --- Create / Edit -------------------------------------------------------
  // TaskForm calls onSubmit with a cleaned { title, description, status,
  // priority } object. Which branch we take depends on whether we are editing
  // an existing task or creating a new one.
  const handleFormSubmit = (data) => {
    if (editingTask) {
      // Edit mode (Requirement 9.6): full update via PUT. On success we replace
      // that one task in the list with the server's returned version and leave
      // edit mode.
      runMutation(async () => {
        const updated = await updateTask(editingTask.id, data);
        setTasks((prev) =>
          prev.map((task) => (task.id === updated.id ? updated : task)),
        );
        setEditingTask(null);
      });
    } else {
      // Create mode (Requirement 9.4): POST, then append the returned task
      // (which now has a backend-assigned id) to the end of the list.
      runMutation(async () => {
        const created = await createTask(data);
        setTasks((prev) => [...prev, created]);
      });
    }
  };

  // Start editing a given task: swap the form into edit mode. Wired to
  // TaskList's onEdit, which surfaces an Edit button per row (Requirement 9.6).
  const handleStartEdit = (task) => {
    setActionError(null);
    setEditingTask(task);
  };

  // Cancel an in-progress edit and return to create mode.
  const handleCancelEdit = () => {
    setEditingTask(null);
  };

  // --- Row mutations -------------------------------------------------------
  // Each of these replaces exactly the one task the backend returns. We map
  // over the list and swap in the updated task by id, leaving every other row
  // untouched (mirrors the backend's targeted-update behavior).

  const handleChangeStatus = (id, status) => {
    // Requirement 9.7
    runMutation(async () => {
      const updated = await changeStatus(id, status);
      setTasks((prev) =>
        prev.map((task) => (task.id === updated.id ? updated : task)),
      );
    });
  };

  const handleChangePriority = (id, priority) => {
    // Requirement 9.8
    runMutation(async () => {
      const updated = await changePriority(id, priority);
      setTasks((prev) =>
        prev.map((task) => (task.id === updated.id ? updated : task)),
      );
    });
  };

  const handleDelete = (id) => {
    // Requirement 9.9: on success remove the task from the displayed list.
    // deleteTask resolves to undefined (204 No Content), so there is nothing to
    // merge - we just filter the deleted id out.
    runMutation(async () => {
      await deleteTask(id);
      setTasks((prev) => prev.filter((task) => task.id !== id));
    });
  };

  // --- Render --------------------------------------------------------------
  return (
    // `.app` centers the content column and adds page padding (see index.css).
    // We keep <main> as the semantic landmark and give it the layout class.
    <main className="app">
      <header className="app__header">
        <h1>DevTrack</h1>
      </header>

      {/* Create/edit form (Requirements 9.4, 9.6).
          - `key` forces React to rebuild the form (and thus reset its internal
            field state) whenever we switch between creating and editing a
            different task; without it, the prefilled values would go stale.
          - `initialTask` puts the form in edit mode when set.
          - `disabled` blocks a second submission while one is in flight. */}
      <section
        className="task-form-section"
        aria-label={editingTask ? 'Edit task' : 'Create task'}
      >
        <h2>{editingTask ? 'Edit task' : 'New task'}</h2>
        <TaskForm
          key={editingTask ? `edit-${editingTask.id}` : 'create'}
          initialTask={editingTask ?? undefined}
          onSubmit={handleFormSubmit}
          submitLabel={editingTask ? 'Save' : 'Add'}
          disabled={submitting}
        />
        {editingTask && (
          <button
            type="button"
            className="btn btn--secondary"
            onClick={handleCancelEdit}
            disabled={submitting}
          >
            Cancel
          </button>
        )}
      </section>

      {/* Mutation error (Requirement 9.10): shown ABOVE the list, which remains
          on screen unchanged. role="alert" so assistive tech announces it. */}
      {actionError && (
        <p role="alert" className="action-error">
          {actionError}
        </p>
      )}

      {/* The list area is one of three mutually exclusive states: loading,
          load-failed, or loaded. */}
      {loading ? (
        // Loading indicator while the initial request is in flight.
        <p className="loading">Loading tasks...</p>
      ) : loadError ? (
        // List-load failure (Requirement 9.3): show the message and NO rows.
        // We render the error INSTEAD of <TaskList>, so there is no chance of
        // displaying stale/empty data as if it were real.
        <p role="alert" className="load-error">
          {loadError}
        </p>
      ) : (
        // Success: hand the loaded tasks and mutation callbacks to TaskList.
        // When `tasks` is empty, TaskList shows its "No tasks yet" empty state
        // (Requirement 9.2).
        <TaskList
          tasks={tasks}
          onChangeStatus={handleChangeStatus}
          onChangePriority={handleChangePriority}
          onDelete={handleDelete}
          onEdit={handleStartEdit}
        />
      )}
    </main>
  );
}

export default App;
