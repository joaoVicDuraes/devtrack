# Implementation Plan: DevTrack Task Management

## Overview

This plan builds DevTrack in two parts. The Backend (Java 21, Spring Boot, Maven, Spring Data JPA, MySQL) is built bottom-up: enums and entity first, then the repository, DTOs, service (where the business rules live), and finally the controller and global exception handler that expose everything over HTTP. Property-based tests (jqwik, Properties 1–12) sit next to the service logic they verify, with `@WebMvcTest` covering the HTTP contract and `@DataJpaTest` covering persistence wiring.

The Frontend (React + Vite, JavaScript, functional components) is built after the API contract is stable: the `taskApi.js` network module first, then `App` state orchestration, then the visual components, with React Testing Library and mocked-`fetch` tests alongside.

Each task is incremental and builds on the previous ones, ending with wiring so there is no orphaned code. Test sub-tasks marked with `*` are optional (skippable for a faster MVP) but are still scheduled in the dependency graph.

## Tasks

### Backend

- [x] 1. Scaffold the Spring Boot Maven project
  - Create a Maven project (`pom.xml`) targeting Java 21 with Spring Boot parent
  - Add dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `mysql-connector-j`, and `net.jqwik:jqwik` (test scope), plus `spring-boot-starter-test`
  - Add the main `@SpringBootApplication` class
  - Configure `src/main/resources/application.properties` for a local MySQL datasource (URL, username, password, `spring.jpa.hibernate.ddl-auto`, MySQL dialect)
  - _Requirements: 8.6, 10.3, 10.4_

- [x] 2. Define enums and the Task entity
  - [x] 2.1 Create TaskStatus and TaskPriority enums
    - `TaskStatus { TODO, IN_PROGRESS, DONE }` and `TaskPriority { LOW, MEDIUM, HIGH }`
    - _Requirements: 1.2, 1.3, 1.8, 1.9_

  - [x] 2.2 Create the Task JPA entity
    - `@Entity` mapped to table `tasks`; `id` with `@GeneratedValue(IDENTITY)`
    - `title` (`nullable=false`, `length=150`), `description` (`nullable=false`, `length=2000`, default `""`)
    - `status` and `priority` with `@Enumerated(EnumType.STRING)`, `length=20`, defaults `TODO` / `MEDIUM`
    - Getters/setters (or builder)
    - _Requirements: 1.2, 1.3, 1.4_

- [x] 3. Create the TaskRepository
  - Define `TaskRepository extends JpaRepository<Task, Long>`
  - Add a method returning all tasks ordered by ascending id (e.g. `findAllByOrderByIdAsc()`)
  - _Requirements: 8.6, 2.1_

- [x] 4. Define request/response DTOs as records with validation
  - [x] 4.1 Create request DTOs
    - `CreateTaskRequest` and `UpdateTaskRequest` records: `title` (`@NotBlank`, `@Size(max=150)`), `description` (`@Size(max=2000)`), nullable `status`, nullable `priority`
    - `ChangeStatusRequest` record: `status` (`@NotNull`)
    - `ChangePriorityRequest` record: `priority` (`@NotNull`)
    - _Requirements: 8.4, 1.5, 1.6, 1.7, 4.5, 4.6, 4.7, 5.4, 6.4_

  - [x] 4.2 Create response DTOs
    - `TaskResponse` record (id, title, description, status, priority)
    - `ErrorResponse` record (status, message, `List<FieldError>`) with nested `FieldError(field, message)` record
    - _Requirements: 8.4, 2.3, 3.1, 8.7_

- [x] 5. Implement the TaskService (business rules)
  - [x] 5.1 Create the TaskService interface and TaskNotFoundException
    - Interface methods: `create`, `listAll`, `getById`, `update`, `changeStatus`, `changePriority`, `delete`
    - Custom `TaskNotFoundException` for missing ids
    - _Requirements: 8.1, 8.3, 8.4_

  - [x] 5.2 Implement entity <-> DTO mapping
    - Map `Task` entity to `TaskResponse`; apply request values to the entity
    - _Requirements: 8.4, 2.3, 3.1, 11 (via 2.3/3.1 field completeness)_

  - [x] 5.3 Implement create and full-edit logic with defaulting and title trimming
    - `@Transactional`; trim title and validate trimmed length is 1–150; reject blank/over-length as a validation error
    - Default omitted status→TODO, priority→MEDIUM, description→`""`
    - Full edit replaces all four fields (applying the same defaulting); throw `TaskNotFoundException` if id missing
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 8.11_

  - [x] 5.4 Implement listAll, getById, changeStatus, changePriority, and delete
    - `listAll` returns ascending-id order; `getById` throws if missing
    - `changeStatus` updates only status; `changePriority` updates only priority; both preserve other fields and throw if missing
    - `delete` removes the task and throws `TaskNotFoundException` if missing; all write methods `@Transactional`
    - _Requirements: 2.1, 3.1, 3.2, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 7.1, 7.2_

  - [x] 5.5 Write property test: write-then-read round trip
    - **Property 1: Write-then-read round trip preserves values** (jqwik, tries >= 100)
    - **Validates: Requirements 1.1, 3.1, 4.1, 4.2**

  - [x] 5.6 Write property test: omitted optional fields take defaults
    - **Property 2: Omitted optional fields take their defaults** (jqwik, tries >= 100)
    - **Validates: Requirements 1.2, 1.3, 1.4, 4.3**

  - [x] 5.7 Write property test: title non-blank and within 1–150 after trimming
    - **Property 3: Title must be non-blank and within 1–150 characters after trimming** (jqwik, tries >= 100)
    - **Validates: Requirements 1.5, 1.6, 4.5, 4.6**

  - [x] 5.8 Write property test: description length bound
    - **Property 4: Description must not exceed 2000 characters** (jqwik, tries >= 100)
    - **Validates: Requirements 1.7, 4.7**

  - [x] 5.9 Write property test: rejected write does not change stored state
    - **Property 6: A rejected or invalid write does not change stored state** (jqwik, tries >= 100)
    - **Validates: Requirements 1.10, 5.4, 6.4, 8.11**

  - [x] 5.10 Write property test: targeted updates change only their target field
    - **Property 7: Targeted updates change only their target field** (jqwik, tries >= 100)
    - **Validates: Requirements 5.1, 5.2, 6.1, 6.2**

  - [x] 5.11 Write property test: operations on absent id return not-found
    - **Property 8: Operations on an absent id return 404** (jqwik, tries >= 100)
    - **Validates: Requirements 3.2, 4.4, 5.3, 6.3, 7.2**

  - [x] 5.12 Write property test: delete makes a task absent
    - **Property 9: Delete makes a task absent** (jqwik, tries >= 100)
    - **Validates: Requirements 7.1**

  - [x] 5.13 Write property test: listing returns all tasks in ascending id order
    - **Property 10: Listing returns all tasks ordered by ascending id** (jqwik, tries >= 100)
    - **Validates: Requirements 2.1, 2.2**

  - [x] 5.14 Write property test: every task view includes all five fields
    - **Property 11: Every task view includes all five fields** (jqwik, tries >= 100)
    - **Validates: Requirements 2.3, 3.1**

  - [x] 5.15 Write unit tests for defaulting, trimming, and not-found
    - Concrete examples for defaulting/trimming; `TaskNotFoundException` thrown for missing ids; entity→`TaskResponse` mapping
    - _Requirements: 1.2, 1.3, 1.4, 1.6, 3.2_

- [x] 6. Checkpoint - Ensure service tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement the TaskController (REST endpoints)
  - [x] 7.1 Create TaskController with all endpoints
    - `POST /tasks` (201) with `@Valid CreateTaskRequest`; `GET /tasks` (200); `GET /tasks/{id}` (200)
    - `PUT /tasks/{id}` (200) with `@Valid UpdateTaskRequest`; `PATCH /tasks/{id}/status` (200) with `@Valid ChangeStatusRequest`; `PATCH /tasks/{id}/priority` (200) with `@Valid ChangePriorityRequest`
    - `DELETE /tasks/{id}` (204); delegate every operation to `TaskService`; never touch the repository
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 8.2, 8.5_

  - [x] 7.2 Write @WebMvcTest tests for the HTTP contract
    - Correct status codes per endpoint (201/200/204); validation failure returns 400 and the mocked service is never called (8.5); malformed/missing body → 400; malformed path id `/tasks/abc` → 400; invalid enum in body → 400 (spot-check Property 5); `Authorization` header behaves identically to no header
    - _Requirements: 1.11, 3.3, 5.4, 6.4, 7.3, 8.5, 10.1, 10.2_

- [ ] 8. Implement the GlobalExceptionHandler
  - [~] 8.1 Create @RestControllerAdvice mapping exceptions to ErrorResponse
    - `MethodArgumentNotValidException` → 400 listing every failed field + reason
    - `HttpMessageNotReadableException` (malformed body / invalid enum) → 400 generic parse message
    - `MethodArgumentTypeMismatchException` (malformed path id) → 400 generic invalid-identifier message
    - `TaskNotFoundException` → 404; `DataAccessException` → 500 generic message; expose no stack traces or internal class names
    - _Requirements: 8.7, 8.8, 8.9, 8.10, 1.11, 3.2, 3.3, 4.4, 5.3, 6.3, 7.2, 7.3_

  - [ ]* 8.2 Write property test: a validation error reports every failed field
    - **Property 12: A validation error reports every failed field** (jqwik, tries >= 100)
    - **Validates: Requirements 8.7, 8.9**

  - [ ]* 8.3 Write @WebMvcTest tests for error mapping
    - Multi-field validation error lists all fields (complements Property 12); persistence failure (mock service throws `DataAccessException`) → 500 generic body; invalid enum → 400
    - _Requirements: 8.7, 8.9, 8.10, 5.4, 6.4_

- [ ] 9. Wire persistence end to end
  - [ ]* 9.1 Write @DataJpaTest integration test for persistence and ordering
    - Confirm `TaskRepository` persists and reads back a task; listing returns strictly ascending-id order with representative cases
    - _Requirements: 8.6, 2.1_

- [~] 10. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

### Frontend

- [~] 11. Scaffold the Vite React project
  - Create a Vite React (JavaScript) project with `package.json`, entry `main.jsx`, and `index.html`
  - Add a testing setup (Vitest + React Testing Library + jsdom) for component and module tests
  - _Requirements: 9.12, 10.3, 10.4_

- [ ] 12. Implement the taskApi.js network module
  - [~] 12.1 Create src/api/taskApi.js with all HTTP calls
    - Functions: `listTasks`, `getTask`, `createTask`, `updateTask`, `changeStatus`, `changePriority`, `deleteTask` using `fetch`
    - Normalize non-2xx responses into thrown errors (parse the `ErrorResponse` body when present)
    - _Requirements: 9.11_

  - [ ]* 12.2 Write taskApi.js module tests with fetch mocked
    - Each function calls the expected URL/method; non-2xx responses become thrown errors
    - _Requirements: 9.11_

- [ ] 13. Implement reusable enum controls
  - [~] 13.1 Create StatusControl and PriorityControl dropdown components
    - Small functional components rendering the enum options; report selection via a callback
    - _Requirements: 9.7, 9.8, 9.12_

- [ ] 14. Implement TaskForm, TaskItem, and TaskList
  - [~] 14.1 Create TaskForm with client-side title trim/validation
    - Create/edit form; trim the title and block submission with an inline message if empty/whitespace, without calling the API
    - _Requirements: 9.4, 9.5, 9.6, 9.12_

  - [~] 14.2 Create TaskItem row with status/priority/delete actions
    - Render title, status, priority; embed `StatusControl`/`PriorityControl`; expose delete with confirmation
    - _Requirements: 9.1, 9.7, 9.8, 9.9, 9.12_

  - [~] 14.3 Create TaskList with list and empty state
    - Render a `TaskItem` per task; show "No tasks yet" and no rows when the list is empty
    - _Requirements: 9.1, 9.2, 9.12_

- [ ] 15. Implement App.jsx and wire the frontend together
  - [~] 15.1 Create App.jsx orchestrating state and mutations
    - Hold task list, `loading`, and `error` state; call `taskApi` on load and for each mutation
    - Handle loading, empty, and error UI states; on the list request failing show a failure message and no rows; on a mutation error show the message but keep the prior list unchanged
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.6, 9.7, 9.8, 9.9, 9.10_

  - [ ]* 15.2 Write React Testing Library component tests
    - Empty state renders the "no tasks" message and no rows; list error renders the failure message; a failed mutation keeps the prior list; `TaskForm` blocks a blank/whitespace title with an inline message without calling the API
    - _Requirements: 9.2, 9.3, 9.5, 9.10_

- [~] 16. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; they remain scheduled in the dependency graph.
- Each task references specific requirements (and, for tests, specific correctness properties) for traceability.
- Property-based tests (jqwik, minimum 100 iterations) validate the universal correctness rules; unit, `@WebMvcTest`, and `@DataJpaTest` tests cover concrete scenarios, HTTP wiring, and persistence.
- Frontend behavior is verified with React Testing Library and mocked-`fetch` tests; UI rendering is not a fit for property-based testing.
- Requirements 10.3 and 10.4 (host-runnable, local-only) are satisfied by project setup rather than automated tests.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["3", "4.1", "4.2"] },
    { "id": 3, "tasks": ["5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3", "5.4"] },
    { "id": 5, "tasks": ["5.5", "5.6", "5.7", "5.8", "5.9", "5.10", "5.11", "5.12", "5.13", "5.14", "5.15", "9.1"] },
    { "id": 6, "tasks": ["7.1", "8.1"] },
    { "id": 7, "tasks": ["7.2", "8.2", "8.3", "11"] },
    { "id": 8, "tasks": ["12.1", "13.1"] },
    { "id": 9, "tasks": ["12.2", "14.1", "14.2", "14.3"] },
    { "id": 10, "tasks": ["15.1"] },
    { "id": 11, "tasks": ["15.2"] }
  ]
}
```
