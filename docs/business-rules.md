# Business rules

Source of truth for the library domain. Code, tests and the OpenAPI contract must match this document. All data is fictional.

## 1. Scope

In scope: books, copies, members, loans, renewals, returns, fines and a derived reliability score.

Out of scope (on purpose): payments, reservations, member types, notifications (email/SMS), user accounts and roles. Roles (`LIBRARIAN`, `MEMBER`) arrive later with JWT authentication; until then the API is open and meant for local use only.

## 2. Glossary

| Term | Meaning |
|------|---------|
| Book (`libro`) | A title in the catalog, identified by ISBN |
| Copy (`ejemplar`) | A physical item of a book. Loans are made on copies, not on books |
| Member (`socio`) | A person who can borrow copies |
| Active loan | A loan with no return date |
| Overdue loan | An active loan whose due date is before today |
| Today | The current date in the `America/Lima` time zone, taken from the server clock |

## 3. Parameters

All numbers below live in `application.yml` and can change without touching code.

| Key | Default | Meaning |
|-----|---------|---------|
| `biblioteca.prestamo.dias-plazo` | 14 | Days granted by a loan and by each renewal |
| `biblioteca.prestamo.max-activos` | 5 | Active loans per member |
| `biblioteca.prestamo.max-renovaciones` | 3 | Renewals per loan |
| `biblioteca.prestamo.alerta-dias-previos` | 2 | Days before the due date that raise a `DUE_SOON` alert |
| `biblioteca.multa.por-dia` | 1.00 | Fine per day of delay, in soles (PEN) |
| `biblioteca.score.inicial` | 100 | Starting score |
| `biblioteca.score.penalizacion-base` | 10 | Points lost per late loan |
| `biblioteca.score.penalizacion-por-dia` | 1 | Extra points lost per day of delay |
| `biblioteca.score.umbral-bloqueo` | 50 | Members below this score cannot start new loans |

## 4. Catalog rules

- A book has a unique ISBN (10 or 13 digits; the format is checked, the check digit is not).
- A copy has a unique code, belongs to one book and has an `activo` flag. `activo = false` retires a damaged or lost copy.
- A copy is **available** when it has no active loan. Availability is calculated, never stored.
- A member has a unique email (case-insensitive) and an `activo` flag.
- A book can be deleted only if it has no copies.
- A copy or a member that appears in any loan is never deleted, only deactivated. Without history they can be deleted.
- A member with active loans cannot be deactivated. A copy with an active loan cannot be retired.
- An inactive member cannot borrow or renew, but can still return.

## 5. Creating a loan

The request carries only the member and the copy. Dates are always set by the server: the loan date is today and the due date is today plus the term.

Checks run in this order, so the same situation always gives the same error:

1. The member and the copy exist.
2. The member is active.
3. The copy is active.
4. The copy has no active loan.
5. The member has no overdue loan.
6. The member has no pending fine.
7. The member's score is at least the block threshold.
8. The member has fewer than the maximum active loans.

## 6. Renewing a loan

A renewal moves the due date forward by the term, counted **from the current due date** (not from today), and adds one to the loan's renewal counter. It does not create a new loan.

Allowed only if: the loan is active, the member is active, the loan is **not overdue**, and the loan has fewer than the maximum renewals. A fine or a low score does not block a renewal; they only block new loans.

After the last renewal the member must return the copy. A new loan of the same copy starts again at zero renewals. With the defaults, one cycle lasts at most 14 + 3 × 14 = 56 days.

## 7. Returning a loan

Sets the return date to today. A return is always accepted for an active loan, even if the member or the copy is inactive.

If the return is after the due date, the delay is `return date − due date` in days and a fine is created. Returning on the due date is on time.

## 8. Fines

- One fine per loan at most, created at return time: `days late × fine per day`.
- A fine is `PENDING` or `PAID`. Payment is registered with `POST /multas/{id}/pago` and must cover the whole amount. There is no payment gateway and no real money.
- Paying a fine does **not** change the score.
- While a loan is overdue and not yet returned, no fine exists; the API shows the amount accrued so far as an alert.

## 9. Score

The score is **derived** from loan history at query time. It is never stored and never edited.

```
penalty(loan) = base penalty + days late × per-day penalty      (only for loans with delay > 0)
score         = max(0, initial score − sum of penalty(loan) over all the member's loans)
```

- For a returned loan, days late use the return date. For an active overdue loan, they use today, so the penalty keeps growing until the copy is returned.
- The score can only go down. Returning on time never raises it: a member who always returns on time keeps 100.
- Its purpose is to show whom to trust less in the future.

Example: due 2026-10-14, returned 2026-10-19. Delay 5 days, fine S/ 5.00, penalty 10 + 5 = 15, score 85.

## 10. Alerts

Alerts are calculated on every query and never stored. Member and loan responses include an `alertas` list with codes:

| Code | When |
|------|------|
| `DUE_SOON` | Active loan due within the alert window (today included) |
| `OVERDUE` | Active loan past its due date; includes the fine accrued so far |
| `PENDING_FINE` | The member has an unpaid fine |
| `LOW_SCORE` | The score is below the block threshold |

## 11. Consistency under concurrency

| Rule | Guarantee |
|------|-----------|
| One active loan per copy | Unique function-based index on `prestamo` over `CASE WHEN fecha_devolucion IS NULL THEN ejemplar_id END`. A violation is translated to `409 COPY_ALREADY_ON_LOAN` |
| At most 5 active loans per member | The member row is locked (`PESSIMISTIC_WRITE`) while a loan is created, so a member's loans are processed one at a time |
| Double return or double renewal | The loan row is locked while it is returned or renewed; the second request sees the new state |
| Renewal counter in range | `CHECK` constraint between 0 and 3 on `prestamo.renovaciones`. The bound is written into the schema; if `max-renovaciones` changes, a migration must change the constraint |

## 12. Time

All date logic uses `LocalDate` in `America/Lima` and an injectable `java.time.Clock`, so tests can move time without waiting. The client never sends loan, due or return dates.

## 13. Error model

Every error body has the same shape and never exposes internals (no stack traces, SQL, table names or framework messages):

```json
{ "code": "LOAN_LIMIT_REACHED", "message": "The member already has 5 active loans." }
```

`code` is stable and meant for programs. `message` is English text for people and may change.

| HTTP | Meaning |
|------|---------|
| 400 | The request is malformed or invalid |
| 404 | The resource does not exist |
| 409 | The request conflicts with the current state |
| 422 | The request is valid but breaks a business rule |
| 500 | Unexpected failure; the body is always generic |

## 14. Error catalog

| HTTP | Code | When |
|------|------|------|
| 400 | `VALIDATION_ERROR` | Missing or malformed field (blank title, bad ISBN format, invalid email) |
| 404 | `BOOK_NOT_FOUND` | Unknown book |
| 404 | `COPY_NOT_FOUND` | Unknown copy |
| 404 | `MEMBER_NOT_FOUND` | Unknown member |
| 404 | `LOAN_NOT_FOUND` | Unknown loan |
| 404 | `FINE_NOT_FOUND` | Unknown fine |
| 409 | `COPY_ALREADY_ON_LOAN` | The copy has an active loan |
| 409 | `LOAN_ALREADY_RETURNED` | Returning or renewing a loan that was already returned |
| 409 | `FINE_ALREADY_PAID` | Paying a fine that is already paid |
| 409 | `DUPLICATE_ISBN` | A book with that ISBN exists |
| 409 | `DUPLICATE_EMAIL` | A member with that email exists |
| 409 | `DUPLICATE_COPY_CODE` | A copy with that code exists |
| 409 | `BOOK_HAS_COPIES` | Deleting a book that has copies |
| 409 | `COPY_HAS_HISTORY` | Deleting a copy that appears in a loan |
| 409 | `COPY_HAS_ACTIVE_LOAN` | Retiring a copy that is on loan |
| 409 | `MEMBER_HAS_HISTORY` | Deleting a member that appears in a loan |
| 409 | `MEMBER_HAS_ACTIVE_LOANS` | Deactivating a member with active loans |
| 422 | `MEMBER_INACTIVE` | Loan or renewal for an inactive member |
| 422 | `COPY_INACTIVE` | Loan of a retired copy |
| 422 | `MEMBER_HAS_OVERDUE_LOAN` | New loan while another one is overdue |
| 422 | `MEMBER_HAS_PENDING_FINE` | New loan while a fine is unpaid |
| 422 | `MEMBER_BLOCKED_BY_SCORE` | New loan with a score below the threshold |
| 422 | `LOAN_LIMIT_REACHED` | The member already has the maximum active loans |
| 422 | `RENEWAL_LIMIT_REACHED` | The loan already used all its renewals |
| 422 | `RENEWAL_NOT_ALLOWED_OVERDUE` | Renewing an overdue loan |
| 500 | `INTERNAL_ERROR` | Anything unexpected; the message is always generic |

## 15. Decisions behind these rules

| Decision | Chosen | Alternative | Why |
|----------|--------|-------------|-----|
| Enforce the 5-loan limit | Lock the member row | Counter column with `CHECK` | No duplicated data that can drift; correct at this scale |
| Score | Derived from history | Stored column updated on return | Counts unreturned loans, no drift, no scheduled job |
| Overdue detection | On query | Scheduled job | Simpler, never stale |
| Copy availability | Calculated | `estado` column | Cannot disagree with the loans |
| Dates | Server only | Client-supplied | Prevents backdated records |
