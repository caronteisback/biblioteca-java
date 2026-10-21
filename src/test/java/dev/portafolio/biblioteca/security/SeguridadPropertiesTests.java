package dev.portafolio.biblioteca.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** The application must refuse to start with a weak or missing signing key, instead of running with a guessable one. */
class SeguridadPropertiesTests {

	@Configuration
	@EnableConfigurationProperties(SeguridadProperties.class)
	static class Config {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.withPropertyValues(
					"biblioteca.seguridad.jwt-duracion=60m",
					"biblioteca.seguridad.intentos-maximos=5",
					"biblioteca.seguridad.bloqueo=5m");

	@Test
	void bindsTheSettingsWhenTheSecretIsLongEnough() {
		runner.withPropertyValues("biblioteca.seguridad.jwt-secreto=" + "k".repeat(32)).run(context -> {
			SeguridadProperties propiedades = context.getBean(SeguridadProperties.class);

			assertThat(propiedades.jwtDuracion().toMinutes()).isEqualTo(60);
			assertThat(propiedades.intentosMaximos()).isEqualTo(5);
			assertThat(propiedades.bloqueo().toMinutes()).isEqualTo(5);
			assertThat(propiedades.adminEmail()).isNull();
		});
	}

	@Test
	void doesNotStartWithoutASecret() {
		runner.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("biblioteca.seguridad.jwt-secreto=").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void doesNotStartWithAShortSecret() {
		runner.withPropertyValues("biblioteca.seguridad.jwt-secreto=" + "k".repeat(31)).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("at least 32 bytes");
		});
	}

	@Test
	void doesNotStartWithANonPositiveDurationOrLock() {
		String secreto = "biblioteca.seguridad.jwt-secreto=" + "k".repeat(32);
		runner.withPropertyValues(secreto, "biblioteca.seguridad.jwt-duracion=0s").run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues(secreto, "biblioteca.seguridad.bloqueo=-1m").run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues(secreto, "biblioteca.seguridad.intentos-maximos=0").run(context -> assertThat(context).hasFailed());
	}

}
