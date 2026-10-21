package dev.portafolio.biblioteca.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads the demo library when the application starts with the {@code demo} profile, and never otherwise. It runs before the
 * startup of the first librarian ({@code ArranqueDeUsuarios}), so the demo accounts exist when that one checks for accounts.
 */
@Component
@Profile("demo")
@Order(1)
@RequiredArgsConstructor
class CargadorDeDemo implements ApplicationRunner {

	private final DatosDeEjemplo datos;

	@Override
	public void run(ApplicationArguments args) {
		datos.cargar();
	}

}
