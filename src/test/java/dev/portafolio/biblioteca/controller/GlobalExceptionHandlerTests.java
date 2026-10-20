package dev.portafolio.biblioteca.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every failure must come out as {code, message} with the right status and without internals.
 * A throwaway controller provokes each kind of failure; no database is involved.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTests.ErrorProbe.class)
@Import({ GlobalExceptionHandlerTests.ErrorProbe.class, GlobalExceptionHandler.class })
@ActiveProfiles("test")
class GlobalExceptionHandlerTests {

	@Autowired
	private MockMvc mvc;

	@RestController
	@RequestMapping("/probe")
	static class ErrorProbe {

		record Body(@NotBlank String titulo, @Min(1) int cantidad) {
		}

		@GetMapping("/business/{code}")
		String business(@PathVariable ErrorCode code) {
			throw new BusinessException(code);
		}

		@GetMapping("/business-custom")
		String custom() {
			throw new BusinessException(ErrorCode.LOAN_LIMIT_REACHED, "The member already has 5 active loans.");
		}

		@PostMapping("/body")
		String body(@Valid @RequestBody Body body) {
			return "ok";
		}

		@GetMapping("/typed/{id}")
		String typed(@PathVariable Long id) {
			return "ok";
		}

		@GetMapping("/param")
		String param(@RequestParam int n) {
			return "ok";
		}

		@GetMapping("/page")
		String page(@RequestParam @Min(1) int page) {
			return "ok";
		}

		@GetMapping("/lock")
		String lock() {
			throw new CannotAcquireLockException("ORA-30006: resource busy; SELECT s FROM socio s FOR UPDATE WAIT 5");
		}

		@GetMapping("/constraint")
		String constraint() {
			throw new DataIntegrityViolationException("could not execute statement",
					new java.sql.SQLException("ORA-00001: unique constraint (BIBLIOTECA.UX_SOCIO_EMAIL) violated"));
		}

		@GetMapping("/unknown-constraint")
		String unknownConstraint() {
			throw new DataIntegrityViolationException("ORA-02290: check constraint (BIBLIOTECA.CK_SECRET_TABLE) violated");
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("password=hunter2 jdbc:oracle:thin:@//db:1521/XEPDB1");
		}

		@PostMapping("/post-only")
		String postOnly() {
			return "ok";
		}

		@PostMapping(value = "/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
		String jsonOnly(@RequestBody Body body) {
			return "ok";
		}

	}

	@ParameterizedTest
	@EnumSource(ErrorCode.class)
	void everyBusinessErrorUsesItsOwnStatusCodeAndMessage(ErrorCode code) throws Exception {
		mvc.perform(get("/probe/business/" + code.name()))
				.andExpect(status().is(code.status().value()))
				.andExpect(jsonPath("$.code").value(code.name()))
				.andExpect(jsonPath("$.message").value(code.message()));
	}

	@Test
	void aBusinessErrorCanCarryASpecificMessage() throws Exception {
		mvc.perform(get("/probe/business-custom"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("LOAN_LIMIT_REACHED"))
				.andExpect(jsonPath("$.message").value("The member already has 5 active loans."));
	}

	@Test
	void theBodyHasExactlyCodeAndMessage() throws Exception {
		mvc.perform(get("/probe/business/MEMBER_INACTIVE"))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$.code").exists())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void anInvalidBodyListsEveryProblemInEnglish() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"\",\"cantidad\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("titulo: must not be blank")))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cantidad: must be greater than or equal to 1")));
	}

	@Test
	void aMalformedOrMissingBodyIsAValidationError() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("The request body is missing or malformed."));
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void aWrongTypedPathVariableNamesTheParameter() throws Exception {
		mvc.perform(get("/probe/typed/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("The value of 'id' is not valid."));
	}

	@Test
	void aMissingRequiredParameterNamesIt() throws Exception {
		mvc.perform(get("/probe/param"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The parameter 'n' is required."));
	}

	@Test
	void aParameterConstraintIsReportedAsValidationError() throws Exception {
		mvc.perform(get("/probe/page").param("page", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("page: must be greater than or equal to 1")));
	}

	@Test
	void anUnknownRouteIsEndpointNotFound() throws Exception {
		mvc.perform(get("/does/not/exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
	}

	@Test
	void aWrongMethodIsMethodNotAllowed() throws Exception {
		mvc.perform(get("/probe/post-only"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void aWrongContentTypeIsUnsupportedMediaType() throws Exception {
		mvc.perform(post("/probe/json-only").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void aLockTimeoutIsAConflictThatHidesTheSql() throws Exception {
		String body = mvc.perform(get("/probe/lock"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("ORA-").doesNotContain("FOR UPDATE").doesNotContain("socio");
	}

	@Test
	void aKnownConstraintBecomesTheSameErrorTheCheckWouldHaveGiven() throws Exception {
		mvc.perform(get("/probe/constraint"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
				.andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_EMAIL.message()));
	}

	@Test
	void anUnknownConstraintIsAnInternalErrorWithoutDetails() throws Exception {
		String body = mvc.perform(get("/probe/unknown-constraint"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("CK_SECRET_TABLE").doesNotContain("ORA-");
	}

	@Test
	void anUnexpectedFailureIsGenericAndLeaksNothing() throws Exception {
		String body = mvc.perform(get("/probe/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("An unexpected error occurred."))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("hunter2").doesNotContain("jdbc").doesNotContain("IllegalStateException");
	}

}
