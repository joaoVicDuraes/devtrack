// App.test.jsx
//
// Component tests for the top-level container (src/App.jsx). App is the only
// component that talks to the network (through the taskApi module), so here we
// verify its three data-flow behaviors WITHOUT any real HTTP:
//
//   - Requirement 9.2  Empty state: when the list loads but is empty, show the
//                      "No tasks yet" message and render no task rows.
//   - Requirement 9.3  List-load failure: when the initial list request fails,
//                      show the failure message and render no task rows.
//   - Requirement 9.10 Failed mutation keeps the prior list: when a mutation
//                      (here, delete) fails, show the error message BUT leave
//                      the already-displayed list untouched.
//
// How we avoid real network traffic:
//   vi.mock('./api/taskApi.js', ...) replaces the entire module App imports.
//   App calls listTasks() on mount and the mutation functions on user actions;
//   with the module mocked, those calls hit our vi.fn() fakes instead of fetch.
//   We import the mocked functions below so each test can set their behavior
//   (mockResolvedValue / mockRejectedValue) and assert on how they were called.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from '@testing-library/react';

// Replace the network module with mocks. The factory returns a vi.fn() for each
// export App uses, plus a real-ish ApiError class so tests can throw one that
// carries a `message` (App reads err.message to build its error text).
vi.mock('./api/taskApi.js', () => {
  class ApiError extends Error {
    constructor(message, status = 500, fieldErrors = []) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.fieldErrors = fieldErrors;
    }
  }
  return {
    ApiError,
    listTasks: vi.fn(),
    getTask: vi.fn(),
    createTask: vi.fn(),
    updateTask: vi.fn(),
    changeStatus: vi.fn(),
    changePriority: vi.fn(),
    deleteTask: vi.fn(),
  };
});

// Import AFTER vi.mock so we get the mocked versions. These are the same vi.fn()
// instances App will call, so configuring them here controls App's behavior.
import App from './App.jsx';
import { listTasks, deleteTask, ApiError } from './api/taskApi.js';

// A small, stable fixture of tasks used by the "keep the list" test.
const SAMPLE_TASKS = [
  { id: 1, title: 'First task', description: '', status: 'TODO', priority: 'MEDIUM' },
  { id: 2, title: 'Second task', description: '', status: 'DONE', priority: 'LOW' },
];

beforeEach(() => {
  // Clear call history AND any per-test mockResolved/Rejected implementations so
  // one test's setup cannot leak into the next.
  vi.clearAllMocks();
});

afterEach(() => {
  // Unmount the rendered App between tests (Vitest auto-cleanup is not enabled).
  cleanup();
});

describe('App list states (Requirements 9.2, 9.3)', () => {
  it('empty state: shows "No tasks yet" and renders no task rows (Req 9.2)', async () => {
    // The list request succeeds but returns nothing.
    listTasks.mockResolvedValue([]);

    render(<App />);

    // findBy* waits for the async load (listTasks resolves, then state updates)
    // before asserting, so we are testing the post-load UI, not the loading one.
    expect(await screen.findByText('No tasks yet')).toBeInTheDocument();

    // "No rows": TaskList renders each task inside a <ul class="task-list">. In
    // the empty state it renders a <p> instead, so there is no list and no
    // list items at all.
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
  });

  it('list-load failure: shows the failure message and no task rows (Req 9.3)', async () => {
    // The initial list request fails. App should catch this, store the message,
    // and render it INSTEAD of the list.
    listTasks.mockRejectedValue(new ApiError('Could not load tasks', 500));

    render(<App />);

    // App renders the load error with role="alert"; assert on both the role and
    // the message text taken from err.message.
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Could not load tasks');

    // No list and no rows are shown when the load failed.
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
    // And the empty-state message must NOT appear - a failed load is not "empty".
    expect(screen.queryByText('No tasks yet')).not.toBeInTheDocument();
  });
});

describe('App mutation error keeps the prior list (Requirement 9.10)', () => {
  it('a failed delete shows the error message but leaves the original tasks on screen', async () => {
    // The list loads successfully with two tasks...
    listTasks.mockResolvedValue(SAMPLE_TASKS);
    // ...but the delete mutation will fail.
    deleteTask.mockRejectedValue(new ApiError('Delete failed', 500));

    // window.confirm is used by TaskItem before deleting. jsdom does not
    // implement it, so we stub it to always confirm, letting the delete proceed
    // to the (failing) API call.
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<App />);

    // Wait for the initial load: both task titles should be visible.
    expect(await screen.findByText('First task')).toBeInTheDocument();
    expect(screen.getByText('Second task')).toBeInTheDocument();
    // Two rows loaded.
    expect(screen.getAllByRole('listitem')).toHaveLength(2);

    // Trigger a delete on the first task. Each row renders its own "Delete"
    // button; we click the first one.
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete' })[0]);

    // The mutation error surfaces as a role="alert" with the message.
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Delete failed');
    });

    // Confirm the mutation was actually attempted with the right id...
    expect(deleteTask).toHaveBeenCalledTimes(1);
    expect(deleteTask).toHaveBeenCalledWith(1);

    // ...yet the list is UNCHANGED (Req 9.10): both original tasks remain and
    // there are still exactly two rows. Nothing was optimistically removed.
    expect(screen.getByText('First task')).toBeInTheDocument();
    expect(screen.getByText('Second task')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });
});
