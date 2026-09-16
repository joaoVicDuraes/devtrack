import { TASK_STATUS_OPTIONS } from '../constants/taskEnums.js';

// StatusControl: a small, reusable dropdown for choosing a Task_Status.
// (Requirements 9.7 select a status, 9.12 functional component.)
//
// This component is deliberately "dumb": it only renders the enum options and
// reports the user's choice back through the `onChange` callback. It does NOT
// call the API or hold its own state. That keeps API communication out of the
// visual components (Requirement 9.11) and lets both TaskItem and TaskForm reuse
// this same control without duplicating the option list.
//
// This is a "controlled component" pattern: the parent owns the current value
// and passes it in via `value`; when the user picks a new option, we hand the
// selected value up via `onChange` and let the parent decide what to do next.
//
// Props:
//   - value:    the currently selected status (e.g. "TODO")
//   - onChange: callback invoked with the newly selected status string
//   - id:       optional DOM id, useful for associating an external <label>
//   - disabled: optional flag to disable the dropdown (e.g. while a request runs)
function StatusControl({ value, onChange, id, disabled = false }) {
  // The native <select> gives us the raw value on its change event. We forward
  // just that value to the parent so callers don't have to dig into the event.
  const handleChange = (event) => {
    onChange(event.target.value);
  };

  return (
    <select
      id={id}
      className="control"
      // aria-label gives the control an accessible name even when there is no
      // visible <label> next to it (e.g. inside a compact task row).
      aria-label="Task status"
      value={value}
      onChange={handleChange}
      disabled={disabled}
    >
      {TASK_STATUS_OPTIONS.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export default StatusControl;
