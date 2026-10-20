package dev.portafolio.biblioteca.dto;

/** An alert calculated at query time. It is never stored. */
public record Alerta(AlertaCodigo code, String message) {
}
