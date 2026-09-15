# Requirements Document

## Introduction

DevTrack is a full-stack task management application. In this first version, a user can create, list, edit, and delete tasks, change a task's status, and set a task's priority. Each task has a title, an optional description, a status (defaulting to TODO), and a priority.

The system is composed of two parts:

- A **Backend** REST API built with Java 21, Spring Boot, Maven, Spring Data JPA, and MySQL, following a Controller → Service → Repository architecture.
- A **Frontend** single-page application built with React (Vite, JavaScript, functional components) that communicates with the Backend over HTTP.

Authentication, Docker, and cloud services are explicitly out of scope for this version.

## Glossary

- **DevTrack**: The complete task management application, comprising the Backend and Frontend.
- **Backend**: The Spring Boot REST API that manages tasks and persists them to the database.
- **Frontend**: The React single-page application that provides the user interface and communicates with the Backend.
- **Task**: A unit of work tracked by DevTrack, consisting of a title, an optional description, a status, and a priority.
- **Task_Title**: A required text field naming a Task, between 1 and 150 characters after trimming leading and trailing whitespace.
- **Task_Description**: An optional text field describing a Task, up to 2000 characters.
- **Task_Status**: The current state of a Task. Allowed values: TODO, IN_PROGRESS, DONE.
- **Task_Priority**: The importance level of a Task. Allowed values: LOW, MEDIUM, HIGH.
- **Task_Id**: The unique identifier assigned to a Task by the Backend when the Task is created.
- **Task_Repository**: The Spring Data JPA component responsible for reading and writing Task records in MySQL.
- **Task_Service**: The Backend component that contains task-related business logic and mediates between the Controller and the Task_Repository.
- **Task_Controller**: The Backend component that exposes the REST endpoints for tasks.
- **Request_DTO**: A data transfer object representing the body of an incoming API request.
- **Response_DTO**: A data transfer object representing the body of an outgoing API response.
- **User**: A person who interacts with DevTrack through the Frontend.

## Requirements

### Requirement 1: Create a Task

**User Story:** As a User, I want to create a task, so that I can track a new unit of work.

#### Acceptance Criteria

1. WHEN the Backend receives a create-task request with a valid Task_Title, THE Backend SHALL create a Task and respond with HTTP 201 Created and a Response_DTO containing the assigned Task_Id.
2. WHEN a create-task request omits Task_Status, THE Backend SHALL set the Task_Status to TODO.
3. WHEN a create-task request omits Task_Priority, THE Backend SHALL set the Task_Priority to MEDIUM.
4. WHEN a create-task request omits Task_Description, THE Backend SHALL create the Task with an empty Task_Description.
5. IF a create-task request has a Task_Title that is missing, or that consists of only whitespace characters, or whose length after removing leading and trailing whitespace is 0 characters, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
6. IF a create-task request has a Task_Title whose length after removing leading and trailing whitespace is greater than 150 characters, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
7. IF a create-task request has a Task_Description longer than 2000 characters, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
8. IF a create-task request has a Task_Status that is not one of TODO, IN_PROGRESS, or DONE, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
9. IF a create-task request has a Task_Priority that is not one of LOW, MEDIUM, or HIGH, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
10. IF a create-task request is rejected with HTTP 400 Bad Request for any validation reason, THEN THE Backend SHALL NOT persist any Task and SHALL NOT assign a Task_Id.
11. IF a create-task request has a missing or unparseable request body, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.

### Requirement 2: List All Tasks

**User Story:** As a User, I want to list all tasks, so that I can see the work being tracked.

#### Acceptance Criteria

1. WHEN the Backend receives a list-tasks request, THE Backend SHALL respond with HTTP 200 OK and a Response_DTO collection containing every stored Task ordered by ascending Task_Id.
2. IF the Backend receives a list-tasks request while no Task exists, THEN THE Backend SHALL respond with HTTP 200 OK and an empty Response_DTO collection.
3. WHEN the Backend responds to a list-tasks request, THE Backend SHALL include the Task_Id, Task_Title, Task_Description, Task_Status, and Task_Priority for each Task in the response.

### Requirement 3: Retrieve a Single Task

**User Story:** As a User, I want to retrieve one task by its identifier, so that I can view its full details.

#### Acceptance Criteria

1. WHEN the Backend receives a get-task request for an existing Task_Id, THE Backend SHALL respond with HTTP 200 OK and a Response_DTO containing the Task_Id, Task_Title, Task_Description, Task_Status, and Task_Priority of that Task.
2. IF the Backend receives a get-task request for a Task_Id that does not exist, THEN THE Backend SHALL respond with HTTP 404 Not Found and a Response_DTO describing the error.
3. IF the Backend receives a get-task request whose Task_Id is malformed and cannot be interpreted as a valid identifier, THEN THE Backend SHALL respond with HTTP 400 Bad Request and a Response_DTO describing the error.

### Requirement 4: Edit a Task

**User Story:** As a User, I want to edit a task, so that I can correct or update its details.

#### Acceptance Criteria

1. WHEN the Backend receives an edit-task request for an existing Task_Id with a Task_Title that is between 1 and 150 characters after trimming leading and trailing whitespace, THE Backend SHALL update the Task and respond with HTTP 200 OK and a Response_DTO containing the updated Task.
2. WHEN the Backend updates an existing Task, THE Backend SHALL replace that Task's Task_Title, Task_Description, Task_Status, and Task_Priority with the values supplied in the edit-task request and SHALL persist the replaced values.
3. WHEN an edit-task request omits Task_Description, Task_Status, or Task_Priority, THE Backend SHALL set the omitted Task_Description to empty, the omitted Task_Status to TODO, and the omitted Task_Priority to MEDIUM.
4. IF the Backend receives an edit-task request for a Task_Id that does not exist, THEN THE Backend SHALL respond with HTTP 404 Not Found and a Response_DTO describing the error.
5. IF an edit-task request has a Task_Title that is missing, empty, or contains only whitespace, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
6. IF an edit-task request has a Task_Title longer than 150 characters after trimming leading and trailing whitespace, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
7. IF an edit-task request has a Task_Description longer than 2000 characters, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
8. IF an edit-task request has a Task_Status that is not one of TODO, IN_PROGRESS, or DONE, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.
9. IF an edit-task request has a Task_Priority that is not one of LOW, MEDIUM, or HIGH, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.

### Requirement 5: Change Task Status

**User Story:** As a User, I want to change the status of a task, so that I can reflect its progress.

#### Acceptance Criteria

1. WHEN the Backend receives a change-status request for an existing Task_Id with a Task_Status of TODO, IN_PROGRESS, or DONE, THE Backend SHALL persist the requested Task_Status for that Task and respond with HTTP 200 OK and a Response_DTO containing the Task with its Task_Status set to the requested value.
2. WHEN the Backend applies a change-status request to an existing Task, THE Backend SHALL leave the Task_Title, Task_Description, and Task_Priority of that Task unchanged.
3. IF the Backend receives a change-status request for a Task_Id that does not exist, THEN THE Backend SHALL respond with HTTP 404 Not Found and a Response_DTO describing the error.
4. IF a change-status request has a Task_Status that is missing or not one of TODO, IN_PROGRESS, or DONE, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.

### Requirement 6: Define or Change Task Priority

**User Story:** As a User, I want to define or change the priority of a task, so that I can indicate its importance.

#### Acceptance Criteria

1. WHEN the Backend receives a change-priority request for an existing Task_Id with a Task_Priority of LOW, MEDIUM, or HIGH, THE Backend SHALL update the Task_Priority to the requested value and respond with HTTP 200 OK and a Response_DTO containing the Task with its Task_Id, Task_Title, Task_Description, Task_Status, and the updated Task_Priority.
2. WHEN the Backend updates the Task_Priority of an existing Task, THE Backend SHALL persist the updated Task_Priority so that it is returned on subsequent get-task requests for the same Task_Id.
3. IF the Backend receives a change-priority request for a Task_Id that does not exist, THEN THE Backend SHALL respond with HTTP 404 Not Found and a Response_DTO describing the error.
4. IF a change-priority request has a Task_Priority that is missing or not one of LOW, MEDIUM, or HIGH, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request, leave the stored Task_Priority unchanged, and respond with a Response_DTO describing the validation error.

### Requirement 7: Delete a Task

**User Story:** As a User, I want to delete a task, so that I can remove work that is no longer relevant.

#### Acceptance Criteria

1. WHEN the Backend receives a delete-task request for an existing Task_Id, THE Backend SHALL remove the Task, respond with HTTP 204 No Content, and thereafter respond to any get-task request for that Task_Id with HTTP 404 Not Found.
2. IF the Backend receives a delete-task request for a well-formed Task_Id that is not currently stored, whether it never existed or was already deleted, THEN THE Backend SHALL respond with HTTP 404 Not Found and a Response_DTO describing the error.
3. IF the Backend receives a delete-task request whose Task_Id is missing or not a well-formed identifier, THEN THE Backend SHALL reject the request with HTTP 400 Bad Request and a Response_DTO describing the validation error.

### Requirement 8: Backend Architecture and Data Handling

**User Story:** As a developer, I want the Backend to follow a layered architecture with validated DTOs, so that the codebase stays simple, readable, and maintainable.

#### Acceptance Criteria

1. THE Task_Controller SHALL delegate all task operations to the Task_Service.
2. THE Task_Controller SHALL access task data only through the Task_Service and SHALL NOT call the Task_Repository directly.
3. THE Task_Service SHALL contain the task-related business logic and SHALL access task data through the Task_Repository.
4. THE Backend SHALL accept task input through a Request_DTO and return task data through a Response_DTO.
5. WHEN the Backend receives a task request, THE Backend SHALL apply Jakarta Validation to the Request_DTO before the Task_Service processes it, and SHALL NOT invoke the Task_Service if validation fails.
6. THE Task_Repository SHALL persist Task records in MySQL using Spring Data JPA.
7. IF a Request_DTO fails Jakarta Validation, THEN THE Backend SHALL respond with HTTP 400 Bad Request and a Response_DTO that identifies each field that failed validation together with the reason for each failure.
8. IF the Backend receives a request whose body is malformed and cannot be parsed, THEN THE Backend SHALL respond with HTTP 400 Bad Request and a Response_DTO containing an error indication that the request body could not be parsed, without exposing internal implementation details.
9. WHEN a single Request_DTO fails Jakarta Validation on multiple fields, THE Backend SHALL include all failed fields in one Response_DTO rather than reporting only the first failure.
10. IF the Task_Repository cannot persist or retrieve a Task record because MySQL is unavailable, THEN THE Backend SHALL respond with HTTP 500 Internal Server Error and a Response_DTO containing an error indication that the operation could not be completed, without exposing internal implementation details.
11. IF a task operation fails after validation, THEN THE Backend SHALL NOT persist any partial changes for that operation, leaving existing Task records unchanged.

### Requirement 9: Frontend Task Management Interface

**User Story:** As a User, I want a simple web interface, so that I can manage tasks without using the API directly.

#### Acceptance Criteria

1. WHEN the Frontend loads, THE Frontend SHALL request the task list from the Backend and display each returned Task with its Task_Title, Task_Status, and Task_Priority.
2. WHILE the Backend returns an empty task list, THE Frontend SHALL display an indication that no Task exists and SHALL display no Task entries.
3. IF the request for the task list fails or the Backend responds with an error status, THEN THE Frontend SHALL display a message describing the failure to the User and SHALL display no Task entries.
4. WHEN a User submits the create-task form with a Task_Title between 1 and 150 characters after trimming leading and trailing whitespace, THE Frontend SHALL send a create-task request to the Backend and display the resulting Task in the task list.
5. IF a User submits the create-task form with a Task_Title that is empty or contains only whitespace after trimming, THEN THE Frontend SHALL reject the submission, display a message describing the validation error to the User, and SHALL NOT send a create-task request to the Backend.
6. WHEN a User submits an edit for an existing Task, THE Frontend SHALL send an edit-task request to the Backend and display the updated Task.
7. WHEN a User changes the Task_Status of a Task through the interface, THE Frontend SHALL send a change-status request to the Backend and display the updated Task_Status.
8. WHEN a User changes the Task_Priority of a Task through the interface, THE Frontend SHALL send a change-priority request to the Backend and display the updated Task_Priority.
9. WHEN a User confirms deletion of a Task, THE Frontend SHALL send a delete-task request to the Backend and remove the Task from the displayed task list.
10. IF the Backend responds to a Frontend request with an error status, THEN THE Frontend SHALL display a message describing the error to the User and SHALL retain the previously displayed task list unchanged.
11. THE Frontend SHALL keep Backend communication in a module separate from the visual components where practical.
12. THE Frontend SHALL use functional React components.

### Requirement 10: Version Scope Boundaries

**User Story:** As a developer, I want the first version scope kept tight, so that the learning project stays focused and maintainable.

#### Acceptance Criteria

1. THE DevTrack Backend SHALL accept and process every task request without requiring any credential, token, or session, so that no request is rejected due to a missing authentication mechanism.
2. WHEN the Backend receives a task request that includes a credential, token, or session, THE Backend SHALL process the request identically to one without such data and SHALL NOT require or validate it.
3. THE DevTrack Backend and Frontend SHALL each be startable and runnable directly on the host operating system without a container runtime.
4. THE DevTrack Backend, Frontend, and MySQL database SHALL operate using only components running on the local host and SHALL require no externally hosted service to function.
