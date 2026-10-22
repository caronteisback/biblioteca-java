package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Prestamo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * A loan as of today. {@code diasRetraso}, {@code alertas} and {@code activo} are calculated now
 * and never stored; {@code multa} is present only when the loan was returned late.
 */
public record PrestamoResponse(
		Long id,
		Long ejemplarId,
		@Schema(description = "Code of the borrowed copy, so a screen can name it without a second call.") String codigoEjemplar,
		@Schema(description = "Title of the book the borrowed copy belongs to, so a screen can name the loan without a second call.") String tituloLibro,
		Long socioId,
		@Schema(description = "Day the loan started, set by the server.") LocalDate fechaPrestamo,
		@Schema(description = "Last day to return without a fine. Each renewal moves it forward.") LocalDate fechaVencimiento,
		@Schema(nullable = true, description = "Day the copy was returned; null while the loan is active.") LocalDate fechaDevolucion,
		@Schema(description = "Renewals used so far.", minimum = "0", maximum = "3") int renovaciones,
		@Schema(description = "True while the copy has not been returned.") boolean activo,
		@Schema(description = "Days late as of today for an active loan, or up to the return date for a returned one. Zero when on time.", minimum = "0") int diasRetraso,
		@Schema(description = "DUE_SOON or OVERDUE for an active loan; empty once returned.") List<Alerta> alertas,
		@Schema(nullable = true, description = "The fine created by a late return; null when the loan was on time or is still active.") MultaResponse multa) {

	public static PrestamoResponse de(Prestamo prestamo, LocalDate hoy, List<Alerta> alertas, MultaResponse multa) {
		return new PrestamoResponse(
				prestamo.getId(),
				prestamo.getEjemplar().getId(),
				prestamo.getEjemplar().getCodigo(),
				prestamo.getEjemplar().getLibro().getTitulo(),
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
