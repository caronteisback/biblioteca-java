# ADR 0002: Package by layer

Date: 2026-10-20 · Status: accepted

## Context

Classes can be grouped by feature (`libro/`, `prestamo/`, each holding its entity, repository, service and controller) or by layer (`entity/`, `repository/`, `service/`, `controller/`). The first layout of the repository was by feature.

## Decision

Group by **layer** under `dev.portafolio.biblioteca`:

```
config/       typed business parameters and the clock
controller/   REST endpoints and the global error handler
dto/          request and response records, alerts, the page record
entity/       @Entity classes, the enum and the equality base class
exception/    ErrorCode, BusinessException and the constraint translator
repository/   Spring Data JPA interfaces
security/     JWT and roles (empty until phase 6)
service/      business rules, transactions and the score, fine and alert calculators
```

Tests mirror the same packages.

## Alternatives

- **By feature:** keeps everything about one concept together, allows `package-private` classes and scales better to many entities. It is less common in tutorials and legacy code.
- **By layer (chosen):** the most widely recognized layout, easy to explain, and familiar to the author from Spring MVC work. With five entities each package stays small.

## Consequences

- Classes that collaborate across layers must be `public`.
- Opening "loans" means visiting several packages; the IDE's search and the naming convention (`Prestamo`, `PrestamoRepository`, `PrestamoService`, `PrestamoController`) make this cheap.
- If the project grows to many unrelated concepts, revisit this decision; moving files is mechanical and the test suite proves the move.
