package dev.portafolio.biblioteca.config;

import dev.portafolio.biblioteca.dto.ErrorResponse;
import dev.portafolio.biblioteca.entity.Rol;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.security.ReglasDeAcceso;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * The OpenAPI description of the API, built from the code so it cannot disagree with it.
 *
 * <p>Three things are added on top of what the library infers:
 * <ul>
 * <li>the error responses of every operation, from {@link ErroresDocumentados} and from the rules
 * that apply to every request (a bad request, a wrong content type, an unexpected failure);
 * <li>the {@code ErrorResponse} schema, with the full list of codes;
 * <li>"required" on every field of an answer that is not nullable, because a contract where nothing is
 * required cannot check anything.
 * </ul>
 */
@Configuration
public class OpenApiConfig {

	/** Version of the contract (not of the artifact). Raise it when the API changes; see biblioteca-contrato. */
	static final String API_VERSION = "0.4.0";

	@Bean
	OpenAPI bibliotecaOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Biblioteca API")
						.version(API_VERSION)
						.description("""
								REST API of a small library: books, copies, members, loans, renewals, returns and fines.
								All data is fictional.

								**Errors.** Every failure answers `{ "code": "...", "message": "..." }`. The `code` is stable and \
								meant for programs; the `message` is English text for people. Each operation lists the codes it can return.

								**Authentication.** Every operation but `login` needs a bearer token: `Authorization: Bearer <token>`. Get one with \n								`POST /api/v1/auth/login`; it is valid for 60 minutes. There are two roles. A `LIBRARIAN` can do everything. A `MEMBER` \n								can read the catalog and only their own data (`/api/v1/me`). Each operation lists the roles that may call it. Without a \n								token the answer is `401 UNAUTHENTICATED`; with the wrong role, `403 FORBIDDEN`.

								**Dates** are days (`yyyy-MM-dd`) in the `America/Lima` zone and always come from the server, never \
								from the client.

								**Calculated, never stored:** the member score and alerts, the loan alerts and the copy availability.
								""")
						.license(new License().name("MIT").url("https://opensource.org/license/mit")))
				.servers(List.of(new Server().url("http://localhost:8080").description("Local development")))
				.tags(List.of(
						new Tag().name("Authentication").description("Signing in."),
						new Tag().name("Account").description("What the signed-in user can see about themselves."),
						new Tag().name("Books").description("Titles of the catalog, identified by ISBN."),
						new Tag().name("Copies").description("Physical copies of a book. Loans are made on copies."),
						new Tag().name("Members").description("People who can borrow, with their score and alerts."),
						new Tag().name("Loans").description("Lending, renewing and returning a copy."),
						new Tag().name("Fines").description("Fines created by late returns."),
						new Tag().name("Dashboard").description("Totals and rankings for the home of the librarian.")));
	}

	@Bean
	OperationCustomizer erroresPorOperacion() {
		return (operation, handler) -> {
			Set<ErrorCode> codigos = new LinkedHashSet<>();
			ErroresDocumentados documentados = handler.getMethodAnnotation(ErroresDocumentados.class);
			if (documentados != null) {
				codigos.addAll(Arrays.asList(documentados.value()));
			}
			boolean tieneCuerpo = operation.getRequestBody() != null;
			boolean tieneParametros = operation.getParameters() != null && !operation.getParameters().isEmpty();
			if (tieneCuerpo || tieneParametros) {
				codigos.add(ErrorCode.VALIDATION_ERROR);
			}
			if (tieneCuerpo) {
				codigos.add(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
			}
			codigos.add(ErrorCode.INTERNAL_ERROR);

			normalizarExito(operation);
			agregarErrores(operation, codigos);
			return operation;
		};
	}

	@Bean
	OpenApiCustomizer contrato() {
		return openApi -> {
			Components componentes = openApi.getComponents() != null ? openApi.getComponents() : new Components();
			openApi.setComponents(componentes);
			Map<String, Schema> esquemas = componentes.getSchemas() != null ? componentes.getSchemas() : new LinkedHashMap<>();
			componentes.setSchemas(esquemas);

			ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(esquemas::putIfAbsent);
			Schema<?> error = esquemas.get("ErrorResponse");
			if (error.getTypes() == null) {
				// Read with the 3.0 reader, the type lives in the single-type field, which a 3.1 document does not write.
				error.setTypes(Set.of("object"));
			}
			@SuppressWarnings("unchecked")
			Schema<String> codigo = (Schema<String>) error.getProperties().get("code");
			codigo.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toList()));
			codigo.setDescription("Stable error code. ENDPOINT_NOT_FOUND and METHOD_NOT_ALLOWED can answer any URL, so no operation lists them.");

			esquemas.forEach((nombre, esquema) -> {
				if (!nombre.endsWith("Request") && esquema.getProperties() != null) {
					List<String> obligatorios = new ArrayList<>();
					@SuppressWarnings("unchecked")
					Map<String, Schema<?>> propiedades = esquema.getProperties();
					for (Map.Entry<String, Schema<?>> entrada : new ArrayList<>(propiedades.entrySet())) {
						Schema<?> propiedad = entrada.getValue();
						if (!admiteNulo(propiedad)) {
							obligatorios.add(entrada.getKey());
						} else if (propiedad.get$ref() != null) {
							propiedades.put(entrada.getKey(), referenciaOpcional(propiedad));
						}
					}
					esquema.setRequired(obligatorios);
				}
			});

			// Who may call each operation comes from the same rules that the filter chain enforces, and the 401 and
			// 403 answers follow from them. This runs before the examples are registered, so the new codes get theirs.
			componentes.addSecuritySchemes("bearerAuth", new SecurityScheme()
					.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
					.description("The token that `login` returns, sent as `Authorization: Bearer <token>`."));
			openApi.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
			openApi.getPaths().forEach((ruta, item) -> item.readOperationsMap().forEach((metodo, operacion) ->
					ReglasDeAcceso.para(HttpMethod.valueOf(metodo.name()), ruta).ifPresent(regla -> documentarAcceso(operacion, regla))));

			// One shared example per error code, referenced by every operation that can return it. Two codes can
			// answer any URL (unknown route, wrong method), so no operation lists them and they have no example.
			Set<String> usados = ejemplosUsados(openApi);
			Map<String, Example> ejemplos = componentes.getExamples() != null ? componentes.getExamples() : new LinkedHashMap<>();
			componentes.setExamples(ejemplos);
			for (ErrorCode entrada : ErrorCode.values()) {
				if (!usados.contains(entrada.name())) {
					continue;
				}
				Map<String, String> valor = new LinkedHashMap<>();
				valor.put("code", entrada.name());
				valor.put("message", entrada.message());
				ejemplos.put(entrada.name(), new Example().summary(entrada.name()).value(valor));
			}
		};
	}

	/**
	 * The roles of an operation as security requirements (one per role: any of them is enough) and the answers that
	 * follow: 401 when a sign-in is needed, 403 when some role is not allowed. A public operation lists no requirement.
	 */
	private static void documentarAcceso(Operation operacion, ReglasDeAcceso.Regla regla) {
		if (regla.esPublica()) {
			operacion.setSecurity(new ArrayList<>());
			return;
		}
		List<SecurityRequirement> requisitos = new ArrayList<>();
		regla.roles().stream().sorted().forEach(rol -> requisitos.add(new SecurityRequirement().addList("bearerAuth", rol.name())));
		operacion.setSecurity(requisitos);
		Set<ErrorCode> errores = new LinkedHashSet<>();
		errores.add(ErrorCode.UNAUTHENTICATED);
		if (regla.roles().size() < Rol.values().length) {
			errores.add(ErrorCode.FORBIDDEN);
		}
		agregarErrores(operacion, errores);
	}

	/** Names of the shared examples that some operation points to. */
	private static Set<String> ejemplosUsados(OpenAPI openApi) {
		Set<String> usados = new LinkedHashSet<>();
		String prefijo = "#/components/examples/";
		openApi.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(operacion ->
				operacion.getResponses().values().forEach(respuesta -> {
					if (respuesta.getContent() == null) {
						return;
					}
					respuesta.getContent().values().forEach(medio -> {
						if (medio.getExamples() != null) {
							medio.getExamples().values().stream()
									.map(Example::get$ref)
									.filter(ref -> ref != null && ref.startsWith(prefijo))
									.forEach(ref -> usados.add(ref.substring(prefijo.length())));
						}
					});
				})));
		return usados;
	}

	/**
	 * The library writes an optional reference as {@code type: "null"} next to a {@code $ref}. In OpenAPI
	 * 3.1 that means "must be null and also be that object", which no value satisfies. The correct form
	 * is {@code oneOf: [ the object, null ]}.
	 */
	private static Schema<?> referenciaOpcional(Schema<?> propiedad) {
		Schema<?> objeto = new Schema<>();
		objeto.set$ref(propiedad.get$ref());
		Schema<?> nulo = new Schema<>();
		nulo.setTypes(Set.of("null"));
		Schema<?> opcional = new Schema<>();
		opcional.setOneOf(new ArrayList<>(List.of(objeto, nulo)));
		opcional.setDescription(propiedad.getDescription());
		return opcional;
	}

	/** OpenAPI 3.1 writes "may be null" as a type list that contains "null"; 3.0 used the nullable flag. */
	private static boolean admiteNulo(Schema<?> propiedad) {
		return Boolean.TRUE.equals(propiedad.getNullable())
				|| (propiedad.getTypes() != null && propiedad.getTypes().contains("null"));
	}

	/** A creation answers 201 and a delete 204, not the 200 that the library infers from ResponseEntity. */
	private static void normalizarExito(Operation operation) {
		Map<String, ApiResponse> respuestas = operation.getResponses();
		if (respuestas == null) {
			return;
		}
		if (respuestas.containsKey("201") || respuestas.containsKey("204")) {
			respuestas.remove("200");
		}
		// Without "produces" the library lists every answer as "*/*"; this API only ever writes JSON.
		respuestas.values().forEach(OpenApiConfig::soloJson);
		ApiResponse creado = respuestas.get("201");
		if (creado != null) {
			creado.addHeaderObject("Location", new Header()
					.description("URL of the created resource.")
					.schema(new StringSchema()));
		}
	}

	private static void soloJson(ApiResponse respuesta) {
		Content contenido = respuesta.getContent();
		if (contenido == null || !contenido.containsKey("*/*")) {
			return;
		}
		Content json = new Content();
		contenido.forEach((tipo, medio) -> json.addMediaType(tipo.equals("*/*") ? "application/json" : tipo, medio));
		respuesta.setContent(json);
	}

	private static void agregarErrores(Operation operation, Set<ErrorCode> codigos) {
		Map<Integer, List<ErrorCode>> porEstado = new TreeMap<>();
		for (ErrorCode codigo : codigos) {
			porEstado.computeIfAbsent(codigo.status().value(), estado -> new ArrayList<>()).add(codigo);
		}
		porEstado.forEach((estado, lista) -> {
			String clave = String.valueOf(estado);
			if (operation.getResponses().containsKey(clave)) {
				return;
			}
			Map<String, Example> ejemplos = new LinkedHashMap<>();
			for (ErrorCode codigo : lista) {
				ejemplos.put(codigo.name(), new Example().$ref("#/components/examples/" + codigo.name()));
			}
			MediaType json = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
			json.setExamples(ejemplos);
			String descripcion = lista.stream().map(codigo -> "`" + codigo.name() + "`").collect(Collectors.joining(", "));
			operation.getResponses().addApiResponse(clave, new ApiResponse()
					.description("Error. Possible codes: " + descripcion + ".")
					.content(new Content().addMediaType("application/json", json)));
		});
	}

}
