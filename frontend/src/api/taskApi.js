// taskApi.js
//
// This module is the ONLY place in the Frontend that knows about HTTP: URLs,
// methods, status codes, and how the Backend reports errors. Components never
// call `fetch` directly. They import these functions, pass plain data in, and
// get plain data (or a thrown Error) back. Keeping all network code here means
// the UI stays focused on rendering, and we could later swap `fetch` for a
// library like axios without touching a single component (Requirement 9.11).
//
// Backend REST contract (see design.md):
//   GET    /tasks                -> 200, TaskResponse[]
//   GET    /tasks/{id}           -> 200 TaskResponse | 404
//   POST   /tasks                -> 201 TaskResponse
//   PUT    /tasks/{id}           -> 200 TaskResponse
//   PATCH  /tasks/{id}/status    -> 200 TaskResponse
//   PATCH  /tasks/{id}/priority  -> 200 TaskResponse
//   DELETE /tasks/{id}           -> 204 No Content
//
// A TaskResponse looks like: { id, title, description, status, priority }
// An error body (ErrorResponse) looks like:
//   { status: number, message: string, errors: [{ field, message }] }

// Base URL for the API.
//
// We read it from a Vite environment variable (`VITE_API_BASE_URL`). Vite
// exposes any variable prefixed with `VITE_` on `import.meta.env` at build
// time. If it is not set, we default to an empty string, which makes every
// request relative (e.g. "/tasks"). Relative URLs are convenient in dev when a
// Vite proxy forwards "/tasks" to the backend, and in prod when the SPA is
// served from the same origin as the API.
//
// The `?.` (optional chaining) guards against `import.meta.env` being undefined
// in environments where Vite has not injected it.
const BASE_URL = import.meta.env?.VITE_API_BASE_URL ?? '';

/**
 * Error type thrown for any non-2xx response.
 *
 * We use a dedicated subclass (instead of a plain Error) so callers can, if
 * they want, check `err instanceof ApiError` and read the HTTP `status` and the
 * per-field validation errors. It still behaves like a normal Error, so code
 * that only reads `err.message` keeps working.
 */
export class ApiError extends Error {
  constructor(message, status, fieldErrors = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    // Array of { field, message } from the backend's ErrorResponse.errors,
    // or an empty array when the response had no field-level details.
    this.fieldErrors = fieldErrors;
  }
}

/**
 * Turn a non-2xx `Response` into a thrown ApiError.
 *
 * We try to parse the backend's ErrorResponse JSON to surface a useful message
 * and the field errors. The body might be missing or not be JSON (e.g. a
 * gateway returned plain text), so the parse is wrapped in try/catch and we
 * fall back to a generic message built from the status.
 */
async function throwApiError(response) {
  let message = `Request failed with status ${response.status}`;
  let fieldErrors = [];

  try {
    const body = await response.json();
    if (body && typeof body.message === 'string' && body.message.length > 0) {
      message = body.message;
    }
    if (Array.isArray(body?.errors)) {
      fieldErrors = body.errors;
    }
  } catch {
    // No JSON body (or unparseable). Keep the generic message above.
  }

  throw new ApiError(message, response.status, fieldErrors);
}

/**
 * Shared request helper.
 *
 * Every function below funnels through here so error handling and JSON parsing
 * live in one place. It:
 *   - prefixes the path with BASE_URL,
 *   - sets a JSON Content-Type only when there is a body to send,
 *   - throws an ApiError on any non-2xx response,
 *   - returns parsed JSON for successful responses, or `undefined` for 204.
 *
 * `body`, when provided, is a plain object that we serialize to JSON.
 */
async function request(path, { method = 'GET', body } = {}) {
  const options = { method };

  if (body !== undefined) {
    // Only set headers/body for write requests. GET/DELETE have no body.
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${BASE_URL}${path}`, options);

  if (!response.ok) {
    // response.ok is true for 200–299. Anything else is normalized to a throw.
    await throwApiError(response);
  }

  // 204 No Content (used by DELETE) has no body to parse. Trying to call
  // response.json() on it would throw, so we return undefined instead.
  if (response.status === 204) {
    return undefined;
  }

  return response.json();
}

// --- Public API ------------------------------------------------------------
// Each function maps to exactly one backend endpoint. They return the parsed
// TaskResponse (or an array of them), and throw an ApiError on failure.

/** GET /tasks -> TaskResponse[] (ascending id order, per the backend). */
export function listTasks() {
  return request('/tasks');
}

/** GET /tasks/{id} -> TaskResponse (throws ApiError with status 404 if absent). */
export function getTask(id) {
  return request(`/tasks/${id}`);
}

/**
 * POST /tasks -> TaskResponse (201).
 * `data` is { title, description?, status?, priority? }. Omitted optional
 * fields are defaulted by the backend (status=TODO, priority=MEDIUM, desc="").
 */
export function createTask(data) {
  return request('/tasks', { method: 'POST', body: data });
}

/**
 * PUT /tasks/{id} -> TaskResponse (200). Full edit: replaces all fields.
 * `data` has the same shape as createTask's argument.
 */
export function updateTask(id, data) {
  return request(`/tasks/${id}`, { method: 'PUT', body: data });
}

/** PATCH /tasks/{id}/status -> TaskResponse (200). Changes only the status. */
export function changeStatus(id, status) {
  return request(`/tasks/${id}/status`, { method: 'PATCH', body: { status } });
}

/** PATCH /tasks/{id}/priority -> TaskResponse (200). Changes only the priority. */
export function changePriority(id, priority) {
  return request(`/tasks/${id}/priority`, {
    method: 'PATCH',
    body: { priority },
  });
}

/** DELETE /tasks/{id} -> 204 (resolves to undefined; throws if absent). */
export function deleteTask(id) {
  return request(`/tasks/${id}`, { method: 'DELETE' });
}
