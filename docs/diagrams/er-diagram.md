# Entity-relationship diagram (Oracle)

The relational model that Flyway migration `V1` will create. Table and column names stay in Spanish (the domain language); everything else is English.

```mermaid
erDiagram
  LIBRO ||--o{ EJEMPLAR : "has copies"
  EJEMPLAR ||--o{ PRESTAMO : "is lent in"
  SOCIO ||--o{ PRESTAMO : "borrows"
  PRESTAMO ||--o| MULTA : "may generate"
  LIBRO {
    NUMBER id PK
    VARCHAR2 isbn UK
    VARCHAR2 titulo
    VARCHAR2 autor
    NUMBER anio_publicacion
  }
  EJEMPLAR {
    NUMBER id PK
    NUMBER libro_id FK
    VARCHAR2 codigo UK
    NUMBER activo "0 or 1"
  }
  SOCIO {
    NUMBER id PK
    VARCHAR2 nombre
    VARCHAR2 email UK "unique, case-insensitive"
    NUMBER activo "0 or 1"
    DATE fecha_registro
  }
  PRESTAMO {
    NUMBER id PK
    NUMBER ejemplar_id FK
    NUMBER socio_id FK
    DATE fecha_prestamo
    DATE fecha_vencimiento
    DATE fecha_devolucion "null means active"
    NUMBER renovaciones "CHECK 0 to 3"
  }
  MULTA {
    NUMBER id PK
    NUMBER prestamo_id FK, UK
    NUMBER dias_retraso
    NUMBER monto "10,2 in soles"
    VARCHAR2 estado "PENDING or PAID"
    DATE fecha_generacion
    DATE fecha_pago "null while pending"
  }
```

## What the diagram does not draw

| Object | Purpose |
|--------|---------|
| Unique function-based index on `prestamo` over `CASE WHEN fecha_devolucion IS NULL THEN ejemplar_id END` | At most one active loan per copy |
| Unique index on `LOWER(socio.email)` | Case-insensitive unique email |
| `CHECK (renovaciones BETWEEN 0 AND 3)` | Renewal counter stays in range |
| `CHECK (fecha_vencimiento >= fecha_prestamo)` and `CHECK (fecha_devolucion >= fecha_prestamo)` | Coherent dates |
| `CHECK (estado IN ('PENDING','PAID'))` on `multa` | Valid fine status |
| Index on `ejemplar(libro_id)` and on `prestamo(socio_id)` | Oracle does not index foreign keys by itself; these serve joins and the active-loans lookup |

## What is deliberately not stored

- **Score**: derived from loan history at query time.
- **Copy availability**: a copy is available when it has no active loan.
- **Alerts** (`DUE_SOON`, `OVERDUE`, `PENDING_FINE`, `LOW_SCORE`): calculated on every query.

See [business rules](../business-rules.md) for the reasoning behind each choice.
