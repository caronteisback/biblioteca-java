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

import dev.portafolio.biblioteca.dto.LibroRequest;
import dev.portafolio.biblioteca.dto.LibroResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.LibroService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract of /api/v1/libros: statuses, JSON, validation. The service is replaced by a mock. */
@WebMvcTest(LibroController.class)
@ActiveProfiles("test")
class LibroControllerTests {

	private static final String VALIDO = "{\"isbn\":\"978-0-306-40615-7\",\"titulo\":\"Libro Uno\",\"autor\":\"Autora Ficticia\",\"anioPublicacion\":1999}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LibroService servicio;

	private LibroResponse libro() {
		return new LibroResponse(7L, "9780306406157", "Libro Uno", "Autora Ficticia", 1999);
	}

	@Test
	void createsAndAnswers201WithLocationAndBody() throws Exception {
		when(servicio.crear(any())).thenReturn(libro());

		mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/libros/7")))
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.isbn").value("9780306406157"))
				.andExpect(jsonPath("$.titulo").value("Libro Uno"));

		verify(servicio).crear(new LibroRequest("978-0-306-40615-7", "Libro Uno", "Autora Ficticia", 1999));
	}

	@Test
	void anInvalidBodyIsRejectedBeforeReachingTheService() throws Exception {
		mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON)
						.content("{\"isbn\":\"\",\"titulo\":\"   \",\"autor\":\"A\",\"anioPublicacion\":3000}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("isbn: must not be blank")))
				.andExpect(jsonPath("$.message").value(containsString("titulo: must not be blank")))
				.andExpect(jsonPath("$.message").value(containsString("anioPublicacion: must be less than or equal to 2100")));

		verifyNoInteractions(servicio);
	}

	@Test
	void aMissingFieldIsRejected() throws Exception {
		mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Solo titulo\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("isbn: must not be blank")))
				.andExpect(jsonPath("$.message").value(containsString("autor: must not be blank")));
	}

	@Test
	void aBusinessErrorFromTheServiceBecomesItsHttpStatus() throws Exception {
		when(servicio.crear(any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_ISBN));

		mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_ISBN"))
				.andExpect(jsonPath("$.message").value("A book with that ISBN already exists."));
	}

	@Test
	void readsABookById() throws Exception {
		when(servicio.obtener(7L)).thenReturn(libro());

		mvc.perform(get("/api/v1/libros/7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.autor").value("Autora Ficticia"))
				.andExpect(jsonPath("$.anioPublicacion").value(1999));
	}

	@Test
	void anUnknownBookIs404() throws Exception {
		when(servicio.obtener(99L)).thenThrow(new BusinessException(ErrorCode.BOOK_NOT_FOUND));

		mvc.perform(get("/api/v1/libros/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
	}

	@Test
	void anIdThatIsNotANumberIs400() throws Exception {
		mvc.perform(get("/api/v1/libros/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The value of 'id' is not valid."));
	}

	@Test
	void listsWithTheDefaultPageAndNoFilter() throws Exception {
		when(servicio.listar(isNull(), eq(0), eq(20))).thenReturn(new PaginaDto<>(List.of(libro()), 0, 20, 1, 1));

		mvc.perform(get("/api/v1/libros"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(7))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void passesTheFilterAndThePageToTheService() throws Exception {
		when(servicio.listar("faro", 2, 5)).thenReturn(new PaginaDto<>(List.of(), 2, 5, 11, 3));

		mvc.perform(get("/api/v1/libros").param("q", "faro").param("page", "2").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.totalPages").value(3));

		verify(servicio).listar("faro", 2, 5);
	}

	@Test
	void pageParametersOutOfRangeAreRejected() throws Exception {
		mvc.perform(get("/api/v1/libros").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("size: must be less than or equal to 100")));
		mvc.perform(get("/api/v1/libros").param("size", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/libros").param("page", "-1")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/libros").param("page", "abc")).andExpect(status().isBadRequest());

		verifyNoInteractions(servicio);
	}

	@Test
	void aSortParameterIsIgnoredNotObeyed() throws Exception {
		when(servicio.listar(isNull(), eq(0), eq(20))).thenReturn(new PaginaDto<>(List.of(), 0, 20, 0, 0));

		mvc.perform(get("/api/v1/libros").param("sort", "passwordHash,desc")).andExpect(status().isOk());
	}

	@Test
	void updatesABook() throws Exception {
		when(servicio.actualizar(eq(7L), any())).thenReturn(libro());

		mvc.perform(put("/api/v1/libros/7").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7));
	}

	@Test
	void deletesAndAnswers204WithoutBody() throws Exception {
		mvc.perform(delete("/api/v1/libros/7")).andExpect(status().isNoContent());

		verify(servicio).eliminar(7L);
	}

	@Test
	void deletingABookWithCopiesIs409() throws Exception {
		doThrow(new BusinessException(ErrorCode.BOOK_HAS_COPIES)).when(servicio).eliminar(7L);

		mvc.perform(delete("/api/v1/libros/7"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BOOK_HAS_COPIES"));
	}

}
