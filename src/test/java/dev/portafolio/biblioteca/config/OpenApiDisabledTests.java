package dev.portafolio.biblioteca.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Without the dev profile (and without the test properties that turn them on) the API description
 * and Swagger UI do not exist. A real deployment must not publish them by accident.
 */
@AutoConfigureMockMvc
class OpenApiDisabledTests extends BaseIntegracionTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void theApiDescriptionIsNotPublishedByDefault() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
		mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUiIsNotPublishedByDefault() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

}
