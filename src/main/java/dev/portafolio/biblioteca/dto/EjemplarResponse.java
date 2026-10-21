package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Ejemplar;
import io.swagger.v3.oas.annotations.media.Schema;

public record EjemplarResponse(
		Long id,
		Long libroId,
		String codigo,
		@Schema(description = "False when the copy was retired (damaged or lost).") boolean activo,
		@Schema(description = "Calculated: the copy is active and has no active loan. Never stored.") boolean disponible) {

	public static EjemplarResponse de(Ejemplar ejemplar, boolean enPrestamo) {
		return new EjemplarResponse(
				ejemplar.getId(), ejemplar.getLibro().getId(), ejemplar.getCodigo(), ejemplar.isActivo(), ejemplar.isActivo() && !enPrestamo);
	}

}
