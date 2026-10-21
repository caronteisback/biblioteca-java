package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MultaResponse(
		Long id,
		Long prestamoId,
		int diasRetraso,
		BigDecimal monto,
		EstadoMulta estado,
		LocalDate fechaGeneracion,
		LocalDate fechaPago) {

	public static MultaResponse de(Multa multa) {
		return new MultaResponse(
				multa.getId(), multa.getPrestamo().getId(), multa.getDiasRetraso(), multa.getMonto(),
				multa.getEstado(), multa.getFechaGeneracion(), multa.getFechaPago());
	}

}
