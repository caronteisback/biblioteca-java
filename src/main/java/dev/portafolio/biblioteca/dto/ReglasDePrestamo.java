package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The lending rules the API applies, as configured. A screen can show a member how close they are to a limit
 * without copying the numbers, which would drift from the configuration.
 */
@Schema(description = "The lending rules the API applies, so a screen can show a member how close they are to a limit without copying the numbers.")
public record ReglasDePrestamo(
		@Schema(description = "Active loans a member may have at once; with that many, a new loan is refused with LOAN_LIMIT_REACHED.", minimum = "1") int maxPrestamosActivos,
		@Schema(description = "A member whose score is below this cannot start new loans (MEMBER_BLOCKED_BY_SCORE).", minimum = "0", maximum = "100") int puntajeMinimo,
		@Schema(description = "Renewals allowed per loan.", minimum = "0", maximum = "3") int maxRenovaciones,
		@Schema(description = "Days granted by a loan and by each renewal.", minimum = "1") int diasPlazo) {

	public static ReglasDePrestamo de(BibliotecaProperties propiedades) {
		return new ReglasDePrestamo(
				propiedades.prestamo().maxActivos(),
				propiedades.score().umbralBloqueo(),
				propiedades.prestamo().maxRenovaciones(),
				propiedades.prestamo().diasPlazo());
	}

}
