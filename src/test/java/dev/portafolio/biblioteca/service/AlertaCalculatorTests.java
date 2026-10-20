package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.PropiedadesDePrueba;
import dev.portafolio.biblioteca.config.BibliotecaProperties;
import dev.portafolio.biblioteca.dto.Alerta;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.entity.Prestamo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertaCalculatorTests {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	private final BibliotecaProperties properties = PropiedadesDePrueba.porDefecto();
	private final AlertaCalculator alertas = new AlertaCalculator(properties, new MultaCalculator(properties));

	private Prestamo prestamo(LocalDate vence) {
		return Prestamo.builder().fechaPrestamo(vence.minusDays(14)).fechaVencimiento(vence).build();
	}

	private List<AlertaCodigo> codigos(List<Alerta> lista) {
		return lista.stream().map(Alerta::code).toList();
	}

	@Test
	void aLoanDueInThreeDaysRaisesNothing() {
		assertThat(alertas.delPrestamo(prestamo(HOY.plusDays(3)), HOY)).isEmpty();
	}

	@Test
	void aLoanDueInTwoDaysIsDueSoon() {
		List<Alerta> lista = alertas.delPrestamo(prestamo(HOY.plusDays(2)), HOY);

		assertThat(codigos(lista)).containsExactly(AlertaCodigo.DUE_SOON);
		assertThat(lista.get(0).message()).isEqualTo("Due in 2 day(s).");
	}

	@Test
	void aLoanDueTodayIsDueSoonAndNotOverdue() {
		List<Alerta> lista = alertas.delPrestamo(prestamo(HOY), HOY);

		assertThat(codigos(lista)).containsExactly(AlertaCodigo.DUE_SOON);
		assertThat(lista.get(0).message()).isEqualTo("Due today.");
	}

	@Test
	void anOverdueLoanShowsTheFineAccruedSoFar() {
		List<Alerta> lista = alertas.delPrestamo(prestamo(HOY.minusDays(3)), HOY);

		assertThat(codigos(lista)).containsExactly(AlertaCodigo.OVERDUE);
		assertThat(lista.get(0).message()).isEqualTo("Overdue by 3 day(s). Fine so far: S/ 3.00.");
	}

	@Test
	void aReturnedLoanRaisesNothingEvenIfItWasLate() {
		Prestamo prestamo = prestamo(HOY.minusDays(10));
		prestamo.devolver(HOY.minusDays(2));

		assertThat(alertas.delPrestamo(prestamo, HOY)).isEmpty();
	}

	@Test
	void aHealthyMemberHasNoAlerts() {
		assertThat(alertas.delSocio(100, false)).isEmpty();
		assertThat(alertas.delSocio(50, false)).isEmpty();
	}

	@Test
	void aPendingFineRaisesPendingFine() {
		assertThat(codigos(alertas.delSocio(100, true))).containsExactly(AlertaCodigo.PENDING_FINE);
	}

	@Test
	void aScoreBelowTheThresholdRaisesLowScore() {
		List<Alerta> lista = alertas.delSocio(49, false);

		assertThat(codigos(lista)).containsExactly(AlertaCodigo.LOW_SCORE);
		assertThat(lista.get(0).message()).isEqualTo("Score 49 is below 50: no new loans.");
	}

	@Test
	void aMemberCanHaveBothMemberAlerts() {
		assertThat(codigos(alertas.delSocio(40, true))).containsExactly(AlertaCodigo.PENDING_FINE, AlertaCodigo.LOW_SCORE);
	}

	@Test
	void theFineIsDaysTimesTheConfiguredAmount() {
		MultaCalculator multas = new MultaCalculator(properties);

		assertThat(multas.calcular(5)).isEqualByComparingTo(new BigDecimal("5.00"));
		assertThat(multas.calcular(5).scale()).isEqualTo(2);
		assertThat(multas.calcular(0)).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(multas.calcular(-3)).isEqualByComparingTo(BigDecimal.ZERO);
	}

}
