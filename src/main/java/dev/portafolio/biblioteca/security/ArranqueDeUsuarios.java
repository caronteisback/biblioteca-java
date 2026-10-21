package dev.portafolio.biblioteca.security;

import dev.portafolio.biblioteca.repository.UsuarioRepository;
import dev.portafolio.biblioteca.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nobody can sign in until an account exists, and accounts cannot be created through the API. So, when the
 * table is empty and the operator set {@code BIBLIOTECA_ADMIN_EMAIL} and {@code BIBLIOTECA_ADMIN_PASSWORD},
 * the first librarian is created from them. There is no default account: with nothing set, the application
 * starts and says clearly that nobody can sign in.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
class ArranqueDeUsuarios implements ApplicationRunner {

	private final UsuarioRepository usuarios;
	private final UsuarioService usuarioService;
	private final SeguridadProperties propiedades;

	@Override
	public void run(ApplicationArguments args) {
		if (usuarios.count() > 0) {
			return;
		}
		String email = propiedades.adminEmail();
		String password = propiedades.adminPassword();
		boolean sinEmail = email == null || email.isBlank();
		boolean sinPassword = password == null || password.isBlank();
		if (sinEmail && sinPassword) {
			log.warn("There are no accounts, so nobody can sign in. Set BIBLIOTECA_ADMIN_EMAIL and BIBLIOTECA_ADMIN_PASSWORD "
					+ "(at least {} characters) to create the first librarian.", UsuarioService.LONGITUD_MINIMA_PASSWORD);
			return;
		}
		// Only one of the two is set, or the password is too short: stop with the reason instead of starting half configured.
		usuarioService.crearBibliotecario(email, password);
		log.info("The first librarian was created for {}.", email);
	}

}
