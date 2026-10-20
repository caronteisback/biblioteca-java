package dev.portafolio.biblioteca.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Date rules of a loan, without a database. Due day itself is on time. */
class PrestamoTests {

	private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
	private static final LocalDate VENCE = DESDE.plusDays(14);

	private Prestamo prestamo() {
		return Prestamo.builder().fechaPrestamo(DESDE).fechaVencimiento(VENCE).build();
	}

	@Test
	void newLoanIsActiveWithNoRenewals() {
		Prestamo prestamo = prestamo();

		assertThat(prestamo.isActivo()).isTrue();
		assertThat(prestamo.getRenovaciones()).isZero();
		assertThat(prestamo.getFechaDevolucion()).isNull();
	}

	@Test
	void isNotOverdueOnTheDueDay() {
		assertThat(prestamo().isVencido(VENCE)).isFalse();
		assertThat(prestamo().diasRetraso(VENCE)).isZero();
	}

	@Test
	void isOverdueTheDayAfterTheDueDay() {
		assertThat(prestamo().isVencido(VENCE.plusDays(1))).isTrue();
		assertThat(prestamo().diasRetraso(VENCE.plusDays(1))).isEqualTo(1);
	}

	@Test
	void delayNeverGoesNegativeBeforeTheDueDate() {
		assertThat(prestamo().isVencido(DESDE)).isFalse();
		assertThat(prestamo().diasRetraso(DESDE)).isZero();
	}

	@Test
	void anActiveLoanKeepsCountingDelayUpToToday() {
		assertThat(prestamo().diasRetraso(VENCE.plusDays(5))).isEqualTo(5);
		assertThat(prestamo().diasRetraso(VENCE.plusDays(9))).isEqualTo(9);
	}

	@Test
	void aReturnedLoanFreezesItsDelayAtTheReturnDate() {
		Prestamo prestamo = prestamo();
		prestamo.devolver(VENCE.plusDays(5));

		assertThat(prestamo.isActivo()).isFalse();
		assertThat(prestamo.diasRetraso(VENCE.plusDays(40))).isEqualTo(5);
		assertThat(prestamo.isVencido(VENCE.plusDays(40))).isFalse();
	}

	@Test
	void returningOnTheDueDayIsOnTime() {
		Prestamo prestamo = prestamo();
		prestamo.devolver(VENCE);

		assertThat(prestamo.diasRetraso(VENCE.plusDays(10))).isZero();
	}

	@Test
	void renewalMovesTheDueDateFromTheCurrentDueDateAndCountsIt() {
		Prestamo prestamo = prestamo();

		prestamo.renovar(14);
		assertThat(prestamo.getFechaVencimiento()).isEqualTo(VENCE.plusDays(14));
		assertThat(prestamo.getRenovaciones()).isEqualTo(1);

		prestamo.renovar(14);
		prestamo.renovar(14);
		assertThat(prestamo.getFechaVencimiento()).isEqualTo(DESDE.plusDays(14 + 3 * 14));
		assertThat(prestamo.getRenovaciones()).isEqualTo(3);
	}

}
