# Sequence diagrams

How a request travels from the librarian to Oracle in the three write operations. `alt` blocks mark the choice: the first branch is the error path, the last one the success path.

Participants: the librarian (client), the REST **Controller**, the **Service** that holds the business rules and the transaction, the Spring Data **JPA repositories**, and **Oracle**.

## 1. Create a loan

```mermaid
sequenceDiagram
  autonumber
  actor C as Librarian
  participant API as Controller
  participant S as Service
  participant R as JPA repositories
  participant DB as Oracle
  C->>API: POST /api/v1/prestamos (socioId, ejemplarId)
  API->>API: validate input (Bean Validation)
  API->>S: crearPrestamo(socioId, ejemplarId)
  activate S
  S->>R: find member with lock
  R->>DB: SELECT ... FOR UPDATE (member row)
  DB-->>S: member locked
  S->>R: find copy with lock
  R->>DB: SELECT ... FOR UPDATE (copy row)
  DB-->>S: copy locked
  S->>R: member's loans, fines
  R->>DB: queries with bind variables
  DB-->>S: data
  S->>S: compute score from history
  Note over S: 8 checks in order:<br/>exist, member active, copy active,<br/>copy free, no overdue loan, no pending fine,<br/>score at least 50, fewer than 5 loans
  alt a check fails
    S-->>API: business error with code
    API-->>C: 404, 409 or 422 with code and message
  else all checks pass
    S->>R: save loan (today, today + 14 days, 0 renewals)
    R->>DB: INSERT
    alt last safety net: the unique index rejects a second active loan of the copy
      DB-->>S: ORA-00001
      S-->>API: COPY_ALREADY_ON_LOAN
      API-->>C: 409 with code and message
    else created
      DB-->>S: row created
      S-->>API: loan with alerts
      API-->>C: 201 Created
    end
  end
  deactivate S
  Note over S,DB: COMMIT releases both locks
```

- The member row is locked **before** the checks, so two simultaneous loans for the same member are processed one at a time and cannot exceed the limit of 5.
- The copy row is locked right after, so when two librarians lend the same copy at once the second one waits, then sees the active loan and gets `409 COPY_ALREADY_ON_LOAN` from the normal check. The same lock stops a copy from being retired while it is being lent.
- The unique function-based index stays as the last safety net: if it ever fires, the handler translates `ORA-00001` to the same `409 COPY_ALREADY_ON_LOAN`.
- Locks are always taken in the same order (member, copy, loan) and each wait is capped at 5 seconds; if it runs out the answer is `409 CONCURRENT_UPDATE`.

## 2. Renew a loan

```mermaid
sequenceDiagram
  autonumber
  actor C as Librarian
  participant API as Controller
  participant S as Service
  participant R as JPA repositories
  participant DB as Oracle
  C->>API: POST /api/v1/prestamos/{id}/renovacion
  API->>S: renovar(prestamoId)
  activate S
  S->>R: find loan with lock
  R->>DB: SELECT ... FOR UPDATE (loan row)
  DB-->>S: loan locked
  Note over S: checks in order:<br/>exists, still active, member active,<br/>not overdue, fewer than 3 renewals
  alt a check fails
    S-->>API: business error with code
    API-->>C: 404, 409 or 422 with code and message
  else all checks pass
    S->>S: due date = current due date + 14 days
    S->>R: save (renewals + 1)
    R->>DB: UPDATE
    DB-->>S: ok
    S-->>API: loan with new due date
    API-->>C: 200 OK
  end
  deactivate S
  Note over S,DB: no new loan is created, so the unique indexes stay valid
```

## 3. Return a loan

```mermaid
sequenceDiagram
  autonumber
  actor C as Librarian
  participant API as Controller
  participant S as Service
  participant R as JPA repositories
  participant DB as Oracle
  C->>API: POST /api/v1/prestamos/{id}/devolucion
  API->>S: devolver(prestamoId)
  activate S
  S->>R: member id of the loan (id only)
  R->>DB: SELECT member id
  DB-->>S: member id
  S->>R: find member with lock
  R->>DB: SELECT ... FOR UPDATE (member row)
  DB-->>S: member locked
  S->>R: find loan with lock
  R->>DB: SELECT ... FOR UPDATE (loan row)
  DB-->>S: loan locked
  alt not found or already returned
    S-->>API: LOAN_NOT_FOUND or LOAN_ALREADY_RETURNED
    API-->>C: 404 or 409 with code and message
  else active
    S->>S: return date = today
    alt today is after the due date
      S->>S: fine = days late x S/ 1
      S->>R: save fine as PENDING
      R->>DB: INSERT into multa
      DB-->>S: ok
    end
    S->>R: save loan
    R->>DB: UPDATE
    DB-->>S: ok
    S-->>API: return result, with fine if any
    API-->>C: 200 OK
  end
  deactivate S
  Note over S,DB: the score is not stored, it is recalculated on every query
```

- Only the member id is read before locking, not the loan. Loading the loan first would leave a copy in memory that the later lock does not refresh, and the return could act on stale data.
- The member lock is defensive. With the current order of checks a loan being created cannot slip past the fine that a return is creating even without it (tests that removed it still passed), but the lock keeps that rule from depending on the order of two checks. It also makes two returns of the same member's loans go one after the other.

The error codes and their HTTP status are listed in the [error catalog](../business-rules.md#14-error-catalog).
