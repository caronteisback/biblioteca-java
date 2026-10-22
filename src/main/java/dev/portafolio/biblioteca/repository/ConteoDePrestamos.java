package dev.portafolio.biblioteca.repository;

/** Active loans and how many of them are overdue or due soon, counted in a single query. */
public record ConteoDePrestamos(Long activos, Long vencidos, Long porVencer) {
}
