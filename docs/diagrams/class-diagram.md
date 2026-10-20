# Class diagram (JPA entities)

The domain model in Java: the `@Entity` classes, one enum and the service that derives the score. Names are Spanish where they come from the domain.

```mermaid
classDiagram
  direction LR
  class Libro {
    -Long id
    -String isbn
    -String titulo
    -String autor
    -Integer anioPublicacion
  }
  class Ejemplar {
    -Long id
    -String codigo
    -boolean activo
  }
  class Socio {
    -Long id
    -String nombre
    -String email
    -boolean activo
    -LocalDate fechaRegistro
  }
  class Prestamo {
    -Long id
    -LocalDate fechaPrestamo
    -LocalDate fechaVencimiento
    -LocalDate fechaDevolucion
    -int renovaciones
    +isActivo() boolean
    +isVencido(LocalDate hoy) boolean
    +diasRetraso(LocalDate hoy) int
  }
  class Multa {
    -Long id
    -int diasRetraso
    -BigDecimal monto
    -EstadoMulta estado
    -LocalDate fechaGeneracion
    -LocalDate fechaPago
  }
  class EstadoMulta {
    <<enumeration>>
    PENDING
    PAID
  }
  class ScoreCalculator {
    <<service>>
    +calcular(List~Prestamo~ prestamos, LocalDate hoy) int
  }
  Ejemplar "*" --> "1" Libro : libro
  Prestamo "*" --> "1" Ejemplar : ejemplar
  Prestamo "*" --> "1" Socio : socio
  Multa "0..1" --> "1" Prestamo : prestamo
  Multa --> EstadoMulta
  ScoreCalculator ..> Prestamo : reads
```

## Design notes

- **Associations only from the "many" side.** `Prestamo` points to `Ejemplar` and `Socio` with `@ManyToOne(fetch = LAZY)`, but `Socio` has no list of loans. This avoids loading huge collections by accident; histories are read through paginated repository queries.
- **Derived, not stored.** The score lives in `ScoreCalculator`, which reads loans and applies the formula in the [business rules](../business-rules.md). Availability and alerts are calculated the same way.
- **`Prestamo` helper methods** take today's date as an argument, so tests can move time with an injected `java.time.Clock` instead of waiting.
- **Booleans.** `activo` is `boolean` in Java and `NUMBER(1)` in Oracle, which has no boolean column type in tables. JPA converts between them.
- **Dates** are `LocalDate`, all interpreted in the `America/Lima` zone. Money is `BigDecimal`.

The matching relational model is in the [ER diagram](er-diagram.md).
