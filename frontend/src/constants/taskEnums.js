// Shared source of truth for the Task enum values.
//
// These lists MUST match the backend enums exactly, because the values are sent
// verbatim to the API (e.g. PATCH /tasks/{id}/status expects "IN_PROGRESS"):
//   - TaskStatus:   TODO, IN_PROGRESS, DONE
//   - TaskPriority: LOW, MEDIUM, HIGH
//
// Keeping them here (instead of hard-coding options inside each component) means
// StatusControl, PriorityControl, and TaskForm can all reuse the same lists.
// If the backend ever adds a value, we only change it in one place (DRY).
//
// Each option separates the raw enum `value` (what the backend understands) from
// a human-friendly `label` (what the user reads), so "IN_PROGRESS" can be shown
// as "In Progress" without changing what we send over the wire.

export const TASK_STATUS_OPTIONS = [
  { value: 'TODO', label: 'To Do' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'DONE', label: 'Done' },
];

export const TASK_PRIORITY_OPTIONS = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
];
