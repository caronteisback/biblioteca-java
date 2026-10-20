# API conventions

Rules that every endpoint follows, so a client learns them once. Business behavior is in [business rules](business-rules.md); the full OpenAPI contract arrives in a later part.

Base path: `/api/v1`. Bodies are `application/json`. The API is open until the authentication phase.

## Endpoints implemented

| Resource | Endpoints |
|----------|-----------|
| Books | `POST /libros`, `GET /libros`, `GET /libros/{id}`, `PUT /libros/{id}`, `DELETE /libros/{id}` |
| Copies | `POST /libros/{libroId}/ejemplares`, `GET /libros/{libroId}/ejemplares`, `GET /ejemplares/{id}`, `POST /ejemplares/{id}/baja`, `POST /ejemplares/{id}/alta`, `DELETE /ejemplares/{id}` |
| Members | `POST /socios`, `GET /socios`, `GET /socios/{id}`, `PUT /socios/{id}`, `POST /socios/{id}/desactivar`, `POST /socios/{id}/reactivar`, `DELETE /socios/{id}` |

Loans (`/prestamos`) and fines (`/multas`) come next.

## Status codes

| Operation | Success |
|-----------|---------|
| Create | `201 Created` with a `Location` header and the created resource |
| Read, update, action (`baja`, `alta`, `desactivar`, `reactivar`) | `200 OK` with the resource |
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
