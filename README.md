# biblioteca-java

[![CI](https://github.com/caronteisback/biblioteca-java/actions/workflows/ci.yml/badge.svg)](https://github.com/caronteisback/biblioteca-java/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 17](https://img.shields.io/badge/Java-17-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![Oracle XE 21c](https://img.shields.io/badge/Oracle-XE%2021c-red)

REST API for a small library: books, copies, members, loans, renewals, returns and fines. Built with **Java 17**, **Spring Boot 4**, Spring Data JPA and **Oracle Database XE 21c**, with the design documented before the code.

> All data in this project is fictional. It is a portfolio project and is not related to any real organization.

## What it does

- **Catalog:** books with a unique ISBN, physical copies that can be retired, members with a unique email.
- **Loans:** a member can have up to **5 active loans** of 14 days each. Eight checks run in a fixed order and each failure has its own error code.
- **Renewals:** up to 3 per loan, each one adding 14 days to the current due date. An overdue loan cannot be renewed.
- **Returns and fines:** a late return creates a fine of S/ 1 per day, paid whole and only once. There is no payment gateway.
- **Score:** a reliability score calculated from the loan history on every query. It starts at 100, only goes down, and below 50 the member cannot borrow. It is never stored.
- **Alerts:** `DUE_SOON`, `OVERDUE`, `PENDING_FINE` and `LOW_SCORE`, calculated at query time.

The rules are written in [docs/business-rules.md](docs/business-rules.md) and every behavior is tested.

## What makes it interesting

- **The database enforces the rules too.** One active loan per copy is a function-based unique index; renewals 0 to 3, coherent dates and fine consistency are `CHECK` constraints. If application code has a bug, the schema still refuses the bad row.
- **Concurrency is designed and tested, not hoped for.** Row locks are taken in one fixed order (member, copy, loan) with a 5-second cap. Nine tests run real threads against Oracle, and removing the locks makes six of them fail, so they do detect the races. An experiment also showed that one lock I had planned was not needed, and the code and docs say so.
- **Derived data is never stored.** Score, alerts and copy availability are calculated from the history, so they cannot disagree with it.
- **One error shape.** Every failure answers `{ "code": "...", "message": "..." }` with a stable code and the right status (400, 404, 405, 409, 415, 422, 500), and nothing internal (SQL, constraint names, stack traces) reaches the client.
- **Real database in tests.** 300 tests run on Oracle XE in Testcontainers, not on an in-memory substitute that hides engine behavior.
- **Design first.** Business rules, process flows, sequence diagrams, class and ER diagrams, query plans checked with `DBMS_XPLAN`, and architecture decision records live in [`docs/`](docs/README.md).

## Quick start

Requirements: JDK 17 or newer, Docker Desktop (Compose v2). No Maven installation is needed: the repository includes the wrapper.

1. Create your local configuration and set your own passwords (letters and digits only):

   ```bash
   cp .env.example .env
   ```

2. Start the database and wait until it is healthy (the first start takes about a minute):

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

IntelliJ IDEA: the project uses Lombok. Reload the Maven project and enable *Settings > Build, Execution, Deployment > Compiler > Annotation Processors > Enable annotation processing*. Maven builds need nothing extra.

## Try the API

With the API running:

```bash
# create a book (the ISBN may carry hyphens)
curl -i -X POST http://localhost:8080/api/v1/libros -H "Content-Type: application/json" \
  -d '{"isbn":"978-0-306-40615-7","titulo":"El Faro Imaginado","autor":"Autora Ficticia","anioPublicacion":2001}'

# add a copy to book 1 (use the id returned above)
curl -X POST http://localhost:8080/api/v1/libros/1/ejemplares -H "Content-Type: application/json" -d '{"codigo":"EJ-1"}'

# create a member: the answer carries score 100 and no alerts
curl -X POST http://localhost:8080/api/v1/socios -H "Content-Type: application/json" \
  -d '{"nombre":"Rosa Ficticia","email":"rosa@example.org"}'

# lend copy 1 to member 1 (the due date is 14 days from today, set by the server)
curl -X POST http://localhost:8080/api/v1/prestamos -H "Content-Type: application/json" -d '{"socioId":1,"ejemplarId":1}'

# renew loan 1 (up to 3 times), then return it
curl -X POST http://localhost:8080/api/v1/prestamos/1/renovacion
curl -X POST http://localhost:8080/api/v1/prestamos/1/devolucion

# the member carries a score and alerts calculated from the history
curl http://localhost:8080/api/v1/socios/1

# list books whose title or author contains "faro", 5 per page
curl "http://localhost:8080/api/v1/libros?q=faro&size=5"
```

| Resource | Main endpoints under `/api/v1` |
|----------|--------------------------------|
| Books | `POST/GET /libros`, `GET/PUT/DELETE /libros/{id}` |
| Copies | `POST/GET /libros/{id}/ejemplares`, `GET/DELETE /ejemplares/{id}`, `POST /ejemplares/{id}/baja` and `/alta` |
| Members | `POST/GET /socios`, `GET/PUT/DELETE /socios/{id}`, `POST /socios/{id}/desactivar` and `/reactivar` |
| Loans | `POST/GET /prestamos`, `GET /prestamos/{id}`, `POST /prestamos/{id}/renovacion` and `/devolucion` |
| Fines | `GET /multas`, `GET /multas/{id}`, `POST /multas/{id}/pago` |

Statuses, pagination, filters and CORS are in [API conventions](docs/api-conventions.md); the error catalog is in [business rules](docs/business-rules.md#14-error-catalog).

## OpenAPI and Swagger UI

The API describes itself with an [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0) document built from the code: 26 operations, the error codes each one can return, and which fields are always present.

- **Interactive page (Swagger UI):** <http://localhost:8080/swagger-ui.html>, to read and try every operation.
- **The document itself:** `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`.
- **A committed copy:** [`docs/openapi.yaml`](docs/openapi.yaml), readable on GitHub without running anything.

Both endpoints exist only in the `dev` profile (the default on your machine). With `prod` they answer `404`, so a deployed API does not advertise its own surface.

The committed copy cannot drift: a test generates the document and fails if it differs from `docs/openapi.yaml`. After changing an endpoint, refresh the file and commit it:

```bash
./mvnw test -Dtest=OpenApiDocumentTests -Dopenapi.update=true
```

## Run in a container

The image uses JDK 17, runs as a user without privileges and has a health check. It contains no secret: the database comes from the environment, and the `prod` profile (the image default) stops at startup if a variable is missing.

```bash
docker build -t biblioteca-java .

docker run -d --name biblioteca-api --network biblioteca-java_default -p 8080:8080 \
  -e DB_URL=jdbc:oracle:thin:@//oracle:1521/XEPDB1 \
  -e APP_USER=biblioteca \
  -e APP_USER_PASSWORD=<the value in your .env> \
  biblioteca-java
```

The `--network` flag puts the container on the network of `docker compose up -d`, where the database is reachable as `oracle`. To let a browser page call the API from another origin, set `BIBLIOTECA_CORS_ORIGENES_PERMITIDOS` (comma-separated, no wildcard).

## Architecture

```
controller  ->  service  ->  repository  ->  Oracle XE
   (HTTP)     (rules, locks,   (Spring Data JPA)
               transactions)
```

- **Packages by layer** under `dev.portafolio.biblioteca`: `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security` (empty until a later phase) and `service`. See [ADR 0002](docs/adr/0002-package-by-layer.md).
- **Schema ownership:** Flyway migrations in `src/main/resources/db/migration` are the only way the schema changes. Hibernate runs with `ddl-auto: validate`.
- **Configuration:** one `.env` file feeds both `docker compose` and Spring Boot; no credentials live in code or in committed files. Business parameters (term, limits, fine, score) are in `application.yml` and an invalid value stops the startup.
- **Profiles:** `dev` (default, uses the compose database), `test` (Testcontainers) and `prod` (everything from the environment).

## Tests

```bash
./mvnw verify
```

300 tests. They start a throwaway Oracle XE 21c container with Testcontainers (Docker must be running; the first run downloads the image), share one container for the whole run and never touch the compose database.

| Level | What it proves |
|-------|----------------|
| Unit | Date rules of a loan, score, fine and alert formulas, constraint-to-error translation, validation of the business parameters |
| Schema | Every rule the database enforces by itself; each rejection is checked by the constraint name Oracle reports |
| Services (real Oracle) | Each rule at its exact boundary (5th and 6th loan, score 50 and 49, due today and due yesterday), the order of the eight checks, every error code |
| Controllers (mocked services) | The HTTP contract: statuses, JSON, validation, CORS |
| OpenAPI | The document is valid, lists exactly the 26 operations, documents the errors and required fields, matches the committed copy, and is hidden outside `dev` |
| Whole path | HTTP to Oracle with the real error handler, including the full life of a loan |
| Concurrency | Real threads on Oracle: one copy and eight members, six loans for one member, double return, double renewal, double payment, retiring a copy while it is lent, a lock held too long |

Continuous integration runs the same command on every push, and also checks that the Docker image builds.

## Key decisions

| Decision | Chosen | Alternatives considered |
|----------|--------|-------------------------|
| Framework | Spring Boot 4.1 (Java 17 baseline) | Spring Boot 3.5: open-source support ended on 2026-06-30 |
| Persistence | Spring Data JPA | JdbcTemplate: more control, much more boilerplate |
| Boilerplate | Lombok on entities (no `@Data`) | Hand-written getters and builders: more explicit, more code |
| Database | Oracle XE 21c | PostgreSQL: lighter, but not the target engine; H2: hides Oracle-specific behavior |
| Schema management | Flyway (plain SQL) | Hibernate `ddl-auto`: unsafe and unversioned |
| Concurrency | Pessimistic row locks in a fixed order, plus constraints as the last safety net | Optimistic versioning: more retries; counter column: a duplicated datum that can drift |
| Score | Derived from history on every query | A stored column: counts no unreturned loans and can drift |
| Packages | By layer | By feature: see [ADR 0002](docs/adr/0002-package-by-layer.md) |

## Security notes

- No secret in the repository or in the image; `.env` is git-ignored and `.dockerignore` keeps it out of the build.
- The database port is published on `127.0.0.1` only, and the application connects with a dedicated schema user, never with `SYS` or `SYSTEM`.
- Every query uses bind variables; input is validated; errors never expose SQL, constraint names or stack traces.
- CORS allows only the listed origins, never a wildcard (a wildcard stops the startup).
- `/actuator/health` hides component details; only `health` and `info` are exposed.
- The API is open for now. Authentication (JWT) and the roles `LIBRARIAN` and `MEMBER` arrive in a later phase, so do not expose it to a network.

## Documentation

Everything is in [`docs/`](docs/README.md): [business rules](docs/business-rules.md), [process flows](docs/diagrams/process-flows.md), [sequence diagrams](docs/diagrams/sequence-diagrams.md), [class diagram](docs/diagrams/class-diagram.md), [ER diagram](docs/diagrams/er-diagram.md), [query plans](docs/query-plans.md), [API conventions](docs/api-conventions.md) and [architecture decisions](docs/adr/) and the [OpenAPI document](docs/openapi.yaml). See also the [CHANGELOG](CHANGELOG.md).

## Roadmap

- **Next:** move the OpenAPI document to its own repository, `biblioteca-contrato`, as the shared contract for the other backend and the frontend, with a test here that checks the API against it.
- **Later:** authentication with JWT and roles, a complete `docker compose` with every component, and a browser frontend.

## Troubleshooting

- `ORA-12514` or `ORA-65096`: the JDBC URL must point to the service `XEPDB1`, not `XE`.
- Init variables seem ignored: they apply only when the volume is empty. Run `docker compose down -v` and start again.
- `Port 8080 was already in use`: another copy of the API is running (terminal, IDE or container).
- `bad interpreter` in scripts on Windows: files need LF endings; `.gitattributes` enforces this.

## Conventions

Commits follow [Conventional Commits](https://www.conventionalcommits.org/). See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
