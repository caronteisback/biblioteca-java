package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Ejemplar;

/** {@code disponible} is calculated: the copy is active and has no active loan. It is never stored. */
public record EjemplarResponse(Long id, Long libroId, String codigo, boolean activo, boolean disponible) {

	public static EjemplarResponse de(Ejemplar ejemplar, boolean enPrestamo) {
		return new EjemplarResponse(
				ejemplar.getId(), ejemplar.getLibro().getId(), ejemplar.getCodigo(), ejemplar.isActivo(), ejemplar.isActivo() && !enPrestamo);
	}

}
