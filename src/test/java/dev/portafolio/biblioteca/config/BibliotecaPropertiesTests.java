package dev.portafolio.biblioteca.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/** Binding and validation of the business parameters, without a database. */
class BibliotecaPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(BibliotecaConfig.class)
			.withPropertyValues(
					"biblioteca.zona-horaria=America/Lima",
					"biblioteca.prestamo.dias-plazo=14",
					"biblioteca.prestamo.max-activos=5",
					"biblioteca.prestamo.max-renovaciones=3",
					"biblioteca.prestamo.alerta-dias-previos=2",
					"biblioteca.panel.dias-por-vencer=7",
					"biblioteca.multa.por-dia=1.00",
					"biblioteca.score.inicial=100",
					"biblioteca.score.penalizacion-base=10",
					"biblioteca.score.penalizacion-por-dia=1",
					"biblioteca.score.umbral-bloqueo=50",
					"biblioteca.cors.origenes-permitidos[0]=http://localhost:5500",
					"biblioteca.cors.origenes-permitidos[1]=https://caronteisback.github.io");

	@Test
	void bindsTheValuesOfTheBusinessRules() {
		runner.run(context -> {
			BibliotecaProperties properties = context.getBean(BibliotecaProperties.class);

			assertThat(properties.zonaHoraria()).isEqualTo(ZoneId.of("America/Lima"));
			assertThat(properties.prestamo().diasPlazo()).isEqualTo(14);
			assertThat(properties.prestamo().maxActivos()).isEqualTo(5);
			assertThat(properties.prestamo().maxRenovaciones()).isEqualTo(3);
			assertThat(properties.prestamo().alertaDiasPrevios()).isEqualTo(2);
			assertThat(properties.panel().diasPorVencer()).isEqualTo(7);
			assertThat(properties.multa().porDia()).isEqualByComparingTo(new BigDecimal("1.00"));
			assertThat(properties.score().inicial()).isEqualTo(100);
			assertThat(properties.score().penalizacionBase()).isEqualTo(10);
			assertThat(properties.score().penalizacionPorDia()).isEqualTo(1);
			assertThat(properties.score().umbralBloqueo()).isEqualTo(50);
			assertThat(properties.cors().origenesPermitidos()).containsExactly("http://localhost:5500", "https://caronteisback.github.io");
		});
	}

	@Test
	void aWildcardOriginStopsTheStartup() {
		runner.withPropertyValues("biblioteca.cors.origenes-permitidos[2]=*")
				.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("biblioteca.cors.origenes-permitidos[2]=https://*.example.org")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void anOriginWithAPathOrATrailingSlashOrNoSchemeStopsTheStartup() {
		runner.withPropertyValues("biblioteca.cors.origenes-permitidos[2]=http://localhost:5500/")
				.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("biblioteca.cors.origenes-permitidos[2]=http://localhost:5500/app")
				.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("biblioteca.cors.origenes-permitidos[2]=localhost:5500")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void theClockUsesTheConfiguredZone() {
		runner.run(context -> assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("America/Lima")));
	}

	@Test
	void aWindowOfZeroDaysForTheDashboardStopsTheStartup() {
		runner.withPropertyValues("biblioteca.panel.dias-por-vencer=0")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void anInvalidValueStopsTheStartup() {
		runner.withPropertyValues("biblioteca.prestamo.dias-plazo=0")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void moreRenewalsThanTheSchemaAllowsStopsTheStartup() {
		runner.withPropertyValues("biblioteca.prestamo.max-renovaciones=4")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void aFreeFineStopsTheStartup() {
		runner.withPropertyValues("biblioteca.multa.por-dia=0")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void aMissingSectionStopsTheStartup() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
				.withUserConfiguration(BibliotecaConfig.class)
				.withPropertyValues("biblioteca.zona-horaria=America/Lima")
				.run(context -> assertThat(context).hasFailed());
	}

}
