package dev.portafolio.biblioteca.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** A loan is requested with only the member and the copy: dates always come from the server clock. */
public record PrestamoRequest(@NotNull @Positive Long socioId, @NotNull @Positive Long ejemplarId) {
}
