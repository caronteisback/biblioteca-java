package dev.portafolio.biblioteca.repository;

import java.math.BigDecimal;

/** Pending fines, their total (null when there are none) and the members that owe them. */
public record ConteoDeMultas(Long pendientes, BigDecimal monto, Long socios) {
}
