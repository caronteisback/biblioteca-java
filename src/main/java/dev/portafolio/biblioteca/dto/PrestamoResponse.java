package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Prestamo;
import java.time.LocalDate;
import java.util.List;

/**
 * A loan as of today. {@code diasRetraso}, {@code alertas} and {@code activo} are calculated now
 * and never stored; {@code multa} is present only when the loan was returned late.
 */
public record PrestamoResponse(
		Long id,
		Long ejemplarId,
		Long socioId,
		LocalDate fechaPrestamo,
		LocalDate fechaVencimiento,
		LocalDate fechaDevolucion,
		int renovaciones,
		boolean activo,
		int diasRetraso,
		List<Alerta> alertas,
		MultaResponse multa) {

	public static PrestamoResponse de(Prestamo prestamo, LocalDate hoy, List<Alerta> alertas, MultaResponse multa) {
		return new PrestamoResponse(
				prestamo.getId(),
				prestamo.getEjemplar().getId(),
				prestamo.getSocio().getId(),
				prestamo.getFechaPrestamo(),
				prestamo.getFechaVencimiento(),
				prestamo.getFechaDevolucion(),
				prestamo.getRenovaciones(),
				prestamo.isActivo(),
				prestamo.diasRetraso(hoy),
				alertas,
				multa);
	}

}
