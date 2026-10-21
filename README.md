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
- **Real database in tests.** 399 tests run on Oracle XE in Testcontainers, not on an in-memory substitute that hides engine behavior.
- **Design first.** Business rules, process flows, sequence diagrams, class and ER diagrams, query plans checked with `DBMS_XPLAN`, and architecture decision records live in [`docs/`](docs/README.md).

## Quick start

Requirements: JDK 17 or newer, Docker Desktop (Compose v2). No Maven installation is needed: the repository includes the wrapper.

1. Create your local configuration and set your own values: the database passwords (letters and digits only), `JWT_SECRET` (a random value of at least 32 characters, for example `openssl rand -base64 48`) and, to be able to sign in, `BIBLIOTECA_ADMIN_EMAIL` and `BIBLIOTECA_ADMIN_PASSWORD` (at least 12 characters), which create the first librarian on the first start:

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

With the API running. Every operation but the sign-in needs a token, so sign in first (with the librarian you set in `.env`):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"<BIBLIOTECA_ADMIN_EMAIL>","password":"<BIBLIOTECA_ADMIN_PASSWORD>"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
AUTH="Authorization: Bearer $TOKEN"
```

Then add `-H "$AUTH"` to each call:

```bash
# create a book (the ISBN may carry hyphens)
curl -i -X POST http://localhost:8080/api/v1/libros -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"isbn":"978-0-306-40615-7","titulo":"El Faro Imaginado","autor":"Autora Ficticia","anioPublicacion":2001}'

# add a copy to book 1 (use the id returned above)
curl -X POST http://localhost:8080/api/v1/libros/1/ejemplares -H "$AUTH" -H "Content-Type: application/json" -d '{"codigo":"EJ-1"}'

# create a member: the answer carries score 100 and no alerts
curl -X POST http://localhost:8080/api/v1/socios -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"nombre":"Rosa Ficticia","email":"rosa@example.org"}'

# lend copy 1 to member 1 (the due date is 14 days from today, set by the server)
curl -X POST http://localhost:8080/api/v1/prestamos -H "$AUTH" -H "Content-Type: application/json" -d '{"socioId":1,"ejemplarId":1}'

# renew loan 1 (up to 3 times), then return it
curl -X POST -H "$AUTH" http://localhost:8080/api/v1/prestamos/1/renovacion
curl -X POST -H "$AUTH" http://localhost:8080/api/v1/prestamos/1/devolucion

# the member carries a score and alerts calculated from the history
curl -H "$AUTH" http://localhost:8080/api/v1/socios/1

# list books whose title or author contains "faro", 5 per page
curl -H "$AUTH" "http://localhost:8080/api/v1/libros?q=faro&size=5"
```

| Resource | Main endpoints under `/api/v1` |
|----------|--------------------------------|
| Books | `POST/GET /libros`, `GET/PUT/DELETE /libros/{id}` |
| Copies | `POST/GET /libros/{id}/ejemplares`, `GET/DELETE /ejemplares/{id}`, `POST /ejemplares/{id}/baja` and `/alta` |
| Members | `POST/GET /socios`, `GET/PUT/DELETE /socios/{id}`, `POST /socios/{id}/desactivar` and `/reactivar` |
| Loans | `POST/GET /prestamos`, `GET /prestamos/{id}`, `POST /prestamos/{id}/renovacion` and `/devolucion` |
| Fines | `GET /multas`, `GET /multas/{id}`, `POST /multas/{id}/pago` |
| Sign-in | `POST /auth/login` (the only operation without a token) |
| Account | `GET /me`, `GET /me/prestamos`, `GET /me/multas` (a member sees only their own data) |

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

### The shared contract

The authoritative description of the API lives in its own repository, [biblioteca-contrato](https://github.com/caronteisback/biblioteca-contrato), so that other backends and the frontend follow the same text. This project keeps a **pinned copy** of one tagged version in [`src/test/resources/contrato/`](src/test/resources/contrato/) (the tag and a SHA-256 are in `contrato.properties`) and proves the API follows it: `ContratoTests` calls the real API against Oracle and checks every status, every body (no undocumented field allowed), every error code and every request body against the contract, and fails if an operation of the contract is not exercised. A copy edited by hand, or an operation present on one side only, also fails.

To adopt a new version of the contract, copy its `openapi.yaml` and update the three lines of `contrato.properties` in the same commit. To change the API, change the contract first (a new tagged version) and then the code.

## Sign-in and roles

Every operation but `POST /api/v1/auth/login` needs `Authorization: Bearer <token>`. The token is a JWT signed with HMAC-SHA256, valid for 60 minutes, and the API stores no session.

| Role | May |
|------|-----|
| `LIBRARIAN` | Everything the API offers |
| `MEMBER` | Read the catalog, and only **their own** data: `/me`, `/me/prestamos`, `/me/multas`. The member is read from the token, never from the request, so there is no way to ask for another member's data |

Who may call what is one list in the code (`ReglasDeAcceso`), read both by the filter chain, to enforce it, and by the OpenAPI description, to publish it. Anything that list does not name under `/api` is for librarians only, so a new endpoint is closed to members until someone opens it on purpose. A test walks every operation of the contract with no token, a librarian's and a member's, and checks the answer is the one the contract declares.

- **Accounts** live in the `usuario` table with a salted BCrypt hash; no answer ever carries a password or a hash. They are not created through the API: the first librarian comes from `BIBLIOTECA_ADMIN_EMAIL` and `BIBLIOTECA_ADMIN_PASSWORD` when there are no accounts, and the demo profile creates a librarian and two members.
- **Guessing a password is slowed down:** a wrong email and a wrong password get the same answer and take about the same time, and five failures in a row lock that email for five minutes.
- **Refused calls** use the usual `{ "code", "message" }` shape: `401 UNAUTHENTICATED` (no token, a bad signature, an expired one: the answer never says which), `403 FORBIDDEN`, `401 INVALID_CREDENTIALS`, `429 TOO_MANY_ATTEMPTS`.
- **The key** is `JWT_SECRET`. It has no default: without at least 32 bytes the application does not start.

Limits, on purpose: there is no operation to create or manage accounts; a token stays valid until it expires even if its account is switched off; and the lock counts failures in the memory of one instance (several instances would each count their own).

## Run in a container

The image uses JDK 17, runs as a user without privileges and has a health check. It contains no secret: the database comes from the environment, and the `prod` profile (the image default) stops at startup if a variable is missing.

```bash
docker build -t biblioteca-java .

docker run -d --name biblioteca-api --network biblioteca-java_default -p 8080:8080 \
  -e DB_URL=jdbc:oracle:thin:@//oracle:1521/XEPDB1 \
  -e APP_USER=biblioteca \
  -e APP_USER_PASSWORD=<the value in your .env> \
  -e JWT_SECRET=<a random value of at least 32 characters> \
  biblioteca-java
```

The `--network` flag puts the container on the network of `docker compose up -d`, where the database is reachable as `oracle`. To let a browser page call the API from another origin, set `BIBLIOTECA_CORS_ORIGENES_PERMITIDOS` (comma-separated, no wildcard).

### Demo data

To look at the API with something in it, add the `demo` profile (`SPRING_PROFILES_ACTIVE=prod,demo` in the container, or `--spring.profiles.active=dev,demo` with Maven). On start it loads a small made-up library, only if the catalog is empty: 20 books with their copies, 8 members, and loans in every state (due soon, renewed, four days overdue, returned late with a pending fine, returned late and paid, a deactivated member, a retired copy). It goes through the real services, so the rules apply to it as to any request, and "overdue" stays overdue whichever day you start it. Without the profile nothing is loaded.

The [biblioteca](https://github.com/caronteisback/biblioteca) repository starts the whole system, with this demo data, in one command.

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

399 tests. They start a throwaway Oracle XE 21c container with Testcontainers (Docker must be running; the first run downloads the image), share one container for the whole run and never touch the compose database.

| Level | What it proves |
|-------|----------------|
| Unit | Date rules of a loan, score, fine and alert formulas, constraint-to-error translation, validation of the business parameters |
| Schema | Every rule the database enforces by itself; each rejection is checked by the constraint name Oracle reports |
| Services (real Oracle) | Each rule at its exact boundary (5th and 6th loan, score 50 and 49, due today and due yesterday), the order of the eight checks, every error code |
| Controllers (mocked services) | The HTTP contract: statuses, JSON, validation, CORS |
| Contract | The API against the pinned copy of the shared contract: statuses, bodies, error codes, headers and request bodies of every operation |
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
- Every operation but the sign-in needs a JWT, and a member can only reach their own data (see [Sign-in and roles](#sign-in-and-roles)). Passwords are stored as salted BCrypt hashes, repeated failures lock an email, and refused calls reveal nothing about why.
- The signing key comes from the environment and the application refuses to start without a strong one. Use HTTPS in front of the API in any real deployment: a bearer token sent in clear can be copied.

## Documentation

Everything is in [`docs/`](docs/README.md): [business rules](docs/business-rules.md), [process flows](docs/diagrams/process-flows.md), [sequence diagrams](docs/diagrams/sequence-diagrams.md), [class diagram](docs/diagrams/class-diagram.md), [ER diagram](docs/diagrams/er-diagram.md), [query plans](docs/query-plans.md), [API conventions](docs/api-conventions.md) and [architecture decisions](docs/adr/) and the [OpenAPI document](docs/openapi.yaml). See also the [CHANGELOG](CHANGELOG.md).

## Roadmap

- **Next:** the Python implementation of the same API (`biblioteca-python`), checked against the same contract.
- **Done around it:** the [browser panel](https://github.com/caronteisback/biblioteca-web) and the [whole system in one command](https://github.com/caronteisback/biblioteca).
- **Later:** an operation to manage accounts, and tokens that can be revoked.

## Troubleshooting

- `ORA-12514` or `ORA-65096`: the JDBC URL must point to the service `XEPDB1`, not `XE`.
- Init variables seem ignored: they apply only when the volume is empty. Run `docker compose down -v` and start again.
- `Port 8080 was already in use`: another copy of the API is running (terminal, IDE or container).
- `bad interpreter` in scripts on Windows: files need LF endings; `.gitattributes` enforces this.

## Conventions

Commits follow [Conventional Commits](https://www.conventionalcommits.org/). See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
