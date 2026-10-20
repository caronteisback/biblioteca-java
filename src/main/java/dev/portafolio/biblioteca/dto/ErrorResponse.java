package dev.portafolio.biblioteca.dto;

/**
 * Body of every error response. {@code code} is stable and meant for programs; {@code message} is
 * English text for people and may change.
 */
public record ErrorResponse(String code, String message) {
}
