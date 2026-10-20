package dev.portafolio.biblioteca.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The rules that must hold even if the application code has a bug: they live in the schema.
 * Each rejected statement is checked by the name of the constraint Oracle reports.
 */
class EsquemaRestriccionesTests extends BaseIntegracionTest {

	@Test
	void flywayAppliedTheBaseMigration() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = 1", Integer.class);

		assertThat(applied).isEqualTo(1);
	}

	@Test
	void aCopyCannotHaveTwoActiveLoans() {
		Ejemplar ejemplar = ejemplar();
		prestamos.saveAndFlush(prestamo(ejemplar, socio(), HOY, 14));

		assertThatThrownBy(() -> prestamos.saveAndFlush(prestamo(ejemplar, socio(), HOY, 14)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("UX_PRESTAMO_EJEMPLAR_ACTIVO");
	}

	@Test
	void aReturnedLoanDoesNotBlockTheNextOne() {
		Ejemplar ejemplar = ejemplar();
		Prestamo first = prestamo(ejemplar, socio(), HOY.minusDays(30), 14);
		first.devolver(HOY.minusDays(20));
		prestamos.saveAndFlush(first);

		assertThatCode(() -> prestamos.saveAndFlush(prestamo(ejemplar, socio(), HOY, 14))).doesNotThrowAnyException();
	}

	@Test
	void manyReturnedLoansOfTheSameCopyAreAllowed() {
		Ejemplar ejemplar = ejemplar();
		for (int index = 0; index < 3; index++) {
			Prestamo prestamo = prestamo(ejemplar, socio(), HOY.minusDays(60 - index * 10), 5);
			prestamo.devolver(HOY.minusDays(58 - index * 10));
			prestamos.saveAndFlush(prestamo);
		}

		assertThat(prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId())).isFalse();
		assertThat(prestamos.existsByEjemplarId(ejemplar.getId())).isTrue();
	}

	@Test
	void aLoanCannotBeRenewedMoreThanThreeTimes() {
		Prestamo prestamo = prestamo(ejemplar(), socio(), HOY, 14);
		for (int index = 0; index < 4; index++) {
			prestamo.renovar(14);
		}

		assertThatThrownBy(() -> prestamos.saveAndFlush(prestamo))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("CK_PRESTAMO_RENOVACIONES");
	}

	@Test
	void aLoanCannotBeDueBeforeItStarts() {
		Prestamo prestamo = prestamo(ejemplar(), socio(), HOY, -1);

		assertThatThrownBy(() -> prestamos.saveAndFlush(prestamo))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("CK_PRESTAMO_VENCIMIENTO");
	}

	@Test
	void aLoanCannotBeReturnedBeforeItStarts() {
		Prestamo prestamo = prestamo(ejemplar(), socio(), HOY, 14);
		prestamo.devolver(HOY.minusDays(1));

		assertThatThrownBy(() -> prestamos.saveAndFlush(prestamo))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("CK_PRESTAMO_DEVOLUCION");
	}

	@Test
	void memberEmailIsUniqueIgnoringCase() {
		socios.saveAndFlush(Socio.builder().nombre("Ana").email("Ana.Ficticia@Example.org").fechaRegistro(HOY).build());

		assertThatThrownBy(() -> socios
						.saveAndFlush(Socio.builder().nombre("Otra Ana").email("ana.ficticia@example.org").fechaRegistro(HOY).build()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("UX_SOCIO_EMAIL");
	}

	@Test
	void bookIsbnIsUnique() {
		Libro libro = libro();

		assertThatThrownBy(() -> libros.saveAndFlush(
						Libro.builder().isbn(libro.getIsbn()).titulo("Repetido").autor("Autor").build()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("UK_LIBRO_ISBN");
	}

	@Test
	void bookIsbnMustBeTenOrThirteenDigits() {
		assertThatThrownBy(() -> libros.saveAndFlush(Libro.builder().isbn("12-34").titulo("Corto").autor("Autor").build()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("CK_LIBRO_ISBN");
	}

	@Test
	void anIsbnTenMayEndInX() {
		assertThatCode(() -> libros.saveAndFlush(Libro.builder().isbn("080442957X").titulo("Diez").autor("Autor").build()))
				.doesNotThrowAnyException();
	}

	@Test
	void copyCodeIsUnique() {
		Ejemplar ejemplar = ejemplar();

		assertThatThrownBy(() -> ejemplares.saveAndFlush(Ejemplar.builder().libro(libro()).codigo(ejemplar.getCodigo()).build()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("UK_EJEMPLAR_CODIGO");
	}

	@Test
	void aLoanHasAtMostOneFine() {
		Prestamo prestamo = prestamos.saveAndFlush(prestamo(ejemplar(), socio(), HOY.minusDays(30), 14));
		multas.saveAndFlush(multa(prestamo));

		assertThatThrownBy(() -> multas.saveAndFlush(multa(prestamo)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("UK_MULTA_PRESTAMO");
	}

	@Test
	void aPaidFineMustHaveAPaymentDate() {
		Prestamo prestamo = prestamos.saveAndFlush(prestamo(ejemplar(), socio(), HOY.minusDays(30), 14));
		Multa multa = multas.saveAndFlush(multa(prestamo));

		assertThatThrownBy(() -> jdbc.update("update multa set estado = 'PAID' where id = ?", multa.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("CK_MULTA_PAGO");
	}

	@Test
	void aFineMustChargeSomething() {
		Prestamo prestamo = prestamos.saveAndFlush(prestamo(ejemplar(), socio(), HOY.minusDays(30), 14));
		Multa multa = Multa.builder().prestamo(prestamo).diasRetraso(0).monto(BigDecimal.ZERO).fechaGeneracion(HOY).build();

		assertThatThrownBy(() -> multas.saveAndFlush(multa))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("CK_MULTA_");
	}

	private Multa multa(Prestamo prestamo) {
		return Multa.builder().prestamo(prestamo).diasRetraso(5).monto(new BigDecimal("5.00")).fechaGeneracion(HOY).build();
	}

}
