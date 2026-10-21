package dev.portafolio.biblioteca.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.MultaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MultaController.class)
@ActiveProfiles("test")
class MultaControllerTests {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private MultaService servicio;

	private MultaResponse pendiente() {
		return new MultaResponse(4L, 9L, 5, new BigDecimal("5.00"), EstadoMulta.PENDING, HOY, null);
	}

	@Test
	void readsAFine() throws Exception {
		when(servicio.obtener(4L)).thenReturn(pendiente());

		mvc.perform(get("/api/v1/multas/4"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prestamoId").value(9))
				.andExpect(jsonPath("$.monto").value(5.00))
				.andExpect(jsonPath("$.estado").value("PENDING"))
				.andExpect(jsonPath("$.fechaPago").doesNotExist());
	}

	@Test
	void anUnknownFineIs404() throws Exception {
		when(servicio.obtener(99L)).thenThrow(new BusinessException(ErrorCode.FINE_NOT_FOUND));

		mvc.perform(get("/api/v1/multas/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("FINE_NOT_FOUND"));
	}

	@Test
	void listsWithTheDefaultPageAndNoFilter() throws Exception {
		when(servicio.listar(isNull(), isNull(), eq(0), eq(20))).thenReturn(new PaginaDto<>(List.of(pendiente()), 0, 20, 1, 1));

		mvc.perform(get("/api/v1/multas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(4));
	}

	@Test
	void passesTheMemberAndTheStatusToTheService() throws Exception {
		when(servicio.listar(3L, EstadoMulta.PENDING, 0, 20)).thenReturn(new PaginaDto<>(List.of(pendiente()), 0, 20, 1, 1));

		mvc.perform(get("/api/v1/multas").param("socioId", "3").param("estado", "PENDING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].estado").value("PENDING"));

		verify(servicio).listar(3L, EstadoMulta.PENDING, 0, 20);
	}

	@Test
	void anUnknownStatusIsRejected() throws Exception {
		mvc.perform(get("/api/v1/multas").param("estado", "OVERDUE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("The value of 'estado' is not valid."));

		verifyNoInteractions(servicio);
	}

	@Test
	void paysAFine() throws Exception {
		when(servicio.pagar(4L)).thenReturn(new MultaResponse(4L, 9L, 5, new BigDecimal("5.00"), EstadoMulta.PAID, HOY, HOY));

		mvc.perform(post("/api/v1/multas/4/pago"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAID"))
				.andExpect(jsonPath("$.fechaPago").value("2026-10-19"));
	}

	@Test
	void payingTwiceIs409() throws Exception {
		when(servicio.pagar(4L)).thenThrow(new BusinessException(ErrorCode.FINE_ALREADY_PAID));

		mvc.perform(post("/api/v1/multas/4/pago"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FINE_ALREADY_PAID"));
	}

	@Test
	void thePaymentEndpointOnlyAcceptsPost() throws Exception {
		mvc.perform(get("/api/v1/multas/4/pago")).andExpect(status().isMethodNotAllowed());
	}

}
