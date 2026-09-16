import { TASK_PRIORITY_OPTIONS } from '../constants/taskEnums.js';

// PriorityControl: a small, reusable dropdown for choosing a Task_Priority.
// (Requirements 9.8 select a priority, 9.12 functional component.)
//
// Like StatusControl, this component only renders the enum options and reports
// the user's choice through `onChange`. It performs no API calls and keeps no
// internal state, so the same control can be reused by TaskItem and TaskForm
// without duplicating the priority option list (see src/constants/taskEnums.js).
//
// Controlled component: the parent supplies the current `value` and reacts to
// selection changes via `onChange`.
//
// Props:
//   - value:    the currently selected priority (e.g. "MEDIUM")
//   - onChange: callback invoked with the newly selected priority string
//   - id:       optional DOM id, useful for associating an external <label>
//   - disabled: optional flag to disable the dropdown (e.g. while a request runs)
function PriorityControl({ value, onChange, id, disabled = false }) {
  const handleChange = (event) => {
    onChange(event.target.value);
  };

  return (
    <select
      id={id}
      className="control"
      aria-label="Task priority"
      value={value}
      onChange={handleChange}
      disabled={disabled}
    >
      {TASK_PRIORITY_OPTIONS.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export default PriorityControl;
