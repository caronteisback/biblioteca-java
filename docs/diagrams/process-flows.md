# Process flows

The three write operations as flowcharts. Each diamond is a check; a failed check stops the process and returns the HTTP status and error code shown in red. The checks run in the order drawn, so the same situation always gives the same error. Codes and statuses come from the [error catalog](../business-rules.md#14-error-catalog).

## 1. Create a loan

```mermaid
flowchart TD
  start(["POST /api/v1/prestamos"]) --> lock["Lock the member row, then the copy row"]
  lock --> c1{"Member and copy exist?"}
  c1 -- no --> e1["404 MEMBER_NOT_FOUND or COPY_NOT_FOUND"]
  c1 -- yes --> c2{"Member active?"}
  c2 -- no --> e2["422 MEMBER_INACTIVE"]
  c2 -- yes --> c3{"Copy active?"}
  c3 -- no --> e3["422 COPY_INACTIVE"]
  c3 -- yes --> c4{"Copy has no active loan?"}
  c4 -- no --> e4["409 COPY_ALREADY_ON_LOAN"]
  c4 -- yes --> c5{"No overdue loan?"}
  c5 -- no --> e5["422 MEMBER_HAS_OVERDUE_LOAN"]
  c5 -- yes --> c6{"No pending fine?"}
  c6 -- no --> e6["422 MEMBER_HAS_PENDING_FINE"]
  c6 -- yes --> c7{"Score at least 50?"}
  c7 -- no --> e7["422 MEMBER_BLOCKED_BY_SCORE"]
  c7 -- yes --> c8{"Fewer than 5 active loans?"}
  c8 -- no --> e8["422 LOAN_LIMIT_REACHED"]
  c8 -- yes --> ok["Create loan: today, due in 14 days, 0 renewals"]
  ok --> done(["201 Created"])
  classDef err fill:#fcebeb,stroke:#a32d2d,color:#501313
  classDef good fill:#eaf3de,stroke:#3b6d11,color:#173404
  class e1,e2,e3,e4,e5,e6,e7,e8 err
  class done good
```

- The member row stays locked until the transaction commits, so two simultaneous loans for one member cannot pass the limit of 5.
- The copy row is locked too, so two simultaneous requests for the same copy are processed one after the other and the second one fails check 4. The unique index is the last safety net and gives the same `409 COPY_ALREADY_ON_LOAN`.

## 2. Renew a loan

```mermaid
flowchart TD
  start(["POST /api/v1/prestamos/{id}/renovacion"]) --> lock["Lock the loan row"]
  lock --> c1{"Loan exists?"}
  c1 -- no --> e1["404 LOAN_NOT_FOUND"]
  c1 -- yes --> c2{"Loan still active?"}
  c2 -- no --> e2["409 LOAN_ALREADY_RETURNED"]
  c2 -- yes --> c3{"Member active?"}
  c3 -- no --> e3["422 MEMBER_INACTIVE"]
  c3 -- yes --> c4{"Not overdue?"}
  c4 -- no --> e4["422 RENEWAL_NOT_ALLOWED_OVERDUE"]
  c4 -- yes --> c5{"Fewer than 3 renewals?"}
  c5 -- no --> e5["422 RENEWAL_LIMIT_REACHED"]
  c5 -- yes --> ok["Due date + 14 days, renewals + 1"]
  ok --> done(["200 OK"])
  classDef err fill:#fcebeb,stroke:#a32d2d,color:#501313
  classDef good fill:#eaf3de,stroke:#3b6d11,color:#173404
  class e1,e2,e3,e4,e5 err
  class done good
```

- The 14 days are added to the **current due date**, not to today.
- A renewal never creates a new loan. A pending fine or a low score does not block it; they only block new loans.

## 3. Return a loan

```mermaid
flowchart TD
  start(["POST /api/v1/prestamos/{id}/devolucion"]) --> lock["Lock the member row, then the loan row"]
  lock --> c1{"Loan exists?"}
  c1 -- no --> e1["404 LOAN_NOT_FOUND"]
  c1 -- yes --> c2{"Loan still active?"}
  c2 -- no --> e2["409 LOAN_ALREADY_RETURNED"]
  c2 -- yes --> set["Return date = today"]
  set --> late{"Today after the due date?"}
  late -- no --> done1(["200 OK, nothing else changes"])
  late -- yes --> fine["Create fine: days late x S/ 1, PENDING"]
  fine --> done2(["200 OK, with the fine"])
  classDef err fill:#fcebeb,stroke:#a32d2d,color:#501313
  classDef good fill:#eaf3de,stroke:#3b6d11,color:#173404
  classDef warn fill:#faeeda,stroke:#854f0b,color:#412402
  class e1,e2 err
  class done1 good
  class fine,done2 warn
```

- A return is always accepted for an active loan, even if the member or the copy is inactive.
- The score is never stored. A late loan lowers it by 10 plus one point per day late, calculated from history on every query. Paying the fine does not give points back.

See the [sequence diagrams](sequence-diagrams.md) for how each process travels through the layers.
