package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The loan rules of docs/business-rules.md against real Oracle and a frozen clock (today is
 * 2026-10-19). Boundaries are tested on both sides: the 5th loan passes and the 6th does not,
 * score 50 passes and 49 does not, due today is on time and due yesterday is not.
 */
class PrestamoServiceTests extends BaseIntegracionTest {

	@Autowired
	private PrestamoService servicio;

	@Autowired
	private ScoreCalculator score;

	private PrestamoResponse prestar(Socio socio, Ejemplar ejemplar) {
		return servicio.crear(new PrestamoRequest(socio.getId(), ejemplar.getId()));
	}

	private Prestamo guardar(Prestamo prestamo) {
		return prestamos.saveAndFlush(prestamo);
	}

	/** An active loan that started {@code haceDias} days ago with the normal 14-day term. */
	private Prestamo activo(Socio socio, int haceDias) {
		return guardar(prestamo(ejemplar(), socio, HOY.minusDays(haceDias), 14));
	}

	/** A returned loan whose return was {@code diasTarde} days after its due date. */
	private Prestamo devueltoTarde(Socio socio, int diasTarde) {
		Prestamo prestamo = prestamo(ejemplar(), socio, HOY.minusDays(100), 14);
		prestamo.devolver(prestamo.getFechaVencimiento().plusDays(diasTarde));
		return guardar(prestamo);
	}

	private Multa multaPendiente(Prestamo prestamo) {
		return multas.saveAndFlush(
				Multa.builder().prestamo(prestamo).diasRetraso(5).monto(new BigDecimal("5.00")).fechaGeneracion(HOY).build());
	}

	// ---------------------------------------------------------------- create

	@Test
	void createsALoanWithDatesTakenFromTheServer() {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();

		PrestamoResponse creado = prestar(socio, ejemplar);

		assertThat(creado.id()).isNotNull();
		assertThat(creado.socioId()).isEqualTo(socio.getId());
		assertThat(creado.ejemplarId()).isEqualTo(ejemplar.getId());
		assertThat(creado.fechaPrestamo()).isEqualTo(HOY);
		assertThat(creado.fechaVencimiento()).isEqualTo(HOY.plusDays(14));
		assertThat(creado.fechaDevolucion()).isNull();
		assertThat(creado.renovaciones()).isZero();
		assertThat(creado.activo()).isTrue();
		assertThat(creado.diasRetraso()).isZero();
		assertThat(creado.alertas()).isEmpty();
		assertThat(creado.multa()).isNull();
		assertThat(prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId())).isTrue();
	}

	@Test
	void anUnknownMemberOrCopyIsNotFoundAndTheMemberIsCheckedFirst() {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();

		assertError(() -> servicio.crear(new PrestamoRequest(-1L, ejemplar.getId())), ErrorCode.MEMBER_NOT_FOUND);
		assertError(() -> servicio.crear(new PrestamoRequest(socio.getId(), -1L)), ErrorCode.COPY_NOT_FOUND);
		assertError(() -> servicio.crear(new PrestamoRequest(-1L, -1L)), ErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void anInactiveMemberCannotBorrow() {
		Socio socio = socio();
		socio.desactivar();
		socios.saveAndFlush(socio);

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_INACTIVE);
	}

	@Test
	void aRetiredCopyCannotBeLent() {
		Ejemplar ejemplar = ejemplar();
		ejemplar.darDeBaja();
		ejemplares.saveAndFlush(ejemplar);

		assertError(() -> prestar(socio(), ejemplar), ErrorCode.COPY_INACTIVE);
	}

	@Test
	void aCopyAlreadyOnLoanCannotBeLentAgain() {
		Ejemplar ejemplar = ejemplar();
		prestar(socio(), ejemplar);

		assertError(() -> prestar(socio(), ejemplar), ErrorCode.COPY_ALREADY_ON_LOAN);
	}

	@Test
	void aReturnedCopyCanBeLentAgain() {
		Ejemplar ejemplar = ejemplar();
		PrestamoResponse primero = prestar(socio(), ejemplar);
		servicio.devolver(primero.id());

		assertThat(prestar(socio(), ejemplar).activo()).isTrue();
	}

	@Test
	void anOverdueLoanBlocksNewLoans() {
		Socio socio = socio();
		activo(socio, 15);

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_HAS_OVERDUE_LOAN);
	}

	@Test
	void aLoanDueTodayIsStillOnTimeAndDoesNotBlock() {
		Socio socio = socio();
		activo(socio, 14);

		assertThat(prestar(socio, ejemplar()).activo()).isTrue();
	}

	@Test
	void aPendingFineBlocksNewLoans() {
		Socio socio = socio();
		multaPendiente(devueltoTarde(socio, 5));

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_HAS_PENDING_FINE);
	}

	@Test
	void aPaidFineNoLongerBlocks() {
		Socio socio = socio();
		Multa multa = multaPendiente(devueltoTarde(socio, 5));
		multa.pagar(HOY);
		multas.saveAndFlush(multa);

		assertThat(prestar(socio, ejemplar()).activo()).isTrue();
	}

	@Test
	void aScoreOfFiftyMayBorrowAndFortyNineMayNot() {
		Socio enElLimite = socio();
		devueltoTarde(enElLimite, 40);
		Socio pordebajo = socio();
		devueltoTarde(pordebajo, 41);

		assertThat(score.calcular(prestamos.findBySocioId(enElLimite.getId()), HOY)).isEqualTo(50);
		assertThat(score.calcular(prestamos.findBySocioId(pordebajo.getId()), HOY)).isEqualTo(49);
		assertThat(prestar(enElLimite, ejemplar()).activo()).isTrue();
		assertError(() -> prestar(pordebajo, ejemplar()), ErrorCode.MEMBER_BLOCKED_BY_SCORE);
	}

	@Test
	void theFifthLoanPassesAndTheSixthIsRefusedUntilOneIsReturned() {
		Socio socio = socio();
		PrestamoResponse primero = null;
		for (int index = 0; index < 5; index++) {
			PrestamoResponse creado = prestar(socio, ejemplar());
			primero = primero == null ? creado : primero;
		}

		assertThatThrownBy(() -> prestar(socio, ejemplar()))
				.isInstanceOfSatisfying(BusinessException.class, error -> {
					assertThat(error.getCode()).isEqualTo(ErrorCode.LOAN_LIMIT_REACHED);
					assertThat(error.getMessage()).isEqualTo("The member already has 5 active loans.");
				});
		assertThat(prestamos.countBySocioIdAndFechaDevolucionIsNull(socio.getId())).isEqualTo(5);

		servicio.devolver(primero.id());
		assertThat(prestar(socio, ejemplar()).activo()).isTrue();
	}

	@Test
	void nothingIsWrittenWhenACheckFails() {
		Socio socio = socio();
		activo(socio, 15);
		long antes = prestamos.count();

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_HAS_OVERDUE_LOAN);

		assertThat(prestamos.count()).isEqualTo(antes);
	}

	// ---------------------------------------------- order of the checks

	@Test
	void anInactiveMemberIsReportedBeforeACopyAlreadyOnLoan() {
		Ejemplar ejemplar = ejemplar();
		prestar(socio(), ejemplar);
		Socio inactivo = socio();
		inactivo.desactivar();
		socios.saveAndFlush(inactivo);

		assertError(() -> prestar(inactivo, ejemplar), ErrorCode.MEMBER_INACTIVE);
	}

	@Test
	void aCopyAlreadyOnLoanIsReportedBeforeAnOverdueLoan() {
		Ejemplar ocupado = ejemplar();
		prestar(socio(), ocupado);
		Socio moroso = socio();
		activo(moroso, 15);

		assertError(() -> prestar(moroso, ocupado), ErrorCode.COPY_ALREADY_ON_LOAN);
	}

	@Test
	void anOverdueLoanIsReportedBeforeAPendingFine() {
		Socio socio = socio();
		multaPendiente(devueltoTarde(socio, 5));
		activo(socio, 15);

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_HAS_OVERDUE_LOAN);
	}

	@Test
	void aPendingFineIsReportedBeforeALowScore() {
		Socio socio = socio();
		multaPendiente(devueltoTarde(socio, 60));

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_HAS_PENDING_FINE);
	}

	@Test
	void aLowScoreIsReportedBeforeTheLoanLimit() {
		Socio socio = socio();
		devueltoTarde(socio, 41);
		for (int index = 0; index < 5; index++) {
			activo(socio, 3);
		}

		assertError(() -> prestar(socio, ejemplar()), ErrorCode.MEMBER_BLOCKED_BY_SCORE);
	}

	// ----------------------------------------------------------------- renew

	@Test
	void renewingAddsATermToTheCurrentDueDateNotToToday() {
		Prestamo prestamo = activo(socio(), 11);
		assertThat(prestamo.getFechaVencimiento()).isEqualTo(HOY.plusDays(3));

		PrestamoResponse renovado = servicio.renovar(prestamo.getId());

		assertThat(renovado.fechaVencimiento()).isEqualTo(HOY.plusDays(17));
		assertThat(renovado.fechaPrestamo()).isEqualTo(HOY.minusDays(11));
		assertThat(renovado.renovaciones()).isEqualTo(1);
		assertThat(renovado.activo()).isTrue();
	}

	@Test
	void aLoanCanBeRenewedThreeTimesAndTheFourthIsRefused() {
		PrestamoResponse creado = prestar(socio(), ejemplar());

		servicio.renovar(creado.id());
		servicio.renovar(creado.id());
		PrestamoResponse tercera = servicio.renovar(creado.id());

		assertThat(tercera.renovaciones()).isEqualTo(3);
		assertThat(tercera.fechaVencimiento()).isEqualTo(HOY.plusDays(14 + 3 * 14));
		assertError(() -> servicio.renovar(creado.id()), ErrorCode.RENEWAL_LIMIT_REACHED);
		assertThat(servicio.obtener(creado.id()).renovaciones()).isEqualTo(3);
	}

	@Test
	void afterReturningACopyANewLoanStartsAgainAtZeroRenewals() {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();
		PrestamoResponse primero = prestar(socio, ejemplar);
		servicio.renovar(primero.id());
		servicio.renovar(primero.id());
		servicio.renovar(primero.id());
		servicio.devolver(primero.id());

		PrestamoResponse segundo = prestar(socio, ejemplar);

		assertThat(segundo.id()).isNotEqualTo(primero.id());
		assertThat(segundo.renovaciones()).isZero();
		assertThat(segundo.fechaVencimiento()).isEqualTo(HOY.plusDays(14));
	}

	@Test
	void aLoanDueTodayCanStillBeRenewedButAnOverdueOneCannot() {
		Prestamo venceHoy = activo(socio(), 14);
		Prestamo vencido = activo(socio(), 15);

		assertThat(servicio.renovar(venceHoy.getId()).fechaVencimiento()).isEqualTo(HOY.plusDays(14));
		assertError(() -> servicio.renovar(vencido.getId()), ErrorCode.RENEWAL_NOT_ALLOWED_OVERDUE);
	}

	@Test
	void aReturnedLoanCannotBeRenewed() {
		PrestamoResponse creado = prestar(socio(), ejemplar());
		servicio.devolver(creado.id());

		assertError(() -> servicio.renovar(creado.id()), ErrorCode.LOAN_ALREADY_RETURNED);
	}

	@Test
	void anInactiveMemberCannotRenew() {
		Socio socio = socio();
		Prestamo prestamo = activo(socio, 3);
		socio.desactivar();
		socios.saveAndFlush(socio);

		assertError(() -> servicio.renovar(prestamo.getId()), ErrorCode.MEMBER_INACTIVE);
	}

	@Test
	void anUnknownLoanCannotBeRenewed() {
		assertError(() -> servicio.renovar(-1L), ErrorCode.LOAN_NOT_FOUND);
	}

	@Test
	void aPendingFineAndALowScoreDoNotBlockARenewal() {
		Socio socio = socio();
		multaPendiente(devueltoTarde(socio, 60));
		Prestamo vigente = activo(socio, 3);

		assertThat(servicio.renovar(vigente.getId()).renovaciones()).isEqualTo(1);
	}

	// ---------------------------------------------------------------- return

	@Test
	void returningOnTimeCreatesNoFine() {
		PrestamoResponse creado = prestar(socio(), ejemplar());

		PrestamoResponse devuelto = servicio.devolver(creado.id());

		assertThat(devuelto.activo()).isFalse();
		assertThat(devuelto.fechaDevolucion()).isEqualTo(HOY);
		assertThat(devuelto.diasRetraso()).isZero();
		assertThat(devuelto.multa()).isNull();
		assertThat(devuelto.alertas()).isEmpty();
		assertThat(multas.findByPrestamoId(creado.id())).isEmpty();
	}

	@Test
	void returningOnTheDueDayIsOnTime() {
		Prestamo prestamo = activo(socio(), 14);

		PrestamoResponse devuelto = servicio.devolver(prestamo.getId());

		assertThat(devuelto.multa()).isNull();
		assertThat(devuelto.diasRetraso()).isZero();
	}

	@Test
	void returningLateCreatesAPendingFineOfOneSolPerDay() {
		Socio socio = socio();
		Prestamo prestamo = activo(socio, 19);

		PrestamoResponse devuelto = servicio.devolver(prestamo.getId());

		assertThat(devuelto.diasRetraso()).isEqualTo(5);
		assertThat(devuelto.multa()).isNotNull();
		assertThat(devuelto.multa().monto()).isEqualByComparingTo("5.00");
		assertThat(devuelto.multa().diasRetraso()).isEqualTo(5);
		assertThat(devuelto.multa().estado()).isEqualTo(EstadoMulta.PENDING);
		assertThat(devuelto.multa().fechaGeneracion()).isEqualTo(HOY);
		assertThat(devuelto.multa().fechaPago()).isNull();
		assertThat(multas.findByPrestamoId(prestamo.getId())).isPresent();
		assertThat(prestamos.findById(prestamo.getId()).orElseThrow().getFechaDevolucion()).isEqualTo(HOY);
	}

	@Test
	void aLateReturnLowersTheScoreByTheBasePlusTheDays() {
		Socio socio = socio();
		Prestamo prestamo = activo(socio, 19);
		assertThat(score.calcular(prestamos.findBySocioId(socio.getId()), HOY)).isEqualTo(85);

		servicio.devolver(prestamo.getId());

		assertThat(score.calcular(prestamos.findBySocioId(socio.getId()), HOY)).isEqualTo(85);
		assertThat(score.calcular(prestamos.findBySocioId(socio.getId()), HOY.plusDays(30))).isEqualTo(85);
	}

	@Test
	void aLoanCannotBeReturnedTwiceAndTheFineIsNotDuplicated() {
		Prestamo prestamo = activo(socio(), 19);
		servicio.devolver(prestamo.getId());

		assertError(() -> servicio.devolver(prestamo.getId()), ErrorCode.LOAN_ALREADY_RETURNED);
		assertThat(multas.findAll().stream().filter(multa -> multa.getPrestamo().getId().equals(prestamo.getId()))).hasSize(1);
	}

	@Test
	void anUnknownLoanCannotBeReturned() {
		assertError(() -> servicio.devolver(-1L), ErrorCode.LOAN_NOT_FOUND);
	}

	@Test
	void aReturnIsAcceptedEvenIfTheMemberAndTheCopyWereDeactivatedMeanwhile() {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();
		PrestamoResponse creado = prestar(socio, ejemplar);
		socio.desactivar();
		socios.saveAndFlush(socio);
		ejemplar.darDeBaja();
		ejemplares.saveAndFlush(ejemplar);

		assertThat(servicio.devolver(creado.id()).activo()).isFalse();
	}

	@Test
	void aReturnUnblocksTheMemberWhoWasLimitedByActiveLoans() {
		Socio socio = socio();
		PrestamoResponse primero = prestar(socio, ejemplar());
		for (int index = 0; index < 4; index++) {
			prestar(socio, ejemplar());
		}
		assertError(() -> prestar(socio, ejemplar()), ErrorCode.LOAN_LIMIT_REACHED);

		servicio.devolver(primero.id());

		assertThat(prestar(socio, ejemplar()).activo()).isTrue();
	}

	// ------------------------------------------------------ read and list

	@Test
	void aLoanDueInTwoDaysCarriesADueSoonAlert() {
		Prestamo prestamo = activo(socio(), 12);

		PrestamoResponse respuesta = servicio.obtener(prestamo.getId());

		assertThat(respuesta.alertas()).extracting(alerta -> alerta.code()).containsExactly(AlertaCodigo.DUE_SOON);
	}

	@Test
	void anOverdueLoanShowsTheFineAccruedSoFar() {
		Prestamo prestamo = activo(socio(), 17);

		PrestamoResponse respuesta = servicio.obtener(prestamo.getId());

		assertThat(respuesta.diasRetraso()).isEqualTo(3);
		assertThat(respuesta.alertas()).hasSize(1);
		assertThat(respuesta.alertas().get(0).code()).isEqualTo(AlertaCodigo.OVERDUE);
		assertThat(respuesta.alertas().get(0).message()).isEqualTo("Overdue by 3 day(s). Fine so far: S/ 3.00.");
		assertThat(respuesta.multa()).isNull();
	}

	@Test
	void aLoanReturnedLateShowsItsFineAndNoAlerts() {
		Prestamo prestamo = activo(socio(), 19);
		servicio.devolver(prestamo.getId());

		PrestamoResponse respuesta = servicio.obtener(prestamo.getId());

		assertThat(respuesta.alertas()).isEmpty();
		assertThat(respuesta.multa().monto()).isEqualByComparingTo("5.00");
	}

	@Test
	void anUnknownLoanIsNotFound() {
		assertError(() -> servicio.obtener(-1L), ErrorCode.LOAN_NOT_FOUND);
	}

	@Test
	void listsByMemberAndByActiveOrReturnedWithTheFineOfEachReturnedLoan() {
		Socio socio = socio();
		Prestamo uno = activo(socio, 3);
		Prestamo dos = activo(socio, 5);
		Prestamo devuelto = activo(socio, 19);
		servicio.devolver(devuelto.getId());
		activo(socio(), 3);

		PaginaDto<PrestamoResponse> todos = servicio.listar(socio.getId(), null, 0, 20);
		PaginaDto<PrestamoResponse> vigentes = servicio.listar(socio.getId(), true, 0, 20);
		PaginaDto<PrestamoResponse> cerrados = servicio.listar(socio.getId(), false, 0, 20);

		assertThat(todos.items()).extracting(PrestamoResponse::id).containsExactly(uno.getId(), dos.getId(), devuelto.getId());
		assertThat(vigentes.items()).extracting(PrestamoResponse::id).containsExactly(uno.getId(), dos.getId());
		assertThat(cerrados.items()).extracting(PrestamoResponse::id).containsExactly(devuelto.getId());
		assertThat(cerrados.items().get(0).multa().monto()).isEqualByComparingTo("5.00");
		assertThat(vigentes.items()).allMatch(item -> item.multa() == null);
	}

	@Test
	void pagesTheLoansInIdOrder() {
		Socio socio = socio();
		List<Long> ids = List.of(activo(socio, 1).getId(), activo(socio, 2).getId(), activo(socio, 3).getId());

		PaginaDto<PrestamoResponse> primera = servicio.listar(socio.getId(), null, 0, 2);
		PaginaDto<PrestamoResponse> segunda = servicio.listar(socio.getId(), null, 1, 2);

		assertThat(primera.items()).extracting(PrestamoResponse::id).containsExactly(ids.get(0), ids.get(1));
		assertThat(segunda.items()).extracting(PrestamoResponse::id).containsExactly(ids.get(2));
		assertThat(primera.totalElements()).isEqualTo(3);
		assertThat(primera.totalPages()).isEqualTo(2);
	}

	@Test
	void withoutFiltersItListsEveryLoan() {
		activo(socio(), 3);

		assertThat(servicio.listar(null, null, 0, 100).totalElements()).isGreaterThanOrEqualTo(1);
	}

}
