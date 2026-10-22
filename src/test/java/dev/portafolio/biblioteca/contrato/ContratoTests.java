package dev.portafolio.biblioteca.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.Error;
import dev.portafolio.biblioteca.BaseApiSegura;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.entity.Usuario;
import dev.portafolio.biblioteca.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The API against the contract of biblioteca-contrato, in the pinned copy under src/test/resources/contrato.
 *
 * <p>The contract and the code are written separately, so each can be wrong. These tests call the real
 * API (real services, real Oracle, real error handler) and check, for every call:
 * <ul>
 * <li>the operation exists in the contract, with that method and that path;
 * <li>the status is one the contract lists for the operation, and the body has the shape of its schema,
 * with no field the contract does not name;
 * <li>an error carries one of the codes the contract lists for that status, and a 201 carries the headers it lists;
 * <li>the bodies sent are valid against the request schema of the contract;
 * <li>every operation allows exactly the roles the contract declares, and refuses the others with the documented status.
 * </ul>
 * The calls carry a real token from the real login, as a client would.
 * At the end, every operation of the contract must have been called with its success status, so adding an
 * operation without a scenario here fails the build.
 */
@AutoConfigureMockMvc
class ContratoTests extends BaseApiSegura {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final ContratoVigente CONTRATO = new ContratoVigente();
	private static final Set<String> LLAMADAS = new LinkedHashSet<>();

	/** The token the next calls carry; null means no sign-in. Every test starts as a librarian. */
	private String tokenActual;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	@BeforeEach
	void entrarComoBibliotecario() throws Exception {
		tokenActual = token(bibliotecario());
	}

	// ---- the pinned copy itself

	@Test
	void thePinnedCopyIsExactlyTheTaggedVersionItClaimsToBe() {
		var propiedades = ContratoVigente.propiedades();

		assertThat(CONTRATO.sha256())
				.as("openapi.yaml changed without updating contrato.properties: copy a tagged version of biblioteca-contrato")
				.isEqualTo(propiedades.getProperty("sha256"));
		assertThat(propiedades.getProperty("tag")).isEqualTo("v" + CONTRATO.documento().at("/info/version").asText());
	}

	@Test
	void theContractAndTheCodeHaveExactlyTheSameOperations() {
		Set<String> delContrato = new TreeSet<>();
		CONTRATO.documento().get("paths").properties().forEach(ruta -> ruta.getValue().properties()
				.forEach(metodo -> delContrato.add(metodo.getKey().toUpperCase(Locale.ROOT) + " " + ruta.getKey())));

		Set<String> delCodigo = new TreeSet<>();
		for (RequestMappingInfo info : mapeos.getHandlerMethods().keySet()) {
			for (String patron : info.getPathPatternsCondition().getPatternValues()) {
				if (patron.startsWith("/api/")) {
					info.getMethodsCondition().getMethods().forEach(metodo -> delCodigo.add(metodo.name() + " " + patron));
				}
			}
		}

		assertThat(delCodigo).containsExactlyElementsOf(delContrato);
	}

	// ---- scenarios: each call is checked against the contract

	@Test
	void booksAndCopiesFollowTheContract() throws Exception {
		String libro = "{\"isbn\":\"978-0-306-40615-7\",\"titulo\":\"El Faro Imaginado\",\"autor\":\"Autora Ficticia\",\"anioPublicacion\":2001}";
		long libroId = id(llamar(HttpMethod.POST, "/api/v1/libros", libro, 201));
		llamar(HttpMethod.POST, "/api/v1/libros", "{\"isbn\":\"9780306406157\",\"titulo\":\"Otro\",\"autor\":\"Autora Ficticia\"}", 409);
		llamar(HttpMethod.GET, "/api/v1/libros", null, 200, "q", "faro", "size", "5");
		llamar(HttpMethod.GET, "/api/v1/libros/" + libroId, null, 200);
		llamar(HttpMethod.PUT, "/api/v1/libros/" + libroId,
				"{\"isbn\":\"978-0-306-40615-7\",\"titulo\":\"El Faro Reimaginado\",\"autor\":\"Autora Ficticia\"}", 200);

		long ejemplarId = id(llamar(HttpMethod.POST, "/api/v1/libros/" + libroId + "/ejemplares", "{\"codigo\":\"EJ-C1\"}", 201));
		long sobrante = id(llamar(HttpMethod.POST, "/api/v1/libros/" + libroId + "/ejemplares", "{\"codigo\":\"EJ-C2\"}", 201));
		llamar(HttpMethod.GET, "/api/v1/libros/" + libroId + "/ejemplares", null, 200);
		llamar(HttpMethod.GET, "/api/v1/ejemplares/" + ejemplarId, null, 200);
		llamar(HttpMethod.POST, "/api/v1/ejemplares/" + ejemplarId + "/baja", null, 200);
		llamar(HttpMethod.POST, "/api/v1/ejemplares/" + ejemplarId + "/alta", null, 200);
		llamar(HttpMethod.DELETE, "/api/v1/ejemplares/" + sobrante, null, 204);

		long sinEjemplares = id(llamar(HttpMethod.POST, "/api/v1/libros",
				"{\"isbn\":\"978-3-16-148410-0\",\"titulo\":\"Sin ejemplares\",\"autor\":\"Autora Ficticia\"}", 201));
		llamar(HttpMethod.DELETE, "/api/v1/libros/" + sinEjemplares, null, 204);
		llamar(HttpMethod.DELETE, "/api/v1/libros/" + libroId, null, 409);
		llamar(HttpMethod.GET, "/api/v1/libros/999999", null, 404);
		llamar(HttpMethod.GET, "/api/v1/ejemplares/999999", null, 404);
	}

	@Test
	void membersFollowTheContract() throws Exception {
		long socioId = id(llamar(HttpMethod.POST, "/api/v1/socios", "{\"nombre\":\"Rosa Ficticia\",\"email\":\"rosa@example.org\"}", 201));
		llamar(HttpMethod.POST, "/api/v1/socios", "{\"nombre\":\"Otra Rosa\",\"email\":\"rosa@example.org\"}", 409);
		llamar(HttpMethod.GET, "/api/v1/socios/" + socioId, null, 200);
		llamar(HttpMethod.GET, "/api/v1/socios", null, 200, "q", "rosa");
		llamar(HttpMethod.PUT, "/api/v1/socios/" + socioId, "{\"nombre\":\"Rosa Ficticia Ruiz\",\"email\":\"rosa@example.org\"}", 200);
		llamar(HttpMethod.POST, "/api/v1/socios/" + socioId + "/desactivar", null, 200);
		llamar(HttpMethod.POST, "/api/v1/socios/" + socioId + "/reactivar", null, 200);
		llamar(HttpMethod.DELETE, "/api/v1/socios/" + socioId, null, 204);
		llamar(HttpMethod.GET, "/api/v1/socios/999999", null, 404);
	}

	@Test
	void loansAndFinesFollowTheContract() throws Exception {
		Socio socio = socio();
		var ejemplar = ejemplar();
		String nuevo = "{\"socioId\":" + socio.getId() + ",\"ejemplarId\":%d}";

		long prestamoId = id(llamar(HttpMethod.POST, "/api/v1/prestamos", nuevo.formatted(ejemplar.getId()), 201));
		llamar(HttpMethod.POST, "/api/v1/prestamos", nuevo.formatted(ejemplar.getId()), 409);
		llamar(HttpMethod.GET, "/api/v1/prestamos/" + prestamoId, null, 200);
		llamar(HttpMethod.GET, "/api/v1/prestamos", null, 200, "socioId", String.valueOf(socio.getId()), "activo", "true");
		llamar(HttpMethod.POST, "/api/v1/prestamos/" + prestamoId + "/renovacion", null, 200);
		llamar(HttpMethod.POST, "/api/v1/prestamos/" + prestamoId + "/devolucion", null, 200);
		llamar(HttpMethod.POST, "/api/v1/prestamos/" + prestamoId + "/devolucion", null, 409);

		// A loan that is 5 days late: its return creates the fine that the rest of the scenario pays.
		Prestamo vencido = prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(19), 14));
		llamar(HttpMethod.GET, "/api/v1/prestamos/" + vencido.getId(), null, 200);
		llamar(HttpMethod.POST, "/api/v1/prestamos", nuevo.formatted(ejemplar().getId()), 422);
		MvcResult devuelto = llamar(HttpMethod.POST, "/api/v1/prestamos/" + vencido.getId() + "/devolucion", null, 200);
		long multaId = ((Number) JsonPath.read(devuelto.getResponse().getContentAsString(), "$.multa.id")).longValue();

		llamar(HttpMethod.GET, "/api/v1/multas/" + multaId, null, 200);
		llamar(HttpMethod.GET, "/api/v1/multas", null, 200, "socioId", String.valueOf(socio.getId()), "estado", "PENDING");
		llamar(HttpMethod.GET, "/api/v1/socios/" + socio.getId(), null, 200);
		llamar(HttpMethod.POST, "/api/v1/multas/" + multaId + "/pago", null, 200);
		llamar(HttpMethod.POST, "/api/v1/multas/" + multaId + "/pago", null, 409);
		llamar(HttpMethod.GET, "/api/v1/prestamos/999999", null, 404);
		llamar(HttpMethod.GET, "/api/v1/multas/999999", null, 404);
	}

	@Test
	void theDashboardFollowsTheContract() throws Exception {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(19), 14));
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY, 14));
		var devuelto = prestamo(ejemplar(), socio, HOY.minusDays(9), 14);
		devuelto.devolver(HOY.minusDays(5));
		prestamos.saveAndFlush(devuelto);

		llamar(HttpMethod.GET, "/api/v1/panel", null, 200);

		tokenActual = token(cuentaDe(socio));
		llamarRefusado(HttpMethod.GET, "/api/v1/panel", "/api/v1/panel", null, 403);
	}

	@Test
	void everyKindOfBadRequestUsesTheDocumentedErrorShape() throws Exception {
		llamarInvalido(HttpMethod.POST, "/api/v1/prestamos", "{}", 400);
		llamarInvalido(HttpMethod.POST, "/api/v1/libros", "{\"isbn\":\"\",\"titulo\":\"\",\"autor\":\"\"}", 400);
		llamar(HttpMethod.GET, "/api/v1/libros", null, 400, "size", "0");
		llamar(HttpMethod.GET, "/api/v1/prestamos/abc", null, 400);

		MvcResult tipo = mvc.perform(MockMvcRequestBuilders.post("/api/v1/libros").header("Authorization", bearer(tokenActual)).contentType(MediaType.TEXT_PLAIN).content("texto"))
				.andReturn();
		assertThat(tipo.getResponse().getStatus()).isEqualTo(415);
		verificarError(tipo, "/api/v1/libros", HttpMethod.POST);

		// These two can answer any URL, so no operation lists them: only the shape of the answer is checked.
		MvcResult noExiste = mvc.perform(MockMvcRequestBuilders.get("/api/v1/no-existe").header("Authorization", bearer(tokenActual))).andReturn();
		assertThat(noExiste.getResponse().getStatus()).isEqualTo(404);
		validarErrorGenerico(noExiste, "ENDPOINT_NOT_FOUND");
		MvcResult metodo = mvc.perform(MockMvcRequestBuilders.patch("/api/v1/libros/1").header("Authorization", bearer(tokenActual))).andReturn();
		assertThat(metodo.getResponse().getStatus()).isEqualTo(405);
		validarErrorGenerico(metodo, "METHOD_NOT_ALLOWED");
	}

	@Test
	void signingInFollowsTheContract() throws Exception {
		Usuario bibliotecario = bibliotecario();
		tokenActual = null;
		String credenciales = "{\"email\":\"%s\",\"password\":\"%s\"}";

		llamar(HttpMethod.POST, "/api/v1/auth/login", credenciales.formatted(bibliotecario.getEmail(), PASSWORD), 200);
		llamar(HttpMethod.POST, "/api/v1/auth/login", credenciales.formatted(bibliotecario.getEmail(), "wrong-password-123"), 401);
		llamarInvalido(HttpMethod.POST, "/api/v1/auth/login", "{}", 400);

		String bloqueado = "bloqueado" + System.nanoTime() + "@example.org";
		for (int i = 0; i < 5; i++) {
			llamar(HttpMethod.POST, "/api/v1/auth/login", credenciales.formatted(bloqueado, "wrong-password-123"), 401);
		}
		llamar(HttpMethod.POST, "/api/v1/auth/login", credenciales.formatted(bloqueado, PASSWORD), 429);
	}

	@Test
	void theAccountOfAMemberFollowsTheContract() throws Exception {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY, 14));
		tokenActual = token(cuentaDe(socio));

		llamar(HttpMethod.GET, "/api/v1/me", null, 200);
		llamar(HttpMethod.GET, "/api/v1/me/prestamos", null, 200, "activo", "true", "size", "5");
		llamar(HttpMethod.GET, "/api/v1/me/multas", null, 200, "estado", "PENDING");
		llamar(HttpMethod.GET, "/api/v1/libros", null, 200);
		llamarRefusado(HttpMethod.GET, "/api/v1/socios", "/api/v1/socios", null, 403);
		llamarRefusado(HttpMethod.DELETE, "/api/v1/libros/{id}", "/api/v1/libros/1", null, 403);

		tokenActual = token(bibliotecario());
		llamar(HttpMethod.GET, "/api/v1/me", null, 200);
		llamarRefusado(HttpMethod.GET, "/api/v1/me/prestamos", "/api/v1/me/prestamos", null, 403);
	}

	@Test
	void withoutATokenEveryOperationAnswersWithTheDocumented401() throws Exception {
		tokenActual = null;

		llamarRefusado(HttpMethod.GET, "/api/v1/libros", "/api/v1/libros", null, 401);
		llamarRefusado(HttpMethod.POST, "/api/v1/prestamos", "/api/v1/prestamos", "{}", 401);
		llamarRefusado(HttpMethod.GET, "/api/v1/me", "/api/v1/me", null, 401);
	}

	@Test
	void everyOperationAllowsExactlyTheRolesTheContractDeclares() throws Exception {
		String deBibliotecario = tokenActual;
		String deSocio = token(cuentaDe(socio()));
		List<String> problemas = new ArrayList<>();
		int[] comprobadas = { 0 };

		CONTRATO.documento().get("paths").properties().forEach(ruta -> ruta.getValue().properties().forEach(metodo -> {
			JsonNode operacion = metodo.getValue();
			List<String> roles = new ArrayList<>();
			operacion.get("security").forEach(requisito -> roles.add(requisito.get("bearerAuth").get(0).asText()));
			boolean publica = roles.isEmpty();
			String url = ruta.getKey().replaceAll("[{][^}]+[}]", "999999");
			String cuerpo = operacion.has("requestBody") ? "{}" : null;
			HttpMethod http = HttpMethod.valueOf(metodo.getKey().toUpperCase(Locale.ROOT));

			for (String actor : new String[] { null, "LIBRARIAN", "MEMBER" }) {
				tokenActual = actor == null ? null : actor.equals("LIBRARIAN") ? deBibliotecario : deSocio;
				try {
					int estado = enviar(http, url, cuerpo).getResponse().getStatus();
					boolean permitido = publica || roles.contains(actor);
					boolean correcto = actor == null && !publica ? estado == 401 : !permitido ? estado == 403 : estado != 401 && estado != 403;
					comprobadas[0]++;
					if (!correcto) {
						problemas.add("%s as %s answered %d (roles in the contract: %s)".formatted(operacion.get("operationId").asText(), actor, estado, roles));
					}
				} catch (Exception e) {
					problemas.add(operacion.get("operationId").asText() + " as " + actor + " failed: " + e);
				}
			}
		}));

		assertThat(problemas).isEmpty();
		assertThat(comprobadas[0]).as("31 operations, three actors each").isEqualTo(93);
	}

	@AfterAll
	static void everyOperationOfTheContractWasCalledWithItsSuccessStatus() {
		List<String> faltan = new ArrayList<>();
		CONTRATO.documento().get("paths").properties().forEach(ruta -> ruta.getValue().properties().forEach(metodo -> {
			String exito = exito(metodo.getValue());
			String clave = metodo.getKey().toUpperCase(Locale.ROOT) + " " + ruta.getKey() + " " + exito;
			if (!LLAMADAS.contains(clave)) {
				faltan.add(clave);
			}
		}));
		assertThat(faltan).as("operations of the contract that no scenario calls").isEmpty();
	}

	// ---- the checks

	/** Calls the API, expects {@code estado} and checks the exchange against the contract. */
	private MvcResult llamar(HttpMethod metodo, String url, String cuerpo, int estado, String... parametros) throws Exception {
		MvcResult resultado = enviar(metodo, url, cuerpo, parametros);
		assertThat(resultado.getResponse().getStatus()).as("%s %s -> %s", metodo, url, resultado.getResponse().getContentAsString())
				.isEqualTo(estado);
		verificar(metodo, null, resultado, cuerpo, true);
		return resultado;
	}

	/**
	 * A call the security filter refuses (401, 403) never reaches a controller, so the test names the operation
	 * it was aimed at and the answer is still checked against what the contract lists for it.
	 */
	private void llamarRefusado(HttpMethod metodo, String plantilla, String url, String cuerpo, int estado) throws Exception {
		MvcResult resultado = enviar(metodo, url, cuerpo);
		assertThat(resultado.getResponse().getStatus()).as("%s %s -> %s", metodo, url, resultado.getResponse().getContentAsString())
				.isEqualTo(estado);
		verificar(metodo, plantilla, resultado, cuerpo, false);
	}

	/** Like {@link #llamar}, for a request body that breaks the request schema on purpose. */
	private void llamarInvalido(HttpMethod metodo, String url, String cuerpo, int estado) throws Exception {
		MvcResult resultado = enviar(metodo, url, cuerpo);
		assertThat(resultado.getResponse().getStatus()).isEqualTo(estado);
		verificar(metodo, null, resultado, cuerpo, false);
	}

	private MvcResult enviar(HttpMethod metodo, String url, String cuerpo, String... parametros) throws Exception {
		MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.request(metodo, url);
		for (int i = 0; i < parametros.length; i += 2) {
			peticion.param(parametros[i], parametros[i + 1]);
		}
		if (cuerpo != null) {
			peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		if (tokenActual != null) {
			peticion.header("Authorization", bearer(tokenActual));
		}
		return mvc.perform(peticion).andReturn();
	}

	private void verificar(HttpMethod metodo, String plantillaPedida, MvcResult resultado, String cuerpoEnviado, boolean cuerpoValido) throws Exception {
		String alcanzada = (String) resultado.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		String plantilla = alcanzada != null ? alcanzada : plantillaPedida;
		assertThat(plantilla).as("the request did not reach an operation").isNotNull();
		JsonNode operacion = CONTRATO.documento().at("/paths").get(plantilla).get(metodo.name().toLowerCase(Locale.ROOT));
		assertThat(operacion).as("%s %s is not in the contract", metodo, plantilla).isNotNull();

		if (cuerpoEnviado != null) {
			JsonNode esquema = operacion.at("/requestBody/content/application~1json/schema");
			assertThat(esquema.isMissingNode()).as("%s %s takes no body in the contract", metodo, plantilla).isFalse();
			if (cuerpoValido) {
				assertConforme(esquema, JSON.readTree(cuerpoEnviado), "request " + metodo + " " + plantilla);
			}
		}

		int estado = resultado.getResponse().getStatus();
		JsonNode respuesta = operacion.at("/responses/" + estado);
		assertThat(respuesta.isMissingNode()).as("%s %s answered %s, which the contract does not list", metodo, plantilla, estado).isFalse();

		String cuerpo = resultado.getResponse().getContentAsString();
		JsonNode json = respuesta.at("/content/application~1json");
		if (json.isMissingNode()) {
			assertThat(cuerpo).as("the contract lists no body for %s", estado).isEmpty();
		} else {
			assertThat(resultado.getResponse().getContentType()).startsWith("application/json");
			assertConforme(json.get("schema"), JSON.readTree(cuerpo), "response " + metodo + " " + plantilla + " " + estado);
			if (estado >= 400) {
				assertThat(codigosDocumentados(json)).as("code of %s %s %s", metodo, plantilla, estado)
						.contains(JSON.readTree(cuerpo).get("code").asText());
			}
		}
		respuesta.at("/headers").fieldNames()
				.forEachRemaining(cabecera -> assertThat(resultado.getResponse().containsHeader(cabecera)).as("header " + cabecera).isTrue());

		LLAMADAS.add(metodo.name() + " " + plantilla + " " + estado);
	}

	private void verificarError(MvcResult resultado, String plantilla, HttpMethod metodo) throws Exception {
		JsonNode respuesta = CONTRATO.documento().at("/paths").get(plantilla).get(metodo.name().toLowerCase(Locale.ROOT))
				.at("/responses/" + resultado.getResponse().getStatus());
		assertThat(respuesta.isMissingNode()).isFalse();
		JsonNode cuerpo = JSON.readTree(resultado.getResponse().getContentAsString());
		assertConforme(respuesta.at("/content/application~1json/schema"), cuerpo, "415");
		assertThat(codigosDocumentados(respuesta.at("/content/application~1json"))).contains(cuerpo.get("code").asText());
	}

	private void validarErrorGenerico(MvcResult resultado, String codigoEsperado) throws Exception {
		JsonNode cuerpo = JSON.readTree(resultado.getResponse().getContentAsString());
		assertConforme(CONTRATO.componente("ErrorResponse"), cuerpo, codigoEsperado);
		assertThat(cuerpo.get("code").asText()).isEqualTo(codigoEsperado);
		assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name)).contains(codigoEsperado);
	}

	private static void assertConforme(JsonNode esquema, JsonNode instancia, String que) {
		List<Error> problemas = CONTRATO.validar(esquema, instancia);
		assertThat(problemas).as("%s does not conform to the contract: %s", que, instancia)
				.isEmpty();
	}

	/** The error codes a response lists, taken from the names of its examples. */
	private static Set<String> codigosDocumentados(JsonNode json) {
		Set<String> codigos = new LinkedHashSet<>();
		json.at("/examples").fieldNames().forEachRemaining(codigos::add);
		return codigos;
	}

	private static String exito(JsonNode operacion) {
		for (var respuesta : operacion.get("responses").properties()) {
			if (respuesta.getKey().startsWith("2")) {
				return respuesta.getKey();
			}
		}
		throw new IllegalStateException("operation without a success response");
	}

	private static long id(MvcResult resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
	}

}
