# Development Standards

## Purpose

This project is being developed both as an application and as a learning project.

When making significant changes, explain the reasoning behind the implementation.

Prefer simple, readable and maintainable solutions.

Do not introduce unnecessary complexity.

## Backend

- Use Java 21.
- Use Spring Boot.
- Use Maven.
- Use Spring Data JPA.
- Use MySQL.
- Use Controller -> Service -> Repository architecture.
- Controllers must not access repositories directly.
- Avoid business logic inside controllers.
- Use DTOs for API requests and responses.
- Use Jakarta Validation for request validation.
- Use appropriate HTTP status codes.
- Create tests for important business rules.

## Frontend

- Use React.
- Use Vite.
- Use functional components.
- Keep API communication separate from visual components when practical.
- Avoid excessively large components.
- Prefer reusable components when appropriate.

## Development

Before implementing a significant feature:

1. Explain what will be changed.
2. Explain which files will be affected.
3. Explain important architectural decisions.
4. Implement only what is necessary for the current task.

Do not modify unrelated files.

## Learning

When using a concept that may not be obvious to a junior developer, briefly explain how it works.

Do not hide important implementation details behind generated code.