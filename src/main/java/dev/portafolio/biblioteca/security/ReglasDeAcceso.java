package dev.portafolio.biblioteca.security;

import dev.portafolio.biblioteca.entity.Rol;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Who may call what: the one list that both the filter chain (to enforce it) and the OpenAPI
 * description (to publish it) read, so what the contract says and what the API does cannot differ.
 *
 * <p>The first rule that matches wins, and the last one closes the door: anything else under
 * {@code /api} is for librarians only. A new endpoint that nobody listed is therefore refused to members.
 */
public final class ReglasDeAcceso {

	/** A rule: the method (null for any), the path pattern and the roles that may pass (none = anyone, no sign-in). */
	public record Regla(HttpMethod metodo, String patron, Set<Rol> roles) {

		public boolean esPublica() {
			return roles.isEmpty();
		}

		/** Whether a method and a path (a real one or an OpenAPI template such as /api/v1/libros/{id}) fall under this rule. */
		boolean aplicaA(HttpMethod otroMetodo, String ruta) {
			PathPattern pattern = PathPatternParser.defaultInstance.parse(patron);
			return (metodo == null || metodo.equals(otroMetodo)) && pattern.matches(org.springframework.http.server.PathContainer.parsePath(ruta));
		}

	}

	private static final Set<Rol> TODOS = EnumSet.allOf(Rol.class);
	private static final Set<Rol> BIBLIOTECARIOS = EnumSet.of(Rol.LIBRARIAN);
	private static final Set<Rol> SOCIOS = EnumSet.of(Rol.MEMBER);

	public static final List<Regla> REGLAS = List.of(
			new Regla(HttpMethod.POST, "/api/v1/auth/login", Set.of()),
			new Regla(HttpMethod.GET, "/api/v1/libros", TODOS),
			new Regla(HttpMethod.GET, "/api/v1/libros/*", TODOS),
			new Regla(HttpMethod.GET, "/api/v1/libros/*/ejemplares", TODOS),
			new Regla(HttpMethod.GET, "/api/v1/me", TODOS),
			new Regla(HttpMethod.GET, "/api/v1/me/**", SOCIOS),
			new Regla(null, "/api/**", BIBLIOTECARIOS));

	private ReglasDeAcceso() {
	}

	/** The rule that governs a method and a path, if any. */
	public static Optional<Regla> para(HttpMethod metodo, String ruta) {
		return REGLAS.stream().filter(regla -> regla.aplicaA(metodo, ruta)).findFirst();
	}

}
