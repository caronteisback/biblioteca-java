package dev.portafolio.biblioteca.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.CasoDeAtencion;
import dev.portafolio.biblioteca.dto.LibroMasPrestado;
import dev.portafolio.biblioteca.dto.PanelResponse;
import dev.portafolio.biblioteca.dto.ResumenDeMultas;
import dev.portafolio.biblioteca.dto.ResumenDePrestamos;
import dev.portafolio.biblioteca.dto.SemanaDePrestamos;
import dev.portafolio.biblioteca.service.PanelService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PanelController.class)
@ActiveProfiles("test")
class PanelControllerTests {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PanelService servicio;

	@Test
	void answersWithEveryFigureOfTheDashboard() throws Exception {
		when(servicio.resumen()).thenReturn(new PanelResponse(
				HOY,
				new ResumenDePrestamos(18, 3, 5, 7),
				20,
				8,
				new ResumenDeMultas(2, new BigDecimal("19.00"), 1),
				List.of(new SemanaDePrestamos(HOY.minusWeeks(1), 9), new SemanaDePrestamos(HOY, 12)),
				List.of(new LibroMasPrestado(4L, "El Faro Imaginado", 9L)),
				List.of(new CasoDeAtencion(7L, 3L, "Rosa Valdivia", "La Biblioteca de Arena", "EJ-004", HOY.minusDays(4), 4, AlertaCodigo.OVERDUE))));

		mvc.perform(get("/api/v1/panel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fecha").value("2026-10-19"))
				.andExpect(jsonPath("$.prestamos.activos").value(18))
				.andExpect(jsonPath("$.prestamos.vencidos").value(3))
				.andExpect(jsonPath("$.prestamos.porVencer").value(5))
				.andExpect(jsonPath("$.prestamos.diasPorVencer").value(7))
				.andExpect(jsonPath("$.ejemplaresActivos").value(20))
				.andExpect(jsonPath("$.sociosActivos").value(8))
				.andExpect(jsonPath("$.multas.pendientes").value(2))
				.andExpect(jsonPath("$.multas.montoPendiente").value(19.00))
				.andExpect(jsonPath("$.multas.sociosConMultaPendiente").value(1))
				.andExpect(jsonPath("$.prestamosPorSemana.length()").value(2))
				.andExpect(jsonPath("$.prestamosPorSemana[1].semana").value("2026-10-19"))
				.andExpect(jsonPath("$.prestamosPorSemana[1].prestamos").value(12))
				.andExpect(jsonPath("$.masPrestados[0].titulo").value("El Faro Imaginado"))
				.andExpect(jsonPath("$.masPrestados[0].prestamos").value(9))
				.andExpect(jsonPath("$.requierenAtencion[0].nombreSocio").value("Rosa Valdivia"))
				.andExpect(jsonPath("$.requierenAtencion[0].tituloLibro").value("La Biblioteca de Arena"))
				.andExpect(jsonPath("$.requierenAtencion[0].alerta").value("OVERDUE"))
				.andExpect(jsonPath("$.requierenAtencion[0].diasRetraso").value(4));
	}

}
