import TaskItem from './TaskItem.jsx';

// TaskList: renders the collection of tasks, or an empty-state message.
// (Requirements 9.1 list each task, 9.2 empty state, 9.12 functional component.)
//
// Like StatusControl/PriorityControl, this component is deliberately "dumb":
// it does NOT talk to the API. It receives the already-loaded `tasks` array and
// the mutation callbacks from its parent (App), and simply renders one TaskItem
// per task, forwarding those callbacks down. Keeping fetch/HTTP logic out of the
// visual components is what Requirement 9.11 asks for.
//
// Props:
//   - tasks:            array of task objects ({ id, title, description, status, priority })
//   - onChangeStatus:   callback (id, status)   -> invoked when a row changes status
//   - onChangePriority: callback (id, priority) -> invoked when a row changes priority
//   - onDelete:         callback (id)           -> invoked when a row is deleted
//   - onEdit:           optional callback (task) -> invoked when a row is edited.
//                       Forwarded straight to TaskItem, which only renders its
//                       Edit button when this handler is provided.
function TaskList({ tasks = [], onChangeStatus, onChangePriority, onDelete, onEdit }) {
  // Empty state (Requirement 9.2): when there are no tasks we show a short
  // message and render NO task rows. We check length here rather than mapping an
  // empty array so the intent is explicit and there is nothing to accidentally
  // render below.
  if (tasks.length === 0) {
    return <p className="task-list-empty">No tasks yet</p>;
  }

  // Otherwise render one TaskItem per task (Requirement 9.1).
  //
  // React uses the `key` prop to track list items efficiently across re-renders.
  // We use the backend-assigned task.id because it is stable and unique, which
  // avoids subtle bugs that happen when using the array index as a key.
  //
  // We forward the mutation callbacks straight through to each row. TaskItem
  // owns the actual UI (dropdowns, delete button) and calls these back with the
  // task's id; App decides what to do (e.g. call the API).
  return (
    <ul className="task-list">
      {tasks.map((task) => (
        <li key={task.id}>
          <TaskItem
            task={task}
            onChangeStatus={onChangeStatus}
            onChangePriority={onChangePriority}
            onDelete={onDelete}
            onEdit={onEdit}
          />
        </li>
      ))}
    </ul>
  );
}

export default TaskList;
