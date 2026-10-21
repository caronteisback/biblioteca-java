package dev.portafolio.biblioteca.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.entity.Rol;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

/** Which role may pass where, for real paths and for OpenAPI templates alike. */
class ReglasDeAccesoTests {

	private static Set<Rol> roles(String metodo, String ruta) {
		return ReglasDeAcceso.para(HttpMethod.valueOf(metodo), ruta).orElseThrow().roles();
	}

	@Test
	void signingInNeedsNoRole() {
		assertThat(ReglasDeAcceso.para(HttpMethod.POST, "/api/v1/auth/login").orElseThrow().esPublica()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
			"GET,/api/v1/libros",
			"GET,/api/v1/libros/5",
			"GET,/api/v1/libros/{id}",
			"GET,/api/v1/libros/5/ejemplares",
			"GET,/api/v1/libros/{libroId}/ejemplares",
			"GET,/api/v1/me" })
	void bothRolesMayRead(String metodo, String ruta) {
		assertThat(roles(metodo, ruta)).containsExactlyInAnyOrder(Rol.LIBRARIAN, Rol.MEMBER);
	}

	@ParameterizedTest
	@CsvSource({ "GET,/api/v1/me/prestamos", "GET,/api/v1/me/multas" })
	void onlyMembersHaveTheirOwnData(String metodo, String ruta) {
		assertThat(roles(metodo, ruta)).containsExactly(Rol.MEMBER);
	}

	@ParameterizedTest
	@CsvSource({
			"POST,/api/v1/libros",
			"PUT,/api/v1/libros/5",
			"DELETE,/api/v1/libros/5",
			"POST,/api/v1/libros/5/ejemplares",
			"GET,/api/v1/ejemplares/5",
			"GET,/api/v1/socios",
			"GET,/api/v1/socios/5",
			"GET,/api/v1/prestamos",
			"POST,/api/v1/prestamos",
			"POST,/api/v1/prestamos/5/devolucion",
			"GET,/api/v1/multas",
			"POST,/api/v1/multas/5/pago",
			"GET,/api/v1/algo-que-nadie-listo" })
	void everythingElseUnderApiIsForLibrariansOnly(String metodo, String ruta) {
		assertThat(roles(metodo, ruta)).containsExactly(Rol.LIBRARIAN);
	}

	@Test
	void aNewWriteOnTheCatalogIsNotOpenToMembersByAccident() {
		assertThat(roles("PATCH", "/api/v1/libros/5")).containsExactly(Rol.LIBRARIAN);
		assertThat(roles("DELETE", "/api/v1/libros/5/ejemplares")).containsExactly(Rol.LIBRARIAN);
	}

	@Test
	void pathsOutsideApiHaveNoRule() {
		assertThat(ReglasDeAcceso.para(HttpMethod.GET, "/actuator/health")).isEmpty();
		assertThat(ReglasDeAcceso.para(HttpMethod.GET, "/anything")).isEmpty();
	}

}
