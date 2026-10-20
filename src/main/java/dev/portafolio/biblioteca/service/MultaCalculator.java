package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Amount of a fine: days late times the configured fine per day, in soles with two decimals. */
@Component
@RequiredArgsConstructor
public class MultaCalculator {

	private final BibliotecaProperties properties;

	public BigDecimal calcular(int diasRetraso) {
		return properties.multa().porDia().multiply(BigDecimal.valueOf(Math.max(0, diasRetraso))).setScale(2);
	}

}
