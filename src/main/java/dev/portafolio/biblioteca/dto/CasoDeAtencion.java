package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** An active loan that needs the librarian's attention: it is overdue or about to be. */
@Schema(description = "An active loan that needs the librarian's attention.")
public record CasoDeAtencion(
		Long prestamoId,
		Long socioId,
		String nombreSocio,
		String tituloLibro,
		String codigoEjemplar,
		LocalDate fechaVencimiento,
		@Schema(description = "Days late as of today; zero when the loan is only due soon.", minimum = "0") int diasRetraso,
		@Schema(description = "OVERDUE when the due date has passed, DUE_SOON when it is within the days that the dashboard counts as soon.",
				allowableValues = { "DUE_SOON", "OVERDUE" }) AlertaCodigo alerta) {
}
