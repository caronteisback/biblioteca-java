# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/) and the versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- **OpenAPI 3.1 document** generated from the code: 26 operations with ids, summaries and descriptions, the error codes of each one (with shared examples), `201` with `Location` and `204` where they apply, and `required` on every field that is never null. The `ErrorResponse` schema lists every error code.
- **Swagger UI** at `/swagger-ui.html` and the document at `/v3/api-docs`, in the `dev` profile only; with `prod` both answer `404`.
- **`docs/openapi.yaml`**, a committed copy that a test compares with the generated document, so it cannot drift.

- **Conformance to the shared contract** ([biblioteca-contrato](https://github.com/caronteisback/biblioteca-contrato) 0.1.1): a pinned copy under `src/test/resources/contrato/` and a test that calls the real API and checks every status, body, error code, header and request body against it. Writing it found that every successful answer was declared as `*/*`; it is now `application/json`.

- **CORS:** the Vite development server of the React frontend (`http://localhost:5173`, `http://127.0.0.1:5173`) is now in the default allowed origins.

### Tests

- 307 tests: 24 new ones check that the document is valid, complete and hidden outside `dev`, and that the API follows the contract.

### Changed

- The project version is now `0.2.0-SNAPSHOT`. The API contract version (`0.1.0`) is kept apart from the artifact version.

## [0.1.0] - 2026-10-21

First complete version of the library API.

### Added

- **Schema** (Flyway `V1`): books, copies, members, loans and fines with keys, `CHECK` constraints and indexes, including the function-based unique index that allows one active loan per copy.
- **Catalog and members:** create, read, update, delete, filtered and paginated lists, and commands to retire a copy and deactivate a member.
- **Loans:** eight ordered checks on creation, up to three renewals from the current due date, returns with a fine of S/ 1 per day late, and paying a fine whole and once.
- **Derived data:** member score, alerts and copy availability, calculated from the history and never stored.
- **Error model:** one `{code, message}` shape for every failure, with the status and the code documented in the error catalog; nothing internal reaches the client.
- **Concurrency:** row locks in a fixed order (member, copy, loan) with a 5-second cap, and `409 CONCURRENT_UPDATE` when it runs out.
- **CORS** for the browser frontend: configurable origins, never a wildcard.
- **Container image** with JDK 17, a non-root user and a health check; a `prod` profile that takes the database from the environment.
- **Continuous integration** on GitHub Actions: all tests on Oracle XE, and the image build.
- **Documentation:** business rules, process flows, sequence diagrams, class and ER diagrams, query plans, API conventions and architecture decision records.

### Tests

- 283 tests on Oracle XE with Testcontainers, including nine concurrency tests that fail when the locks are removed.

### Known limitations

- The API has no authentication yet and must not be exposed to a network.
- The upper bound of the renewals `CHECK` (3) is written in the schema; changing the configured maximum needs a migration.
