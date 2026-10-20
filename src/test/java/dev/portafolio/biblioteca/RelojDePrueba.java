package dev.portafolio.biblioteca;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Freezes "today" at 2026-10-19 (10:00 in Lima), so date rules are tested without waiting. */
@TestConfiguration(proxyBeanMethods = false)
public class RelojDePrueba {

	public static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");

	@Bean
	@Primary
	Clock relojFijo() {
		return Clock.fixed(AHORA, ZoneId.of("America/Lima"));
	}

}
