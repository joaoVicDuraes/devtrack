// taskApi.test.js
//
// Unit tests for the network module (src/api/taskApi.js). The whole point of
// this module is to be the single place that knows about HTTP, so these tests
// verify that contract WITHOUT touching a real server: we replace the global
// `fetch` with a mock and assert on what the module asked it to do (URL,
// method, headers, body) and how it interprets what comes back (parsed JSON,
// `undefined` for 204, or a thrown ApiError on non-2xx).
//
// Why mock `fetch`?
//   The module calls the global `fetch`. In tests we don't want real network
//   traffic (slow, flaky, needs a running backend). `vi.stubGlobal('fetch', ...)`
//   swaps in a fake function we control, so each test can dictate exactly what
//   the "server" returns and then inspect how the module reacted.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  listTasks,
  getTask,
  createTask,
  updateTask,
  changeStatus,
  changePriority,
  deleteTask,
  ApiError,
} from './taskApi.js';

// --- Test helpers ----------------------------------------------------------

/**
 * Build a fake `Response`-like object good enough for what taskApi.js reads.
 *
 * The module only ever touches `response.ok`, `response.status`, and
 * `response.json()`, so we don't need a full Response — a small object with
 * those three is enough. `ok` mirrors the real Fetch rule (true for 200–299).
 *
 * `json` is a `vi.fn()` so, when needed, a test could assert it was (or wasn't)
 * called; by default it resolves to whatever `body` we pass in.
 */
function makeResponse({ status = 200, body = undefined } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  };
}

/** Convenience: install a fetch mock that resolves to the given response. */
function mockFetchResolving(response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

// --- Setup / teardown ------------------------------------------------------
// Each test gets a clean slate: no leftover mock call history, and the real
// globals restored so a stubbed `fetch` from one test can't leak into another.

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// URL + method + body: each function calls fetch as the REST contract expects.
// ---------------------------------------------------------------------------

describe('taskApi request shape (URL, method, headers, body)', () => {
  it('listTasks issues GET /tasks with no body', async () => {
    const fetchMock = mockFetchResolving(makeResponse({ body: [] }));

    await listTasks();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks');
    // A plain read: default method (GET) and no body/headers attached.
    expect(options.method).toBe('GET');
    expect(options.body).toBeUndefined();
    expect(options.headers).toBeUndefined();
  });

  it('getTask issues GET /tasks/{id}', async () => {
    const fetchMock = mockFetchResolving(makeResponse({ body: { id: 42 } }));

    await getTask(42);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks/42');
    expect(options.method).toBe('GET');
    expect(options.body).toBeUndefined();
  });

  it('createTask issues POST /tasks with JSON Content-Type and serialized body', async () => {
    const fetchMock = mockFetchResolving(
      makeResponse({ status: 201, body: { id: 1 } })
    );
    const data = { title: 'Write tests', priority: 'HIGH' };

    await createTask(data);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks');
    expect(options.method).toBe('POST');
    // Write requests must announce a JSON body and serialize the payload.
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(options.body).toBe(JSON.stringify(data));
  });

  it('updateTask issues PUT /tasks/{id} with the full payload as JSON', async () => {
    const fetchMock = mockFetchResolving(makeResponse({ body: { id: 7 } }));
    const data = { title: 'Edit', description: 'x', status: 'DONE', priority: 'LOW' };

    await updateTask(7, data);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks/7');
    expect(options.method).toBe('PUT');
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(options.body).toBe(JSON.stringify(data));
  });

  it('changeStatus issues PATCH /tasks/{id}/status wrapping the value in { status }', async () => {
    const fetchMock = mockFetchResolving(makeResponse({ body: { id: 3 } }));

    await changeStatus(3, 'IN_PROGRESS');

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks/3/status');
    expect(options.method).toBe('PATCH');
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' });
    // The function builds the body object itself; only `status` is sent.
    expect(options.body).toBe(JSON.stringify({ status: 'IN_PROGRESS' }));
  });

  it('changePriority issues PATCH /tasks/{id}/priority wrapping the value in { priority }', async () => {
    const fetchMock = mockFetchResolving(makeResponse({ body: { id: 9 } }));

    await changePriority(9, 'HIGH');

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks/9/priority');
    expect(options.method).toBe('PATCH');
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(options.body).toBe(JSON.stringify({ priority: 'HIGH' }));
  });

  it('deleteTask issues DELETE /tasks/{id} with no body', async () => {
    const fetchMock = mockFetchResolving(makeResponse({ status: 204 }));

    await deleteTask(5);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/tasks/5');
    expect(options.method).toBe('DELETE');
    // DELETE is a write in HTTP terms but carries no payload here, so the
    // module must NOT attach a JSON Content-Type or a body.
    expect(options.body).toBeUndefined();
    expect(options.headers).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Success bodies: 2xx responses resolve to parsed JSON; 204 resolves to undefined.
// ---------------------------------------------------------------------------

describe('taskApi success handling', () => {
  it('resolves to the parsed JSON array for listTasks', async () => {
    const tasks = [
      { id: 1, title: 'A', description: '', status: 'TODO', priority: 'MEDIUM' },
      { id: 2, title: 'B', description: '', status: 'DONE', priority: 'LOW' },
    ];
    mockFetchResolving(makeResponse({ body: tasks }));

    await expect(listTasks()).resolves.toEqual(tasks);
  });

  it('resolves to the parsed task object for getTask', async () => {
    const task = { id: 42, title: 'X', description: '', status: 'TODO', priority: 'HIGH' };
    mockFetchResolving(makeResponse({ body: task }));

    await expect(getTask(42)).resolves.toEqual(task);
  });

  it('resolves to the created task (201) for createTask', async () => {
    const created = { id: 10, title: 'New', description: '', status: 'TODO', priority: 'MEDIUM' };
    mockFetchResolving(makeResponse({ status: 201, body: created }));

    await expect(createTask({ title: 'New' })).resolves.toEqual(created);
  });

  it('resolves to undefined (no body parsed) on a 204 from deleteTask', async () => {
    const response = makeResponse({ status: 204 });
    mockFetchResolving(response);

    await expect(deleteTask(5)).resolves.toBeUndefined();
    // 204 has no body, so the module must not attempt to parse it.
    expect(response.json).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Error handling: any non-2xx is normalized into a thrown ApiError that carries
// the message/status/fieldErrors parsed from the backend's ErrorResponse body.
// ---------------------------------------------------------------------------

describe('taskApi error handling', () => {
  it('throws an ApiError with message and status from the ErrorResponse body', async () => {
    const errorBody = { status: 404, message: 'Task 99 not found', errors: [] };
    mockFetchResolving(makeResponse({ status: 404, body: errorBody }));

    // rejects.toThrow checks the message; we then re-catch to inspect fields.
    await expect(getTask(99)).rejects.toBeInstanceOf(ApiError);

    const error = await getTask(99).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.message).toBe('Task 99 not found');
    expect(error.status).toBe(404);
    expect(error.fieldErrors).toEqual([]);
  });

  it('surfaces field-level validation errors from a 400 body', async () => {
    const errorBody = {
      status: 400,
      message: 'Validation failed',
      errors: [{ field: 'title', message: 'must not be blank' }],
    };
    mockFetchResolving(makeResponse({ status: 400, body: errorBody }));

    const error = await createTask({ title: '' }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Validation failed');
    expect(error.fieldErrors).toEqual([
      { field: 'title', message: 'must not be blank' },
    ]);
  });

  it('falls back to a generic message when the error body is not JSON', async () => {
    // Simulate a gateway that returned non-JSON: response.json() rejects.
    const response = {
      ok: false,
      status: 500,
      json: vi.fn().mockRejectedValue(new SyntaxError('Unexpected token')),
    };
    mockFetchResolving(response);

    const error = await listTasks().catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(500);
    // Generic message built from the status, since no message could be parsed.
    expect(error.message).toBe('Request failed with status 500');
    expect(error.fieldErrors).toEqual([]);
  });
});
