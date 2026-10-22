package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.PanelResponse;
import dev.portafolio.biblioteca.service.PanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class PanelController {

	private final PanelService panel;

	@GetMapping
	@Operation(operationId = "getDashboard", summary = "Get the dashboard of the library",
			description = """
					The figures for the home of the librarian, all as of today (in the library's time zone) and calculated now from the loans, fines and members: nothing is stored.

					- `prestamos`: active loans, how many are overdue and how many are due within `diasPorVencer` days (today included).
					- `multas`: the pending fines, their total and how many members have one.
					- `prestamosPorSemana`: loans started in each of the last 6 weeks, the current one included, oldest first. A week starts on Monday; a week without loans counts 0.
					- `masPrestados`: up to 5 books with the most loans started in the last 90 days.
					- `requierenAtencion`: up to 5 active loans that are overdue (the most late first) or due within `diasPorVencer` days (the soonest first).""")
	PanelResponse resumen() {
		return panel.resumen();
	}

}
