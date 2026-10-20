package dev.portafolio.biblioteca.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole path: HTTP, controller, service, JPA and Oracle, with the real error handler. The
 * mocked slices prove the contract of each layer; this proves that they are wired together.
 */
@AutoConfigureMockMvc
class CatalogoApiTests extends BaseIntegracionTest {

	@Autowired
	private MockMvc mvc;

	private String crearLibro(String isbn, String titulo) throws Exception {
		String cuerpo = mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON)
						.content("{\"isbn\":\"" + isbn + "\",\"titulo\":\"" + titulo + "\",\"autor\":\"Autora Ficticia\",\"anioPublicacion\":2001}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1");
	}

	@Test
	void aBookLivesThroughCreateReadFilterUpdateAndDelete() throws Exception {
		String id = crearLibro("978-0-306-40615-7", "El Faro Imaginado");

		mvc.perform(get("/api/v1/libros/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isbn").value("9780306406157"));
		mvc.perform(get("/api/v1/libros").param("q", "FARO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].titulo").value("El Faro Imaginado"))
				.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/libros/" + id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"isbn\":\"9780306406157\",\"titulo\":\"El Faro Cambiado\",\"autor\":\"Autora Ficticia\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("El Faro Cambiado"));
		mvc.perform(delete("/api/v1/libros/" + id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/libros/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
	}

	@Test
	void theRulesOfTheCatalogComeOutAsTheDocumentedErrors() throws Exception {
		String id = crearLibro("9780306406157", "Con Copias");

		mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON)
						.content("{\"isbn\":\"978-0-306-40615-7\",\"titulo\":\"Otro\",\"autor\":\"A\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_ISBN"));
		mvc.perform(post("/api/v1/libros").contentType(MediaType.APPLICATION_JSON)
						.content("{\"isbn\":\"123\",\"titulo\":\"Corto\",\"autor\":\"A\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("isbn: must have 10 or 13 digits")));

		mvc.perform(post("/api/v1/libros/" + id + "/ejemplares").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"EJ-E2E-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.disponible").value(true));
		mvc.perform(delete("/api/v1/libros/" + id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BOOK_HAS_COPIES"));
	}

	@Test
	void aCopyCanBeRetiredAndRestoredOverHttp() throws Exception {
		String libro = crearLibro("9780306406157", "Para Ejemplares");
		String creado = mvc.perform(post("/api/v1/libros/" + libro + "/ejemplares").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"EJ-E2E-2\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String ejemplar = creado.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(post("/api/v1/ejemplares/" + ejemplar + "/baja"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false))
				.andExpect(jsonPath("$.disponible").value(false));
		mvc.perform(post("/api/v1/ejemplares/" + ejemplar + "/alta"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(true));
		mvc.perform(get("/api/v1/libros/" + libro + "/ejemplares"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].codigo").value("EJ-E2E-2"));
	}

	@Test
	void aMemberIsCreatedWithAPerfectScoreAndDuplicateEmailsAreRefused() throws Exception {
		mvc.perform(post("/api/v1/socios").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Rosa Ficticia\",\"email\":\"rosa.e2e@example.org\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.score").value(100))
				.andExpect(jsonPath("$.fechaRegistro").value("2026-10-19"))
				.andExpect(jsonPath("$.alertas").isEmpty());
		mvc.perform(post("/api/v1/socios").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otra\",\"email\":\"ROSA.E2E@example.org\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
		mvc.perform(get("/api/v1/socios").param("q", "ROSA.E2E"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].email").value("rosa.e2e@example.org"))
				.andExpect(jsonPath("$.items[0].score").doesNotExist());
	}

	@Test
	void unknownResourcesAndRoutesUseTheSameErrorShape() throws Exception {
		mvc.perform(get("/api/v1/socios/-5"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		mvc.perform(get("/api/v1/ejemplares/-5"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COPY_NOT_FOUND"));
		mvc.perform(get("/api/v1/nada"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
	}

}
