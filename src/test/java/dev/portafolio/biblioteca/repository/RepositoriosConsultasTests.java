package dev.portafolio.biblioteca.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The queries the services will rely on, against the real schema. */
class RepositoriosConsultasTests extends BaseIntegracionTest {

	@Test
	void findsAMemberByEmailIgnoringCase() {
		Socio socio = socios.saveAndFlush(
				Socio.builder().nombre("Luis").email("Luis.Ficticio@Example.org").fechaRegistro(HOY).build());

		assertThat(socios.findByEmailIgnoreCase("luis.ficticio@EXAMPLE.ORG")).contains(socio);
		assertThat(socios.existsByEmailIgnoreCase("LUIS.FICTICIO@example.org")).isTrue();
		assertThat(socios.existsByEmailIgnoreCase("nadie@example.org")).isFalse();
	}

	@Test
	void locksTheMemberRowAndTheLoanRow() {
		Socio socio = socio();
		Prestamo prestamo = prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY, 14));

		assertThat(socios.findByIdForUpdate(socio.getId())).contains(socio);
		assertThat(prestamos.findByIdForUpdate(prestamo.getId())).contains(prestamo);
		assertThat(socios.findByIdForUpdate(-1L)).isEmpty();
	}

	@Test
	void countsOnlyTheActiveLoansOfTheMember() {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY, 14));
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY, 14));
		Prestamo returned = prestamo(ejemplar(), socio, HOY.minusDays(20), 14);
		returned.devolver(HOY.minusDays(10));
		prestamos.saveAndFlush(returned);
		prestamos.saveAndFlush(prestamo(ejemplar(), socio(), HOY, 14));

		assertThat(prestamos.countBySocioIdAndFechaDevolucionIsNull(socio.getId())).isEqualTo(2);
		assertThat(prestamos.findBySocioIdAndFechaDevolucionIsNull(socio.getId())).hasSize(2);
		assertThat(prestamos.findBySocioId(socio.getId())).hasSize(3);
	}

	@Test
	void detectsAnOverdueActiveLoan() {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(15), 14));

		assertThat(prestamos.existsBySocioIdAndFechaDevolucionIsNullAndFechaVencimientoBefore(socio.getId(), HOY)).isTrue();
	}

	@Test
	void aLoanDueTodayIsNotOverdue() {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(14), 14));

		assertThat(prestamos.existsBySocioIdAndFechaDevolucionIsNullAndFechaVencimientoBefore(socio.getId(), HOY)).isFalse();
	}

	@Test
	void aReturnedLateLoanIsNotOverdueAnymore() {
		Socio socio = socio();
		Prestamo prestamo = prestamo(ejemplar(), socio, HOY.minusDays(30), 14);
		prestamo.devolver(HOY.minusDays(5));
		prestamos.saveAndFlush(prestamo);

		assertThat(prestamos.existsBySocioIdAndFechaDevolucionIsNullAndFechaVencimientoBefore(socio.getId(), HOY)).isFalse();
	}

	@Test
	void knowsWhetherACopyIsOnLoan() {
		Ejemplar ejemplar = ejemplar();
		assertThat(prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId())).isFalse();

		Prestamo prestamo = prestamos.saveAndFlush(prestamo(ejemplar, socio(), HOY, 14));
		assertThat(prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId())).isTrue();

		prestamo.devolver(HOY);
		prestamos.saveAndFlush(prestamo);
		assertThat(prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId())).isFalse();
	}

	@Test
	void findsAPendingFineOfTheMemberAndStopsFindingItOncePaid() {
		Socio socio = socio();
		Prestamo prestamo = prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(30), 14));
		Multa multa = multas.saveAndFlush(
				Multa.builder().prestamo(prestamo).diasRetraso(5).monto(new BigDecimal("5.00")).fechaGeneracion(HOY).build());

		assertThat(multas.existsByPrestamoSocioIdAndEstado(socio.getId(), EstadoMulta.PENDING)).isTrue();
		assertThat(multas.existsByPrestamoSocioIdAndEstado(socio().getId(), EstadoMulta.PENDING)).isFalse();

		multa.pagar(HOY);
		multas.saveAndFlush(multa);

		assertThat(multas.existsByPrestamoSocioIdAndEstado(socio.getId(), EstadoMulta.PENDING)).isFalse();
		assertThat(multas.existsByPrestamoSocioIdAndEstado(socio.getId(), EstadoMulta.PAID)).isTrue();
		assertThat(multas.findByPrestamoId(prestamo.getId())).map(Multa::getFechaPago).contains(HOY);
	}

	@Test
	void aBookWithCopiesIsReportedSoItCanBeProtectedFromDeletion() {
		Libro conEjemplares = libro();
		ejemplar(conEjemplares);

		assertThat(ejemplares.existsByLibroId(conEjemplares.getId())).isTrue();
		assertThat(ejemplares.existsByLibroId(libro().getId())).isFalse();
	}

	@Test
	void persistsAndReadsBackWhatTheEntitiesMapToTheColumns() {
		Ejemplar ejemplar = ejemplar();
		ejemplar.darDeBaja();
		ejemplares.saveAndFlush(ejemplar);

		assertThat(ejemplares.findByCodigo(ejemplar.getCodigo())).map(Ejemplar::isActivo).contains(false);
		assertThat(jdbc.queryForObject("select activo from ejemplar where id = ?", Integer.class, ejemplar.getId())).isZero();
	}

}
