package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Socio;
import java.time.LocalDate;
import java.util.List;

/** One member with the score and the alerts calculated from the loan history at query time. */
public record SocioResponse(
		Long id, String nombre, String email, boolean activo, LocalDate fechaRegistro, int score, List<Alerta> alertas) {

	public static SocioResponse de(Socio socio, int score, List<Alerta> alertas) {
		return new SocioResponse(
				socio.getId(), socio.getNombre(), socio.getEmail(), socio.isActivo(), socio.getFechaRegistro(), score, alertas);
	}

}
