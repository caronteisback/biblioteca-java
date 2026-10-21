package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MultaResponse(
		Long id,
		Long prestamoId,
		@Schema(description = "Days late that the fine charges.", minimum = "1") int diasRetraso,
		@Schema(description = "Amount in soles: days late times the fine per day.", example = "5.00") BigDecimal monto,
		EstadoMulta estado,
		@Schema(description = "Day the fine was created: the day of the late return.") LocalDate fechaGeneracion,
		@Schema(nullable = true, description = "Day the fine was paid; null while it is pending.") LocalDate fechaPago) {

	public static MultaResponse de(Multa multa) {
		return new MultaResponse(
				multa.getId(), multa.getPrestamo().getId(), multa.getDiasRetraso(), multa.getMonto(),
				multa.getEstado(), multa.getFechaGeneracion(), multa.getFechaPago());
	}

}
