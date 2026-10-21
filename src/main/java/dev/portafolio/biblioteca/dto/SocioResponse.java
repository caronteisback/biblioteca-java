package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Socio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** One member with the score and the alerts calculated from the loan history at query time. */
public record SocioResponse(
		Long id,
		String nombre,
		String email,
		boolean activo,
		@Schema(description = "Day of registration, set by the server.") LocalDate fechaRegistro,
		@Schema(description = "Reliability score from 0 to 100, calculated from the loan history. It only goes down.", minimum = "0", maximum = "100") int score,
		@Schema(description = "PENDING_FINE and LOW_SCORE, calculated now.") List<Alerta> alertas) {

	public static SocioResponse de(Socio socio, int score, List<Alerta> alertas) {
		return new SocioResponse(
				socio.getId(), socio.getNombre(), socio.getEmail(), socio.isActivo(), socio.getFechaRegistro(), score, alertas);
	}

}
