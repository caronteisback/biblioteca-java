# biblioteca-java

REST API for a library system (books, copies, members and loans), built with **Java 17**, **Spring Boot 4** and **Oracle Database XE 21c**.

> All data in this project is fictional. It is a portfolio project and is not related to any real organization.

## Status

| Part | Scope | State |
|------|-------|-------|
| A | Project skeleton, Oracle XE in Docker, Flyway, health endpoint, Testcontainers | Done |
| B1 | Schema (`V1`), JPA entities and repositories, constraint and query tests | Done |
| B2 | Services with the loan/renew/return rules, REST controllers, error model | Next (rules and diagrams are in `docs/`) |
| C | OpenAPI documentation, integration tests for the loan flow, final README | Planned |

## Documentation

The design comes first and lives in [`docs/`](docs/README.md):

- [Business rules](docs/business-rules.md) and the error catalog
- [Process flows](docs/diagrams/process-flows.md) and [sequence diagrams](docs/diagrams/sequence-diagrams.md)
- [Class diagram](docs/diagrams/class-diagram.md) (JPA entities) and [ER diagram](docs/diagrams/er-diagram.md) (Oracle)
- [Architecture decisions](docs/adr/)

## Architecture

```
controller  ->  service  ->  repository  ->  Oracle XE
   (HTTP)     (business rules,   (Spring Data JPA)
               transactions)
```

- **Packages by layer** under `dev.portafolio.biblioteca`: `entity`, `repository`, and, as they arrive, `service`, `controller`, `dto` and `exception`. See [ADR 0002](docs/adr/0002-package-by-layer.md).
- **Schema ownership:** Flyway migrations (`src/main/resources/db/migration`) are the only way the schema changes. Hibernate runs with `ddl-auto: validate`.
- **Configuration:** one `.env` file feeds both `docker compose` and Spring Boot. No credentials live in code or in committed files.
- **Profiles:** `dev` (default, uses the compose database) and `test` (Testcontainers starts a disposable Oracle XE).

## Requirements

- JDK 17 or newer (the code is compiled with `release 17`)
- Docker Desktop (Compose v2)
- No Maven installation needed: use the included wrapper (`mvnw` / `mvnw.cmd`)
- IntelliJ IDEA: the project uses Lombok. Reload the Maven project and enable *Settings > Build, Execution, Deployment > Compiler > Annotation Processors > Enable annotation processing*. Maven builds need nothing extra

## Run

1. Create your local configuration and set your own passwords:

   ```bash
   cp .env.example .env
   ```

   Edit `.env`. Use letters and digits only to keep the file valid for both Compose and Spring.

2. Start the database and wait until it is healthy (first start takes about a minute):

   ```bash
   docker compose up -d
   docker compose ps
   ```

3. Start the API:

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows PowerShell use `.\mvnw.cmd spring-boot:run`.

4. Check it:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

   Expected: `{"groups":["liveness","readiness"],"status":"UP"}`

Stop the database with `docker compose down`. To wipe all data and re-run initialization, use `docker compose down -v`.

## Tests

```bash
./mvnw test
```

Tests start a throwaway Oracle XE 21c container with Testcontainers (Docker must be running; the first run downloads the image). One container is shared by the whole run, and the tests do not touch the compose database.

- `PrestamoTests`: the date rules of a loan, without a database.
- `EsquemaRestriccionesTests`: the rules the schema enforces on its own (one active loan per copy, renewals 0 to 3, unique email ignoring case, fine consistency). Each rejection is checked by the constraint name Oracle reports.
- `RepositoriosConsultasTests`: the locking and counting queries the services will use.

## Key decisions

| Decision | Chosen | Alternatives considered |
|----------|--------|-------------------------|
| Framework | Spring Boot 4.1 (Java 17 baseline) | Spring Boot 3.5: open-source support ended on 2026-06-30 |
| Persistence | Spring Data JPA | JdbcTemplate: more control, much more boilerplate |
| Boilerplate | Lombok on entities (no `@Data`) | Hand-written getters and builders: more explicit, more code |
| Database | Oracle XE 21c | PostgreSQL: lighter, but not the target engine; H2: hides Oracle-specific behavior |
| Schema management | Flyway (plain SQL) | Hibernate `ddl-auto`: unsafe and unversioned |
| Test database | Testcontainers with the same Oracle image | H2 in Oracle mode: not the real engine |

## Security notes

- The database port is published on `127.0.0.1` only.
- The application connects with a dedicated schema user, never with `SYS`/`SYSTEM`.
- `/actuator/health` hides component details; only `health` and `info` are exposed.
- Error responses do not include messages or stack traces from the framework.

## Troubleshooting

- `ORA-12514` or `ORA-65096`: the JDBC URL must point to the service `XEPDB1`, not `XE`.
- Init variables seem ignored: they apply only when the volume is empty. Run `docker compose down -v` and start again.
- `bad interpreter` in scripts on Windows: files need LF endings; `.gitattributes` enforces this.

## Conventions

Commits follow [Conventional Commits](https://www.conventionalcommits.org/). See [CONTRIBUTING.md](CONTRIBUTING.md).
