package dev.portafolio.biblioteca.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.dto.Alerta;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.SocioRequest;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.dto.SocioResumen;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.SocioService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SocioController.class)
@ActiveProfiles("test")
class SocioControllerTests {

	private static final String VALIDO = "{\"nombre\":\"Rosa Ficticia\",\"email\":\"rosa@example.org\"}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private SocioService servicio;

	private SocioResponse socio(int score, List<Alerta> alertas) {
		return new SocioResponse(3L, "Rosa Ficticia", "rosa@example.org", true, LocalDate.of(2026, 10, 19), score, alertas);
	}

	@Test
	void createsAndAnswers201WithLocationAndScore() throws Exception {
		when(servicio.crear(any())).thenReturn(socio(100, List.of()));

		mvc.perform(post("/api/v1/socios").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/socios/3")))
				.andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.fechaRegistro").value("2026-10-19"))
				.andExpect(jsonPath("$.score").value(100))
				.andExpect(jsonPath("$.alertas").isEmpty());

		verify(servicio).crear(new SocioRequest("Rosa Ficticia", "rosa@example.org"));
	}

	@Test
	void anInvalidEmailAndABlankNameAreRejected() throws Exception {
		mvc.perform(post("/api/v1/socios").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" \",\"email\":\"no-es-un-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("nombre: must not be blank")))
				.andExpect(jsonPath("$.message").value(containsString("email: must be a well-formed email address")));

		verifyNoInteractions(servicio);
	}

	@Test
	void aDuplicateEmailIs409() throws Exception {
		when(servicio.crear(any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

		mvc.perform(post("/api/v1/socios").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
	}

	@Test
	void theDetailCarriesScoreAndAlerts() throws Exception {
		when(servicio.obtener(3L)).thenReturn(socio(45, List.of(
				new Alerta(AlertaCodigo.PENDING_FINE, "The member has an unpaid fine."),
				new Alerta(AlertaCodigo.LOW_SCORE, "Score 45 is below 50: no new loans."))));

		mvc.perform(get("/api/v1/socios/3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.score").value(45))
				.andExpect(jsonPath("$.alertas.length()").value(2))
				.andExpect(jsonPath("$.alertas[0].code").value("PENDING_FINE"))
				.andExpect(jsonPath("$.alertas[1].code").value("LOW_SCORE"))
				.andExpect(jsonPath("$.alertas[1].message").value("Score 45 is below 50: no new loans."));
	}

	@Test
	void anUnknownMemberIs404() throws Exception {
		when(servicio.obtener(99L)).thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

		mvc.perform(get("/api/v1/socios/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void theListCarriesNoScore() throws Exception {
		when(servicio.listar(isNull(), eq(0), eq(20))).thenReturn(new PaginaDto<>(
				List.of(new SocioResumen(3L, "Rosa Ficticia", "rosa@example.org", true, LocalDate.of(2026, 10, 19))), 0, 20, 1, 1));

		mvc.perform(get("/api/v1/socios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].nombre").value("Rosa Ficticia"))
				.andExpect(jsonPath("$.items[0].score").doesNotExist())
				.andExpect(jsonPath("$.items[0].alertas").doesNotExist());
	}

	@Test
	void passesTheFilterAndTheValidatedPage() throws Exception {
		when(servicio.listar("rosa", 1, 50)).thenReturn(new PaginaDto<>(List.of(), 1, 50, 0, 0));

		mvc.perform(get("/api/v1/socios").param("q", "rosa").param("page", "1").param("size", "50")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/socios").param("size", "500")).andExpect(status().isBadRequest());

		verify(servicio).listar("rosa", 1, 50);
	}

	@Test
	void updatesAMember() throws Exception {
		when(servicio.actualizar(eq(3L), any())).thenReturn(socio(100, List.of()));

		mvc.perform(put("/api/v1/socios/3").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Rosa Ficticia"));
	}

	@Test
	void deactivatingAndReactivatingAnswerWithTheMember() throws Exception {
		when(servicio.desactivar(3L)).thenReturn(new SocioResponse(3L, "Rosa Ficticia", "rosa@example.org", false, LocalDate.of(2026, 10, 19), 100, List.of()));
		when(servicio.reactivar(3L)).thenReturn(socio(100, List.of()));

		mvc.perform(post("/api/v1/socios/3/desactivar")).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
		mvc.perform(post("/api/v1/socios/3/reactivar")).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void deactivatingAMemberWithActiveLoansIs409() throws Exception {
		when(servicio.desactivar(3L)).thenThrow(new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_LOANS));

		mvc.perform(post("/api/v1/socios/3/desactivar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVE_LOANS"));
	}

	@Test
	void deletesAMemberAnd409WhenThereIsHistory() throws Exception {
		mvc.perform(delete("/api/v1/socios/3")).andExpect(status().isNoContent());
		verify(servicio).eliminar(3L);

		doThrow(new BusinessException(ErrorCode.MEMBER_HAS_HISTORY)).when(servicio).eliminar(4L);
		mvc.perform(delete("/api/v1/socios/4"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MEMBER_HAS_HISTORY"));
	}

}
