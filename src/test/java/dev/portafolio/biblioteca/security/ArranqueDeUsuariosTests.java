package dev.portafolio.biblioteca.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import dev.portafolio.biblioteca.service.UsuarioService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

/** The first librarian is created from the environment, only into an empty table, and never with a weak password. */
class ArranqueDeUsuariosTests extends BaseIntegracionTest {

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private UsuarioService usuarioService;

	private ArranqueDeUsuarios arranque(String email, String password) {
		SeguridadProperties propiedades = new SeguridadProperties("k".repeat(32), Duration.ofMinutes(60), 5, Duration.ofMinutes(5), email, password, null);
		return new ArranqueDeUsuarios(usuarios, usuarioService, propiedades);
	}

	private void ejecutar(ArranqueDeUsuarios arranque) {
		arranque.run(new DefaultApplicationArguments());
	}

	@Test
	void createsTheFirstLibrarianWhenThereAreNoAccounts() {
		jdbc.update("delete from usuario");

		ejecutar(arranque("primera@example.org", "a-long-enough-password"));

		var cuenta = usuarios.findByEmailIgnoreCase("primera@example.org").orElseThrow();
		assertThat(cuenta.getRol().name()).isEqualTo("LIBRARIAN");
		assertThat(cuenta.getSocio()).isNull();
		assertThat(cuenta.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("a-long-enough-password");
	}

	@Test
	void doesNothingWhenThereAreAlreadyAccounts() {
		jdbc.update("delete from usuario");
		usuarioService.crearBibliotecario("existente@example.org", "a-long-enough-password");

		ejecutar(arranque("otra@example.org", "another-long-password"));

		assertThat(usuarios.findByEmailIgnoreCase("otra@example.org")).isEmpty();
	}

	@Test
	void startsWithoutAnyAccountWhenNothingIsConfigured() {
		jdbc.update("delete from usuario");

		assertThatCode(() -> ejecutar(arranque("", ""))).doesNotThrowAnyException();
		assertThatCode(() -> ejecutar(arranque(null, null))).doesNotThrowAnyException();

		assertThat(usuarios.count()).isZero();
	}

	@Test
	void stopsWhenOnlyOneOfTheTwoIsSetOrThePasswordIsWeak() {
		jdbc.update("delete from usuario");

		assertThatThrownBy(() -> ejecutar(arranque("sola@example.org", ""))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ejecutar(arranque("", "a-long-enough-password"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ejecutar(arranque("corta@example.org", "short"))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("12");
		assertThat(usuarios.count()).isZero();
	}

}
