package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResumenDePrestamos(
		@Schema(description = "Loans whose copy has not been returned.", minimum = "0") long activos,
		@Schema(description = "Active loans whose due date has passed.", minimum = "0") long vencidos,
		@Schema(description = "Active loans, not yet overdue, due within `diasPorVencer` days.", minimum = "0") long porVencer,
		@Schema(description = "The window, in days, that `porVencer` counts (today included).", minimum = "1") int diasPorVencer) {
}
