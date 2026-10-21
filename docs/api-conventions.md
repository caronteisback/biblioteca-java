# API conventions

Rules that every endpoint follows, so a client learns them once. Business behavior is in [business rules](business-rules.md); the full contract is the [OpenAPI document](openapi.yaml), generated from the code.

Base path: `/api/v1`. Bodies are `application/json`. The API is open until the authentication phase.

## Endpoints implemented

| Resource | Endpoints |
|----------|-----------|
| Books | `POST /libros`, `GET /libros`, `GET /libros/{id}`, `PUT /libros/{id}`, `DELETE /libros/{id}` |
| Copies | `POST /libros/{libroId}/ejemplares`, `GET /libros/{libroId}/ejemplares`, `GET /ejemplares/{id}`, `POST /ejemplares/{id}/baja`, `POST /ejemplares/{id}/alta`, `DELETE /ejemplares/{id}` |
| Members | `POST /socios`, `GET /socios`, `GET /socios/{id}`, `PUT /socios/{id}`, `POST /socios/{id}/desactivar`, `POST /socios/{id}/reactivar`, `DELETE /socios/{id}` |
| Loans | `POST /prestamos` (body: `socioId`, `ejemplarId`), `GET /prestamos`, `GET /prestamos/{id}`, `POST /prestamos/{id}/renovacion`, `POST /prestamos/{id}/devolucion` |
| Fines | `GET /multas`, `GET /multas/{id}`, `POST /multas/{id}/pago` |

A loan request carries only the member and the copy. Loan, due and return dates always come from the server clock; any date sent by the client is ignored.

Optional filters: `GET /prestamos?socioId=&activo=` (`activo=true` lists loans not returned, `false` the returned ones) and `GET /multas?socioId=&estado=` (`PENDING` or `PAID`). A loan answer includes `diasRetraso`, `alertas` and, when it was returned late, its `multa`.

When two requests collide on the same member, copy, loan or fine, the second one waits up to 5 seconds. If the lock is still not free it answers `409 CONCURRENT_UPDATE` and the client may retry.

## Browser access (CORS)

A web page served from another origin can call `/api/**` only if its origin is in `biblioteca.cors.origenes-permitidos`. The defaults are the local development servers (`http://localhost:5500`, `http://127.0.0.1:5500`, `http://localhost:3000`, `http://localhost:8000`). To use another one, set `BIBLIOTECA_CORS_ORIGENES_PERMITIDOS` to a comma-separated list, for example `https://caronteisback.github.io`.

- Origins are written as `scheme://host[:port]`, with no path, no trailing slash and **no wildcard**. A wildcard is rejected when the application starts.
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`. Allowed request header: `Content-Type`. The page can read the `Location` response header. The `Authorization` header is added with authentication in phase 6.
- A request from any other origin gets `403` from Spring before reaching a controller, so that one body is the framework's text and not `{code, message}`. The browser blocks the response anyway.
- Other clients (`curl`, another backend) are not affected: CORS is a browser rule.

## Status codes

| Operation | Success |
|-----------|---------|
| Create | `201 Created` with a `Location` header and the created resource |
| Read, update, action (`baja`, `alta`, `desactivar`, `reactivar`, `renovacion`, `devolucion`, `pago`) | `200 OK` with the resource |
| Delete | `204 No Content`, no body |

Failures use the [error model](business-rules.md#13-error-model): `{ "code": "...", "message": "..." }`.

## Actions are commands, not field edits

State changes that carry rules have their own endpoint (`POST /socios/{id}/desactivar`) instead of a `PATCH` with `{"activo": false}`. The rule (a member with active loans cannot be deactivated) lives in one clear place and has its own error.

## Lists

- Paginated with `page` (from 0, default 0) and `size` (1 to 100, default 20). Out-of-range values are `400 VALIDATION_ERROR`.
- Optional filter `q`: title or author for books, name or email for members, ignoring case. `%` and `_` are literal characters, not wildcards.
- Always ordered by `id`. A `sort` parameter is ignored: letting a client name any field to sort by would turn a typo into a server error.
- Shape of every page:

```json
{ "items": [ ], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
```

- The member list returns a summary without `score` or `alertas`. Calculating them needs each member's whole loan history, so a page of 20 would cost 20 extra queries. Read one member (`GET /socios/{id}`) to get them.

## Calculated, never stored

| Field | Where | Meaning |
|-------|-------|---------|
| `disponible` | copy | Active and without an active loan |
| `score` | member detail | `100 - sum of penalties` from the loan history, see business rules |
| `alertas` | member detail | `PENDING_FINE`, `LOW_SCORE` |

## Input handling

- Text is trimmed before it is stored.
- An ISBN may be sent with hyphens or spaces; it is stored as 10 or 13 characters (a 10-digit ISBN may end in `X`).
- Duplicates (ISBN, email, copy code) are checked before writing and again by the database, so two simultaneous requests cannot both succeed.
