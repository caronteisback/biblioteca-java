package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** The figures of the librarian's home, all as of today and calculated on every request: none is stored. */
@Schema(description = "The figures of the dashboard of the librarian, as of today.")
public record PanelResponse(
		@Schema(description = "Today in the library's time zone, set by the server. Every figure is as of this day.") LocalDate fecha,
		ResumenDePrestamos prestamos,
		@Schema(description = "Copies that can be lent (not retired).", minimum = "0") long ejemplaresActivos,
		@Schema(minimum = "0") long sociosActivos,
		ResumenDeMultas multas,
		@Schema(description = "Loans started in each of the last 6 weeks, the current one included, oldest first. Always 6 entries.") List<SemanaDePrestamos> prestamosPorSemana,
		@Schema(description = "Up to 5 books with the most loans started in the last 90 days, the most borrowed first.") List<LibroMasPrestado> masPrestados,
		@Schema(description = "Up to 5 active loans that are overdue (the most late first) or due soon (the soonest first).") List<CasoDeAtencion> requierenAtencion) {
}
