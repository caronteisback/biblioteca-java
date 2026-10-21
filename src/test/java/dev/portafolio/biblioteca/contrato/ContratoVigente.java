package dev.portafolio.biblioteca.contrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * The pinned copy of the contract (src/test/resources/contrato) and what is needed to check a JSON
 * document against one of its schemas. The schemas of the contract are JSON Schema 2020-12, so a
 * response is checked by pointing a validator at the schema with the contract's {@code components}
 * next to it, where its {@code $ref}s resolve.
 */
final class ContratoVigente {

	static final String RUTA = "/contrato/openapi.yaml";
	static final String PROPIEDADES = "/contrato/contrato.properties";

	private final byte[] bytes;
	private final JsonNode documento;
	private final JsonNode componentesEstrictos;
	private final SchemaRegistry registro = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

	ContratoVigente() {
		try (InputStream entrada = ContratoVigente.class.getResourceAsStream(RUTA)) {
			bytes = entrada.readAllBytes();
			documento = new YAMLMapper().readTree(bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		componentesEstrictos = estrictos(documento.get("components").deepCopy());
	}

	JsonNode documento() {
		return documento;
	}

	String sha256() {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static Properties propiedades() {
		try (InputStream entrada = ContratoVigente.class.getResourceAsStream(PROPIEDADES)) {
			Properties propiedades = new Properties();
			propiedades.load(entrada);
			return propiedades;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The schema of a component, e.g. {@code ErrorResponse}. */
	JsonNode componente(String nombre) {
		ObjectNode ref = new ObjectMapper().createObjectNode();
		ref.put("$ref", "#/components/schemas/" + nombre);
		return ref;
	}

	/** The problems of {@code instancia} against {@code esquema} (a node of the contract), empty when it conforms. */
	List<Error> validar(JsonNode esquema, JsonNode instancia) {
		ObjectNode raiz = esquema.deepCopy();
		raiz.set("components", componentesEstrictos);
		Schema schema = registro.getSchema(raiz);
		return schema.validate(instancia, contexto -> contexto.executionConfig(config -> config.formatAssertionsEnabled(true)));
	}

	/**
	 * A schema that does not say "no other properties" accepts any extra field, so a field added to the
	 * API without touching the contract would pass unnoticed. In the copy used for checking, every
	 * object closes its list of properties.
	 */
	private static JsonNode estrictos(JsonNode nodo) {
		if (nodo.isObject()) {
			ObjectNode objeto = (ObjectNode) nodo;
			if (objeto.has("properties") && !objeto.has("additionalProperties")) {
				objeto.put("additionalProperties", false);
			}
			objeto.forEach(ContratoVigente::estrictos);
		} else if (nodo.isArray()) {
			nodo.forEach(ContratoVigente::estrictos);
		}
		return nodo;
	}

}
