package dev.portafolio.biblioteca.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.dto.EjemplarRequest;
import dev.portafolio.biblioteca.dto.EjemplarResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.EjemplarService;
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
@WebMvcTest(EjemplarController.class)
@ActiveProfiles("test")
class EjemplarControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private EjemplarService servicio;

	private EjemplarResponse ejemplar() {
		return new EjemplarResponse(5L, 7L, "EJ-7-1", true, true);
	}

	@Test
	void createsACopyUnderABookAnd201PointsToTheCopyResource() throws Exception {
		when(servicio.crear(any(), any())).thenReturn(ejemplar());

		mvc.perform(post("/api/v1/libros/7/ejemplares").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"EJ-7-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/ejemplares/5")))
				.andExpect(jsonPath("$.id").value(5))
				.andExpect(jsonPath("$.libroId").value(7))
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(jsonPath("$.disponible").value(true));

		verify(servicio).crear(7L, new EjemplarRequest("EJ-7-1"));
	}

	@Test
	void aBlankCodeIsRejected() throws Exception {
		mvc.perform(post("/api/v1/libros/7/ejemplares").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("codigo: must not be blank")));

		verifyNoInteractions(servicio);
	}

	@Test
	void aCopyOfAnUnknownBookIs404() throws Exception {
		when(servicio.crear(any(), any())).thenThrow(new BusinessException(ErrorCode.BOOK_NOT_FOUND));

		mvc.perform(post("/api/v1/libros/99/ejemplares").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"X\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
	}

	@Test
	void readsACopy() throws Exception {
		when(servicio.obtener(5L)).thenReturn(ejemplar());

		mvc.perform(get("/api/v1/ejemplares/5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigo").value("EJ-7-1"));
	}

	@Test
	void listsTheCopiesOfABook() throws Exception {
		when(servicio.listarPorLibro(7L, 0, 20)).thenReturn(new PaginaDto<>(List.of(ejemplar()), 0, 20, 1, 1));

		mvc.perform(get("/api/v1/libros/7/ejemplares"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].codigo").value("EJ-7-1"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void retiringAndRestoringAnswerWithTheCopy() throws Exception {
		when(servicio.darDeBaja(5L)).thenReturn(new EjemplarResponse(5L, 7L, "EJ-7-1", false, false));
		when(servicio.reactivar(5L)).thenReturn(ejemplar());

		mvc.perform(post("/api/v1/ejemplares/5/baja"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false))
				.andExpect(jsonPath("$.disponible").value(false));
		mvc.perform(post("/api/v1/ejemplares/5/alta"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void retiringACopyOnLoanIs409() throws Exception {
		when(servicio.darDeBaja(5L)).thenThrow(new BusinessException(ErrorCode.COPY_HAS_ACTIVE_LOAN));

		mvc.perform(post("/api/v1/ejemplares/5/baja"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COPY_HAS_ACTIVE_LOAN"));
	}

	@Test
	void deletesACopy() throws Exception {
		mvc.perform(delete("/api/v1/ejemplares/5")).andExpect(status().isNoContent());

		verify(servicio).eliminar(5L);
	}

	@Test
	void deletingACopyWithHistoryIs409() throws Exception {
		doThrow(new BusinessException(ErrorCode.COPY_HAS_HISTORY)).when(servicio).eliminar(5L);

		mvc.perform(delete("/api/v1/ejemplares/5"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COPY_HAS_HISTORY"));
	}

	@Test
	void aGetOnTheActionEndpointIsMethodNotAllowed() throws Exception {
		mvc.perform(get("/api/v1/ejemplares/5/baja"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

}
