package dev.portafolio.biblioteca.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BibliotecaProperties.class)
public class BibliotecaConfig {

	/**
	 * The only source of "today". Services call {@code LocalDate.now(clock)}, so tests replace this
	 * bean with a fixed clock and move through time without waiting.
	 */
	@Bean
	Clock clock(BibliotecaProperties properties) {
		return Clock.system(properties.zonaHoraria());
	}

}
