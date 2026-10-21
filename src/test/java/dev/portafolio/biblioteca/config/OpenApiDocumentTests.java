package dev.portafolio.biblioteca.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.exception.ErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI description generated from the code, checked in four ways: it is a valid document, it
 * lists exactly the operations the API has, each one carries what a client needs (success status,
 * documented errors, required fields), and it matches the copy committed in {@code docs/openapi.yaml}.
 *
 * <p>When the API changes on purpose, refresh the committed copy with
 * {@code ./mvnw test -Dtest=OpenApiDocumentTests -Dopenapi.update=true} and review the diff.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
class OpenApiDocumentTests extends BaseIntegracionTest {

	private static final Path ARCHIVO = Path.of("docs", "openapi.yaml");

	private static final Set<String> OPERACIONES = new TreeSet<>(List.of(
			"GET /api/v1/libros", "POST /api/v1/libros",
			"GET /api/v1/libros/{id}", "PUT /api/v1/libros/{id}", "DELETE /api/v1/libros/{id}",
			"POST /api/v1/libros/{libroId}/ejemplares", "GET /api/v1/libros/{libroId}/ejemplares",
			"GET /api/v1/ejemplares/{id}", "DELETE /api/v1/ejemplares/{id}",
			"POST /api/v1/ejemplares/{id}/baja", "POST /api/v1/ejemplares/{id}/alta",
			"GET /api/v1/socios", "POST /api/v1/socios",
			"GET /api/v1/socios/{id}", "PUT /api/v1/socios/{id}", "DELETE /api/v1/socios/{id}",
			"POST /api/v1/socios/{id}/desactivar", "POST /api/v1/socios/{id}/reactivar",
			"GET /api/v1/prestamos", "POST /api/v1/prestamos", "GET /api/v1/prestamos/{id}",
			"POST /api/v1/prestamos/{id}/renovacion", "POST /api/v1/prestamos/{id}/devolucion",
			"GET /api/v1/multas", "GET /api/v1/multas/{id}", "POST /api/v1/multas/{id}/pago"));

	private static final Set<String> ETIQUETAS = Set.of("Books", "Copies", "Members", "Loans", "Fines");

	@Autowired
	private MockMvc mvc;

	private String generado() throws Exception {
		return mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static String normalizado(String texto) {
		return texto.replace("\r\n", "\n");
	}

	private OpenAPI documento() throws Exception {
		ParseOptions opciones = new ParseOptions();
		opciones.setResolve(true);
		SwaggerParseResult resultado = new OpenAPIV3Parser().readContents(generado(), null, opciones);
		assertThat(resultado.getMessages()).as("messages of the OpenAPI parser").isEmpty();
		return resultado.getOpenAPI();
	}

	private Map<String, Operation> operaciones(OpenAPI api) {
		Map<String, Operation> mapa = new LinkedHashMap<>();
		api.getPaths().forEach((ruta, item) -> item.readOperationsMap().forEach((metodo, operacion) -> mapa.put(metodo + " " + ruta, operacion)));
		return mapa;
	}

	@Test
	void theGeneratedDocumentIsAValidOpenApiDocument() throws Exception {
		OpenAPI api = documento();

		assertThat(api.getOpenapi()).startsWith("3.");
		assertThat(api.getInfo().getTitle()).isEqualTo("Biblioteca API");
		assertThat(api.getInfo().getVersion()).isEqualTo(OpenApiConfig.API_VERSION);
		assertThat(api.getInfo().getLicense().getName()).isEqualTo("MIT");
	}

	@Test
	void itListsExactlyTheOperationsTheApiHas() throws Exception {
		assertThat(new TreeSet<>(operaciones(documento()).keySet())).isEqualTo(OPERACIONES);
	}

	@Test
	void everyOperationHasASummaryAndOneKnownTag() throws Exception {
		operaciones(documento()).forEach((nombre, operacion) -> {
			assertThat(operacion.getSummary()).as("summary of %s", nombre).isNotBlank();
			assertThat(operacion.getTags()).as("tags of %s", nombre).hasSize(1).allMatch(ETIQUETAS::contains);
		});
	}

	@Test
	void everyOperationHasAStableUniqueIdThatNamesWhatItDoes() throws Exception {
		Map<String, Operation> operaciones = operaciones(documento());

		assertThat(operaciones.values().stream().map(Operation::getOperationId).toList())
				.doesNotContainNull()
				.doesNotHaveDuplicates()
				.allMatch(id -> id.matches("[a-z]+([A-Z][a-z]+)+"), "camelCase verb and noun, with no numeric suffix");
		assertThat(operaciones.get("POST /api/v1/prestamos").getOperationId()).isEqualTo("createLoan");
		assertThat(operaciones.get("POST /api/v1/prestamos/{id}/renovacion").getOperationId()).isEqualTo("renewLoan");
		assertThat(operaciones.get("POST /api/v1/prestamos/{id}/devolucion").getOperationId()).isEqualTo("returnLoan");
		assertThat(operaciones.get("POST /api/v1/multas/{id}/pago").getOperationId()).isEqualTo("payFine");
	}

	@Test
	void errorExamplesAreSharedComponentsNotCopiesInEveryOperation() throws Exception {
		OpenAPI api = documento();

		assertThat(api.getComponents().getExamples().keySet())
				.containsExactlyInAnyOrderElementsOf(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
		assertThat(generado()).doesNotContain("message: The member does not exist.\n              schema");
	}

	@Test
	void creationsAnswer201WithLocationAndDeletesAnswer204() throws Exception {
		operaciones(documento()).forEach((nombre, operacion) -> {
			Map<String, ApiResponse> respuestas = operacion.getResponses();
			boolean esCreacion = nombre.equals("POST /api/v1/libros") || nombre.equals("POST /api/v1/socios")
					|| nombre.equals("POST /api/v1/prestamos") || nombre.equals("POST /api/v1/libros/{libroId}/ejemplares");
			boolean esBorrado = nombre.startsWith("DELETE ");
			if (esCreacion) {
				assertThat(respuestas).as(nombre).containsKey("201").doesNotContainKey("200");
				assertThat(respuestas.get("201").getHeaders()).as("Location of %s", nombre).containsKey("Location");
			} else if (esBorrado) {
				assertThat(respuestas).as(nombre).containsKey("204").doesNotContainKey("200");
			} else {
				assertThat(respuestas).as(nombre).containsKey("200");
			}
		});
	}

	@Test
	void everyErrorResponseUsesTheErrorSchemaAndEveryOperationDocumentsTheUnexpected() throws Exception {
		operaciones(documento()).forEach((nombre, operacion) -> {
			assertThat(operacion.getResponses()).as("500 of %s", nombre).containsKey("500");
			operacion.getResponses().forEach((estado, respuesta) -> {
				if (Integer.parseInt(estado) >= 400) {
					Schema<?> esquema = respuesta.getContent().get("application/json").getSchema();
					assertThat(esquema.get$ref()).as("%s %s", nombre, estado).isEqualTo("#/components/schemas/ErrorResponse");
				}
			});
		});
	}

	@Test
	void operationsWithABodyDocumentTheValidationAndTheMediaTypeErrors() throws Exception {
		operaciones(documento()).forEach((nombre, operacion) -> {
			if (operacion.getRequestBody() != null) {
				assertThat(operacion.getResponses()).as(nombre).containsKeys("400", "415");
			}
		});
	}

	@Test
	void theBusinessErrorsOfTheMainOperationsAreListedByCode() throws Exception {
		Map<String, Operation> operaciones = operaciones(documento());

		String prestar422 = operaciones.get("POST /api/v1/prestamos").getResponses().get("422").getDescription();
		for (ErrorCode codigo : List.of(ErrorCode.MEMBER_INACTIVE, ErrorCode.COPY_INACTIVE, ErrorCode.MEMBER_HAS_OVERDUE_LOAN,
				ErrorCode.MEMBER_HAS_PENDING_FINE, ErrorCode.MEMBER_BLOCKED_BY_SCORE, ErrorCode.LOAN_LIMIT_REACHED)) {
			assertThat(prestar422).contains(codigo.name());
		}
		assertThat(operaciones.get("POST /api/v1/prestamos").getResponses().get("409").getDescription())
				.contains("COPY_ALREADY_ON_LOAN", "CONCURRENT_UPDATE");
		assertThat(operaciones.get("POST /api/v1/prestamos").getResponses().get("404").getDescription())
				.contains("MEMBER_NOT_FOUND", "COPY_NOT_FOUND");
		assertThat(operaciones.get("POST /api/v1/prestamos/{id}/renovacion").getResponses().get("422").getDescription())
				.contains("RENEWAL_LIMIT_REACHED", "RENEWAL_NOT_ALLOWED_OVERDUE", "MEMBER_INACTIVE");
		assertThat(operaciones.get("POST /api/v1/multas/{id}/pago").getResponses().get("409").getDescription()).contains("FINE_ALREADY_PAID");
		assertThat(operaciones.get("DELETE /api/v1/libros/{id}").getResponses().get("409").getDescription()).contains("BOOK_HAS_COPIES");
		assertThat(operaciones.get("POST /api/v1/socios").getResponses().get("409").getDescription()).contains("DUPLICATE_EMAIL");
	}

	@Test
	void everyErrorCodeOfTheApiIsInTheErrorSchema() throws Exception {
		Schema<?> error = documento().getComponents().getSchemas().get("ErrorResponse");
		@SuppressWarnings("unchecked")
		Schema<String> codigo = (Schema<String>) error.getProperties().get("code");

		assertThat(codigo.getEnum()).containsExactlyInAnyOrderElementsOf(
				Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
		assertThat(error.getRequired()).containsExactlyInAnyOrder("code", "message");
		assertThat(error.getTypes()).containsExactly("object");
	}

	@Test
	void answersRequireEveryFieldThatCannotBeNull() throws Exception {
		Map<String, Schema> esquemas = documento().getComponents().getSchemas();

		assertThat(esquemas.get("LibroResponse").getRequired()).containsExactlyInAnyOrder("id", "isbn", "titulo", "autor");
		assertThat(esquemas.get("MultaResponse").getRequired())
				.contains("id", "prestamoId", "diasRetraso", "monto", "estado", "fechaGeneracion").doesNotContain("fechaPago");
		assertThat(esquemas.get("PrestamoResponse").getRequired())
				.contains("id", "ejemplarId", "socioId", "fechaPrestamo", "fechaVencimiento", "renovaciones", "activo", "diasRetraso", "alertas")
				.doesNotContain("fechaDevolucion", "multa");
		assertThat(esquemas.get("SocioResponse").getRequired()).contains("score", "alertas");
	}

	@Test
	void anOptionalObjectIsOneOfTheObjectOrNullNeverNullAndTheObjectAtTheSameTime() throws Exception {
		Schema<?> multa = (Schema<?>) documento().getComponents().getSchemas().get("PrestamoResponse").getProperties().get("multa");

		assertThat(multa.getTypes()).as("a type next to the reference would make the field impossible to fill").isNullOrEmpty();
		assertThat(multa.getOneOf()).hasSize(2);
		assertThat(multa.getOneOf().get(0).get$ref()).isEqualTo("#/components/schemas/MultaResponse");
		assertThat(multa.getOneOf().get(1).getTypes()).containsExactly("null");
		assertThat(generado()).doesNotContainPattern("type: \"null\"\\s*\\n\\s*\\$ref");
	}

	@Test
	void requestsRequireOnlyWhatValidationRequires() throws Exception {
		Map<String, Schema> esquemas = documento().getComponents().getSchemas();

		assertThat(esquemas.get("LibroRequest").getRequired()).containsExactlyInAnyOrder("isbn", "titulo", "autor");
		assertThat(esquemas.get("SocioRequest").getRequired()).containsExactlyInAnyOrder("nombre", "email");
		assertThat(esquemas.get("PrestamoRequest").getRequired()).containsExactlyInAnyOrder("socioId", "ejemplarId");
		assertThat(esquemas.get("EjemplarRequest").getRequired()).containsExactly("codigo");
	}

	@Test
	void theCommittedCopyMatchesWhatTheCodeGenerates() throws Exception {
		String actual = normalizado(generado());
		if (Boolean.getBoolean("openapi.update")) {
			Files.createDirectories(ARCHIVO.getParent());
			Files.writeString(ARCHIVO, actual, StandardCharsets.UTF_8);
		}
		assertThat(ARCHIVO).as("docs/openapi.yaml must exist: run with -Dopenapi.update=true").exists();
		String guardado = normalizado(readAll());
		assertThat(actual)
				.as("docs/openapi.yaml is out of date. Refresh it with: ./mvnw test -Dtest=OpenApiDocumentTests -Dopenapi.update=true")
				.isEqualTo(guardado);
	}

	@Test
	void swaggerUiIsServedWhenItIsEnabled() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	private String readAll() throws IOException {
		return Files.readString(ARCHIVO, StandardCharsets.UTF_8);
	}

}
