package dev.portafolio.biblioteca.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.dto.Alerta;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.PrestamoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PrestamoController.class)
@ActiveProfiles("test")
class PrestamoControllerTests {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PrestamoService servicio;

	private PrestamoResponse vigente() {
		return new PrestamoResponse(9L, 5L, "EJ-5", "El Faro Imaginado", 3L, HOY, HOY.plusDays(14), null, 0, true, 0, List.of(), null);
	}

	@Test
	void createsALoanAnd201PointsToIt() throws Exception {
		when(servicio.crear(new PrestamoRequest(3L, 5L))).thenReturn(vigente());

		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content("{\"socioId\":3,\"ejemplarId\":5}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/prestamos/9")))
				.andExpect(jsonPath("$.id").value(9))
				.andExpect(jsonPath("$.socioId").value(3))
				.andExpect(jsonPath("$.ejemplarId").value(5))
				.andExpect(jsonPath("$.fechaPrestamo").value("2026-10-19"))
				.andExpect(jsonPath("$.fechaVencimiento").value("2026-11-02"))
				.andExpect(jsonPath("$.fechaDevolucion").doesNotExist())
				.andExpect(jsonPath("$.renovaciones").value(0))
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(jsonPath("$.alertas").isEmpty());
	}

	@Test
	void theClientCannotSendDatesTheyAreIgnored() throws Exception {
		when(servicio.crear(new PrestamoRequest(3L, 5L))).thenReturn(vigente());

		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON)
						.content("{\"socioId\":3,\"ejemplarId\":5,\"fechaPrestamo\":\"2020-01-01\",\"fechaVencimiento\":\"2099-01-01\"}"))
				.andExpect(status().isCreated());

		verify(servicio).crear(new PrestamoRequest(3L, 5L));
	}

	@Test
	void missingOrNonPositiveIdsAreRejectedBeforeTheService() throws Exception {
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("socioId: must not be null")))
				.andExpect(jsonPath("$.message").value(containsString("ejemplarId: must not be null")));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content("{\"socioId\":-1,\"ejemplarId\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("socioId: must be greater than 0")));

		verifyNoInteractions(servicio);
	}

	@Test
	void everyCheckThatFailsKeepsItsOwnStatusAndCode() throws Exception {
		when(servicio.crear(new PrestamoRequest(3L, 5L)))
				.thenThrow(new BusinessException(ErrorCode.COPY_ALREADY_ON_LOAN))
				.thenThrow(new BusinessException(ErrorCode.MEMBER_HAS_PENDING_FINE))
				.thenThrow(new BusinessException(ErrorCode.LOAN_LIMIT_REACHED, "The member already has 5 active loans."))
				.thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		String cuerpo = "{\"socioId\":3,\"ejemplarId\":5}";

		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COPY_ALREADY_ON_LOAN"));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MEMBER_HAS_PENDING_FINE"));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("LOAN_LIMIT_REACHED"))
				.andExpect(jsonPath("$.message").value("The member already has 5 active loans."));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void readsALoanWithItsAlerts() throws Exception {
		when(servicio.obtener(9L)).thenReturn(new PrestamoResponse(9L, 5L, "EJ-5", "El Faro Imaginado", 3L, HOY.minusDays(17), HOY.minusDays(3), null, 0, true, 3,
				List.of(new Alerta(AlertaCodigo.OVERDUE, "Overdue by 3 day(s). Fine so far: S/ 3.00.")), null));

		mvc.perform(get("/api/v1/prestamos/9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diasRetraso").value(3))
				.andExpect(jsonPath("$.alertas[0].code").value("OVERDUE"))
				.andExpect(jsonPath("$.alertas[0].message").value("Overdue by 3 day(s). Fine so far: S/ 3.00."));
	}

	@Test
	void anUnknownLoanIs404() throws Exception {
		when(servicio.obtener(99L)).thenThrow(new BusinessException(ErrorCode.LOAN_NOT_FOUND));

		mvc.perform(get("/api/v1/prestamos/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
	}

	@Test
	void listsWithoutFiltersAndWithTheDefaultPage() throws Exception {
		when(servicio.listar(isNull(), isNull(), eq(0), eq(20))).thenReturn(new PaginaDto<>(List.of(vigente()), 0, 20, 1, 1));

		mvc.perform(get("/api/v1/prestamos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(9))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void passesTheFiltersToTheService() throws Exception {
		when(servicio.listar(3L, true, 1, 10)).thenReturn(new PaginaDto<>(List.of(), 1, 10, 11, 2));

		mvc.perform(get("/api/v1/prestamos").param("socioId", "3").param("activo", "true").param("page", "1").param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPages").value(2));

		verify(servicio).listar(3L, true, 1, 10);
	}

	@Test
	void aBadFilterOrPageIsRejected() throws Exception {
		mvc.perform(get("/api/v1/prestamos").param("activo", "maybe"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The value of 'activo' is not valid."));
		mvc.perform(get("/api/v1/prestamos").param("socioId", "abc")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/prestamos").param("size", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/prestamos").param("size", "101")).andExpect(status().isBadRequest());

		verifyNoInteractions(servicio);
	}

	@Test
	void renewsALoan() throws Exception {
		when(servicio.renovar(9L)).thenReturn(new PrestamoResponse(9L, 5L, "EJ-5", "El Faro Imaginado", 3L, HOY, HOY.plusDays(28), null, 1, true, 0, List.of(), null));

		mvc.perform(post("/api/v1/prestamos/9/renovacion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.renovaciones").value(1))
				.andExpect(jsonPath("$.fechaVencimiento").value("2026-11-16"));
	}

	@Test
	void aRefusedRenewalKeepsItsCode() throws Exception {
		when(servicio.renovar(9L)).thenThrow(new BusinessException(ErrorCode.RENEWAL_LIMIT_REACHED));
		when(servicio.renovar(10L)).thenThrow(new BusinessException(ErrorCode.RENEWAL_NOT_ALLOWED_OVERDUE));
		when(servicio.renovar(11L)).thenThrow(new BusinessException(ErrorCode.LOAN_ALREADY_RETURNED));

		mvc.perform(post("/api/v1/prestamos/9/renovacion")).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("RENEWAL_LIMIT_REACHED"));
		mvc.perform(post("/api/v1/prestamos/10/renovacion")).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("RENEWAL_NOT_ALLOWED_OVERDUE"));
		mvc.perform(post("/api/v1/prestamos/11/renovacion")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));
	}

	@Test
	void returnsALoanAndShowsTheFineWhenThereWasADelay() throws Exception {
		MultaResponse multa = new MultaResponse(4L, 9L, 5, new BigDecimal("5.00"), EstadoMulta.PENDING, HOY, null);
		when(servicio.devolver(9L)).thenReturn(new PrestamoResponse(9L, 5L, "EJ-5", "El Faro Imaginado", 3L, HOY.minusDays(19), HOY.minusDays(5), HOY, 0, false, 5, List.of(), multa));

		mvc.perform(post("/api/v1/prestamos/9/devolucion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false))
				.andExpect(jsonPath("$.fechaDevolucion").value("2026-10-19"))
				.andExpect(jsonPath("$.multa.monto").value(5.00))
				.andExpect(jsonPath("$.multa.estado").value("PENDING"))
				.andExpect(jsonPath("$.multa.diasRetraso").value(5));
	}

	@Test
	void aSecondReturnIs409() throws Exception {
		when(servicio.devolver(9L)).thenThrow(new BusinessException(ErrorCode.LOAN_ALREADY_RETURNED));

		mvc.perform(post("/api/v1/prestamos/9/devolucion"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));
	}

	@Test
	void theActionEndpointsOnlyAcceptPost() throws Exception {
		mvc.perform(get("/api/v1/prestamos/9/renovacion")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mvc.perform(get("/api/v1/prestamos/9/devolucion")).andExpect(status().isMethodNotAllowed());
	}

}
