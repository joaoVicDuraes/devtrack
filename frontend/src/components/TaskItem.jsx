import StatusControl from './StatusControl.jsx';
import PriorityControl from './PriorityControl.jsx';
import {
  TASK_STATUS_OPTIONS,
  TASK_PRIORITY_OPTIONS,
} from '../constants/taskEnums.js';

// Small helper: turn a raw enum value (e.g. "IN_PROGRESS") into its friendly
// label (e.g. "In Progress") by looking it up in the shared option lists. We
// reuse those same lists the dropdowns use, so labels never drift out of sync.
// If a value is somehow unknown, we fall back to showing the raw value rather
// than blank, so nothing silently disappears.
function labelFor(options, value) {
  const match = options.find((option) => option.value === value);
  return match ? match.label : value;
}

// TaskItem: renders a single task as one row.
// (Requirements 9.1 display tasks, 9.7 change status, 9.8 change priority,
//  9.9 delete with confirmation, 9.12 functional component.)
//
// This component is "presentational": it renders what it is given and reports
// user intent back up through callbacks. It never talks to the API itself, so
// API communication stays out of the visual components (Requirement 9.11). The
// parent (App) owns the task data and performs the actual network calls when a
// callback fires.
//
// It reuses StatusControl and PriorityControl (rather than re-building the
// dropdowns) so the enum option lists live in exactly one place.
//
// Props:
//   - task:            { id, title, description, status, priority }
//   - onChangeStatus:  called with (id, newStatus) when the user picks a status
//   - onChangePriority:called with (id, newPriority) when the user picks a priority
//   - onDelete:        called with (id) AFTER the user confirms the delete
//   - onEdit:          optional; called with (task) when the user clicks Edit
function TaskItem({ task, onChangeStatus, onChangePriority, onDelete, onEdit }) {
  // The controls hand us just the raw enum value (e.g. "IN_PROGRESS"). We attach
  // this task's id so the parent knows WHICH task changed, then call up.
  const handleStatusChange = (newStatus) => {
    onChangeStatus(task.id, newStatus);
  };

  const handlePriorityChange = (newPriority) => {
    onChangePriority(task.id, newPriority);
  };

  // Deleting is destructive, so we ask for confirmation first (Requirement 9.9).
  // window.confirm() shows a native OK/Cancel dialog and returns true only when
  // the user confirms. We guard the callback so onDelete fires ONLY on confirm;
  // if the user cancels, nothing happens.
  const handleDelete = () => {
    const confirmed = window.confirm(
      `Delete task "${task.title}"? This cannot be undone.`
    );
    if (confirmed) {
      onDelete(task.id);
    }
  };

  // Root element is a <div>, NOT a <li>. The TaskList component owns the list
  // structure and already wraps each TaskItem in its own <li>. If this component
  // also rendered an <li>, we would produce invalid nested markup (<li><li>...).
  // Keeping the row as a plain <div> avoids that and keeps TaskItem reusable even
  // outside of a <ul>.
  return (
    <div className="task-item">
      {/* Left group: the title plus two colored badges summarizing the current
          status/priority at a glance. The badge className is keyed to the enum
          value (e.g. badge--status-IN_PROGRESS) so the CSS can color each one.
          The dropdowns to the right still own changing the value. */}
      <div className="task-item__main">
        <span className="task-item__title">{task.title}</span>
        <span className={`badge badge--status-${task.status}`}>
          {labelFor(TASK_STATUS_OPTIONS, task.status)}
        </span>
        <span className={`badge badge--priority-${task.priority}`}>
          {labelFor(TASK_PRIORITY_OPTIONS, task.priority)}
        </span>
      </div>

      {/* Right group: the interactive controls and action buttons. Wrapping
          them in one flex container lets us push them to the right of the row
          and keep them aligned. */}
      <div className="task-item__actions">
        <StatusControl value={task.status} onChange={handleStatusChange} />
        <PriorityControl value={task.priority} onChange={handlePriorityChange} />

        {/* onEdit is optional: only render the Edit button when a handler is given. */}
        {onEdit && (
          <button
            type="button"
            className="btn btn--secondary"
            onClick={() => onEdit(task)}
          >
            Edit
          </button>
        )}

        <button
          type="button"
          className="btn btn--danger"
          onClick={handleDelete}
        >
          Delete
        </button>
      </div>
    </div>
  );
}

export default TaskItem;
