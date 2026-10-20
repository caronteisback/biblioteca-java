package dev.portafolio.biblioteca;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import java.math.BigDecimal;
import java.time.ZoneId;

/** The default business parameters of docs/business-rules.md, for tests that need no Spring context. */
public final class PropiedadesDePrueba {

	private PropiedadesDePrueba() {
	}

	public static BibliotecaProperties porDefecto() {
		return new BibliotecaProperties(
				ZoneId.of("America/Lima"),
				new BibliotecaProperties.Prestamo(14, 5, 3, 2),
				new BibliotecaProperties.Multa(new BigDecimal("1.00")),
				new BibliotecaProperties.Score(100, 10, 1, 50));
	}

}
