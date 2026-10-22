package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record ResumenDeMultas(
		@Schema(minimum = "0") long pendientes,
		@Schema(description = "Total of the pending fines, in soles.", example = "19.00") BigDecimal montoPendiente,
		@Schema(description = "Members with at least one pending fine; they cannot start new loans until they pay.", minimum = "0") long sociosConMultaPendiente) {
}
