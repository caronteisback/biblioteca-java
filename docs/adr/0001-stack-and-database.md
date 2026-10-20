# ADR 0001: Spring Boot 4, JPA and Oracle XE

Date: 2026-10-19 · Status: accepted

## Context

Portfolio API for a library domain. The author works with Oracle in their day job, so the project should use the same engine, and it should use a supported, current framework.

## Decision

- Spring Boot 4.1.x on Java 17 (the Boot 4 baseline).
- Spring Data JPA (Hibernate) for persistence.
- Oracle XE 21c in Docker for development and Testcontainers for tests.
- Flyway with plain SQL migrations to own the schema.

## Alternatives

- **Spring Boot 3.5:** widely requested in job posts, but open-source support ended on 2026-06-30.
- **JdbcTemplate:** explicit SQL, more boilerplate. May be used later for specific queries.
- **PostgreSQL:** lighter and popular, but it is not the engine the author wants to demonstrate. A second profile could be added later with separate migrations per engine.
- **H2:** fast, but its Oracle mode hides real differences (for example, empty string vs NULL).

## Consequences

- Tests need Docker and download a large image on first run.
- Business rules that must hold under concurrency (one active loan per copy) are enforced by a database constraint, not only by service code.
