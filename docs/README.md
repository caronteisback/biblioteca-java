# Documentation

Everything here describes the system before it is built, so code and tests can be checked against it. All data in the project is fictional.

| Document | What it answers |
|----------|-----------------|
| [Business rules](business-rules.md) | What the system must do: loan, renewal, return, fines, score, error catalog |
| [API conventions](api-conventions.md) | Endpoints, status codes, pagination and filters shared by every resource |
| [Process flows](diagrams/process-flows.md) | In which order the checks run and which error each failure returns |
| [Sequence diagrams](diagrams/sequence-diagrams.md) | How a request travels from the librarian to Oracle, with locks and error paths |
| [Class diagram](diagrams/class-diagram.md) | The JPA entities and how they relate in Java |
| [ER diagram](diagrams/er-diagram.md) | The Oracle tables, keys and cardinalities, plus the constraints and indexes |
| [Query plans](query-plans.md) | How to check a plan with `DBMS_XPLAN` and which indexes the hot queries use |
| [Architecture decisions](adr/) | Why Spring Boot 4, JPA, Oracle XE and Flyway |

## Reading order

1. **Business rules**: the source of truth.
2. **Process flows** and **sequence diagrams**: the rules as behavior.
3. **Class** and **ER diagrams**: the rules as structure, in Java and in the database.

Diagrams are written in [Mermaid](https://mermaid.js.org/), which GitHub renders directly in the browser. If a diagram appears as plain text, open the file on GitHub or in an editor with a Mermaid preview.
