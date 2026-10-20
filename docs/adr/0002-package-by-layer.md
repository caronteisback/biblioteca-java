# ADR 0002: Package by layer

Date: 2026-10-20 · Status: accepted

## Context

Classes can be grouped by feature (`libro/`, `prestamo/`, each holding its entity, repository, service and controller) or by layer (`entity/`, `repository/`, `service/`, `controller/`). The first layout of the repository was by feature.

## Decision

Group by **layer** under `dev.portafolio.biblioteca`:

```
entity/       @Entity classes, the enum and the equality base class
repository/   Spring Data JPA interfaces
service/      business rules and transactions (next)
controller/   REST endpoints and error handling (next)
dto/          request and response records (next)
exception/    business exceptions (next)
```

Tests mirror the same packages.

## Alternatives

- **By feature:** keeps everything about one concept together, allows `package-private` classes and scales better to many entities. It is less common in tutorials and legacy code.
- **By layer (chosen):** the most widely recognized layout, easy to explain, and familiar to the author from Spring MVC work. With five entities each package stays small.

## Consequences

- Classes that collaborate across layers must be `public`.
- Opening "loans" means visiting several packages; the IDE's search and the naming convention (`Prestamo`, `PrestamoRepository`, `PrestamoService`, `PrestamoController`) make this cheap.
- If the project grows to many unrelated concepts, revisit this decision; moving files is mechanical and the test suite proves the move.
