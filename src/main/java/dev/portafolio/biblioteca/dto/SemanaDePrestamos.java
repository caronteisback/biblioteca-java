package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record SemanaDePrestamos(
		@Schema(description = "The Monday that starts the week.") LocalDate semana,
		@Schema(minimum = "0") long prestamos) {
}
