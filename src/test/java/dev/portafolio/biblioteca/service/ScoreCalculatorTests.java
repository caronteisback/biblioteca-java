package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.PropiedadesDePrueba;
import dev.portafolio.biblioteca.entity.Prestamo;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The score formula of docs/business-rules.md, section 9: 100 minus (10 + 1 per day late) per late loan. */
class ScoreCalculatorTests {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	private final ScoreCalculator calculator = new ScoreCalculator(PropiedadesDePrueba.porDefecto());

	private Prestamo prestamo(LocalDate vence) {
		return Prestamo.builder().fechaPrestamo(vence.minusDays(14)).fechaVencimiento(vence).build();
	}

	private Prestamo devuelto(LocalDate vence, LocalDate devolucion) {
		Prestamo prestamo = prestamo(vence);
		prestamo.devolver(devolucion);
		return prestamo;
	}

	@Test
	void aMemberWithoutLoansHasTheStartingScore() {
		assertThat(calculator.calcular(List.of(), HOY)).isEqualTo(100);
	}

	@Test
	void returningOnTimeKeepsTheScoreAtOneHundred() {
		List<Prestamo> historial = List.of(
				devuelto(HOY.minusDays(30), HOY.minusDays(35)),
				devuelto(HOY.minusDays(10), HOY.minusDays(10)));

		assertThat(calculator.calcular(historial, HOY)).isEqualTo(100);
	}

	@Test
	void anActiveLoanNotYetDueDoesNotCost() {
		assertThat(calculator.calcular(List.of(prestamo(HOY.plusDays(3))), HOY)).isEqualTo(100);
		assertThat(calculator.calcular(List.of(prestamo(HOY)), HOY)).isEqualTo(100);
	}

	@Test
	void theExampleOfTheBusinessRules() {
		// Due 2026-10-14, returned 2026-10-19: 5 days late, penalty 10 + 5 = 15.
		Prestamo prestamo = devuelto(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 19));

		assertThat(calculator.calcular(List.of(prestamo), HOY)).isEqualTo(85);
	}

	@Test
	void oneDayLateCostsTheBasePlusOne() {
		assertThat(calculator.calcular(List.of(devuelto(HOY.minusDays(5), HOY.minusDays(4))), HOY)).isEqualTo(89);
	}

	@Test
	void anActiveOverdueLoanKeepsCostingUntilItIsReturned() {
		Prestamo prestamo = prestamo(HOY.minusDays(3));

		assertThat(calculator.calcular(List.of(prestamo), HOY)).isEqualTo(87);
		assertThat(calculator.calcular(List.of(prestamo), HOY.plusDays(4))).isEqualTo(83);
	}

	@Test
	void returningFreezesThePenaltyAtTheReturnDate() {
		Prestamo prestamo = prestamo(HOY.minusDays(3));
		prestamo.devolver(HOY);

		assertThat(calculator.calcular(List.of(prestamo), HOY)).isEqualTo(87);
		assertThat(calculator.calcular(List.of(prestamo), HOY.plusDays(30))).isEqualTo(87);
	}

	@Test
	void penaltiesOfSeveralLoansAdd() {
		List<Prestamo> historial = List.of(
				devuelto(HOY.minusDays(40), HOY.minusDays(35)),
				devuelto(HOY.minusDays(20), HOY.minusDays(18)),
				prestamo(HOY.minusDays(1)));

		// (10 + 5) + (10 + 2) + (10 + 1) = 38
		assertThat(calculator.calcular(historial, HOY)).isEqualTo(62);
	}

	@Test
	void theScoreNeverGoesBelowZero() {
		assertThat(calculator.calcular(List.of(prestamo(HOY.minusDays(500))), HOY)).isZero();
	}

	@Test
	void theScoreOnlyGoesDownAsHistoryGrows() {
		Prestamo first = devuelto(HOY.minusDays(20), HOY.minusDays(15));
		int one = calculator.calcular(List.of(first), HOY);
		int two = calculator.calcular(List.of(first, devuelto(HOY.minusDays(8), HOY.minusDays(7))), HOY);
		int three = calculator.calcular(List.of(first, devuelto(HOY.minusDays(8), HOY.minusDays(7)), prestamo(HOY.minusDays(2))), HOY);

		assertThat(one).isGreaterThanOrEqualTo(two);
		assertThat(two).isGreaterThanOrEqualTo(three);
	}

	@Test
	void aMemberBelowTheThresholdIsBlocked() {
		assertThat(calculator.estaBloqueado(50)).isFalse();
		assertThat(calculator.estaBloqueado(49)).isTrue();
		assertThat(calculator.estaBloqueado(100)).isFalse();
	}

}
