package dev.portafolio.biblioteca.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.config.BibliotecaConfig;
import dev.portafolio.biblioteca.config.CorsConfig;
import dev.portafolio.biblioteca.dto.LibroResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.service.LibroService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the browser asks before calling the API from another origin. The default origins are the
 * local development servers listed in application.yml.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(LibroController.class)
@Import({ BibliotecaConfig.class, CorsConfig.class })
@ActiveProfiles("test")
class CorsTests {

	private static final String PERMITIDO = "http://localhost:5500";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LibroService servicio;

	@Test
	void aPreflightFromAnAllowedOriginIsAccepted() throws Exception {
		mvc.perform(options("/api/v1/libros")
						.header("Origin", PERMITIDO)
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", PERMITIDO))
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("content-type")))
				.andExpect(header().string("Access-Control-Max-Age", "3600"));
	}

	@Test
	void everyDefaultLocalOriginIsAllowed() throws Exception {
		for (String origen : List.of("http://localhost:5500", "http://127.0.0.1:5500", "http://localhost:3000", "http://localhost:8000",
				"http://localhost:5173", "http://127.0.0.1:5173")) {
			mvc.perform(options("/api/v1/libros").header("Origin", origen).header("Access-Control-Request-Method", "GET"))
					.andExpect(status().isOk())
					.andExpect(header().string("Access-Control-Allow-Origin", origen));
		}
	}

	@Test
	void aPreflightFromAnotherOriginIsRefused() throws Exception {
		mvc.perform(options("/api/v1/libros")
						.header("Origin", "https://sitio-malicioso.example")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void aSimpleGetAnswersWithTheAllowedOriginHeader() throws Exception {
		when(servicio.listar(null, 0, 20)).thenReturn(new PaginaDto<>(List.of(), 0, 20, 0, 0));

		mvc.perform(get("/api/v1/libros").header("Origin", PERMITIDO))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", PERMITIDO));
	}

	@Test
	void theFrontendCanReadTheLocationHeaderOfACreatedResource() throws Exception {
		when(servicio.crear(org.mockito.ArgumentMatchers.any())).thenReturn(new LibroResponse(7L, "9780306406157", "Libro", "Autora", null));

		mvc.perform(post("/api/v1/libros").header("Origin", PERMITIDO).contentType(MediaType.APPLICATION_JSON)
						.content("{\"isbn\":\"9780306406157\",\"titulo\":\"Libro\",\"autor\":\"Autora\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Access-Control-Expose-Headers", containsString("Location")));
	}

	@Test
	void aRequestWithoutOriginGetsNoCorsHeaders() throws Exception {
		when(servicio.listar(null, 0, 20)).thenReturn(new PaginaDto<>(List.of(), 0, 20, 0, 0));

		String cabecera = mvc.perform(get("/api/v1/libros")).andExpect(status().isOk())
				.andReturn().getResponse().getHeader("Access-Control-Allow-Origin");

		assertThat(cabecera).isNull();
	}

	@Test
	void theCorsRuleCoversOnlyTheApiPaths() throws Exception {
		mvc.perform(options("/otra/ruta").header("Origin", PERMITIDO).header("Access-Control-Request-Method", "GET"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}
