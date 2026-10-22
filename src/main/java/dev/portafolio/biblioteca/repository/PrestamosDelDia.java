package dev.portafolio.biblioteca.repository;

import java.time.LocalDate;

/** Loans started on one day: at most one row per day, so a few weeks are a few dozen rows. */
public record PrestamosDelDia(LocalDate dia, Long total) {
}
