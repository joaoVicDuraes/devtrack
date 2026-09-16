// TaskForm.test.jsx
//
// Component tests for the create/edit form (src/components/TaskForm.jsx).
// (Requirement 9.5: block a blank/whitespace-only title on the client with an
//  inline message, WITHOUT calling onSubmit / firing any API request.)
//
// TaskForm is a "dumb" presentational component: it never imports taskApi and
// never calls fetch. Its only outward contact is the `onSubmit` callback the
// parent passes in. That makes it easy to test in isolation - we hand it a mock
// onSubmit and simply assert whether (and with what) it was called.
//
// We use React Testing Library, which encourages asserting on what the USER
// sees (labels, text, alerts) rather than on implementation details. We drive
// the form with `fireEvent` (from @testing-library/react) instead of
// user-event, because user-event is not a dependency of this project.

import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import TaskForm from './TaskForm.jsx';

// Unmount anything rendered between tests so each test starts from a clean DOM.
// (vite.config.js does not enable Vitest's automatic cleanup, so we do it here.)
afterEach(() => {
  cleanup();
});

describe('TaskForm client-side title validation (Requirement 9.5)', () => {
  it('blocks an empty title: shows the inline message and does NOT call onSubmit', () => {
    // A mock we can assert against. If TaskForm wrongly submitted, this would
    // record the call - and there is no taskApi involved at all, so a blocked
    // submission means zero API contact by construction.
    const onSubmit = vi.fn();
    render(<TaskForm onSubmit={onSubmit} />);

    // The title starts empty (create mode). Submitting the form should trip the
    // client-side guard. We submit by clicking the button, which fires the
    // form's onSubmit handler.
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    // The inline validation message is announced via role="alert".
    expect(screen.getByRole('alert')).toHaveTextContent('Title is required.');
    // The parent callback must NOT have run, so no request is ever made.
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('blocks a whitespace-only title the same way (trim makes "   " empty)', () => {
    const onSubmit = vi.fn();
    render(<TaskForm onSubmit={onSubmit} />);

    // Type only spaces. TaskForm trims the title before validating, so this is
    // treated exactly like an empty title.
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: '   ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Title is required.');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits a valid title: calls onSubmit once with the trimmed value and no error shows', () => {
    const onSubmit = vi.fn();
    render(<TaskForm onSubmit={onSubmit} />);

    // A title padded with surrounding whitespace. The form should submit the
    // TRIMMED value, matching what it validated.
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: '  Write tests  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Called exactly once with the cleaned data. Description defaults to "" and
    // the enum defaults are TODO / MEDIUM (matching the backend defaults).
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Write tests',
      description: '',
      status: 'TODO',
      priority: 'MEDIUM',
    });
    // With a valid submission there should be no inline error alert.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
