package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real threads, real transactions, real Oracle. Unlike the other tests these are not rolled back:
 * each call commits, because the point is what happens between simultaneous commits. Every test
 * releases its threads together with a barrier and checks both the outcome of each request and the
 * final state of the database. The data is deleted after each test.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrenciaTests extends BaseIntegracionTest {

	@Autowired
	private PrestamoService prestamosServicio;

	@Autowired
	private MultaService multasServicio;

	@Autowired
	private EjemplarService ejemplaresServicio;

	@Autowired
	private PlatformTransactionManager transacciones;

	private record Resultado(Object valor, Throwable error) {

		boolean ok() {
			return error == null;
		}

		ErrorCode codigo() {
			return error instanceof BusinessException negocio ? negocio.getCode() : null;
		}

	}

	@AfterEach
	void limpiar() {
		jdbc.update("delete from multa");
		jdbc.update("delete from prestamo");
		jdbc.update("delete from ejemplar");
		jdbc.update("delete from socio");
		jdbc.update("delete from libro");
	}

	/** Runs every task at the same instant and returns each outcome in order. */
	private List<Resultado> enParalelo(List<Callable<Object>> tareas) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
		CyclicBarrier salida = new CyclicBarrier(tareas.size());
		try {
			List<Future<Resultado>> futuros = new ArrayList<>();
			for (Callable<Object> tarea : tareas) {
				futuros.add(pool.submit(() -> {
					salida.await(30, TimeUnit.SECONDS);
					try {
						return new Resultado(tarea.call(), null);
					} catch (Throwable error) {
						return new Resultado(null, error);
					}
				}));
			}
			List<Resultado> resultados = new ArrayList<>();
			for (Future<Resultado> futuro : futuros) {
				resultados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			return resultados;
		} finally {
			pool.shutdownNow();
		}
	}

	private long exitos(List<Resultado> resultados) {
		return resultados.stream().filter(Resultado::ok).count();
	}

	private long fallosCon(List<Resultado> resultados, ErrorCode codigo) {
		return resultados.stream().filter(resultado -> resultado.codigo() == codigo).count();
	}

	private Callable<Object> prestar(Socio socio, Ejemplar ejemplar) {
		return () -> prestamosServicio.crear(new PrestamoRequest(socio.getId(), ejemplar.getId()));
	}

	private Prestamo vencido(Socio socio, Ejemplar ejemplar) {
		return prestamos.saveAndFlush(prestamo(ejemplar, socio, HOY.minusDays(19), 14));
	}

	@Test
	void eightMembersRacingForTheSameCopyLeaveExactlyOneLoan() throws Exception {
		for (int ronda = 0; ronda < 5; ronda++) {
			Ejemplar ejemplar = ejemplar();
			List<Callable<Object>> tareas = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				tareas.add(prestar(socio(), ejemplar));
			}

			List<Resultado> resultados = enParalelo(tareas);

			assertThat(exitos(resultados)).as("round %d", ronda).isEqualTo(1);
			assertThat(fallosCon(resultados, ErrorCode.COPY_ALREADY_ON_LOAN)).as("round %d", ronda).isEqualTo(7);
			assertThat(jdbc.queryForObject(
					"select count(*) from prestamo where ejemplar_id = ? and fecha_devolucion is null", Integer.class, ejemplar.getId()))
					.isEqualTo(1);
		}
	}

	@Test
	void sixSimultaneousLoansForOneMemberNeverExceedTheLimitOfFive() throws Exception {
		Socio socio = socio();
		List<Callable<Object>> tareas = new ArrayList<>();
		for (int index = 0; index < 6; index++) {
			tareas.add(prestar(socio, ejemplar()));
		}

		List<Resultado> resultados = enParalelo(tareas);

		assertThat(exitos(resultados)).isEqualTo(5);
		assertThat(fallosCon(resultados, ErrorCode.LOAN_LIMIT_REACHED)).isEqualTo(1);
		assertThat(prestamos.countBySocioIdAndFechaDevolucionIsNull(socio.getId())).isEqualTo(5);
	}

	@Test
	void twoSimultaneousReturnsOfTheSameLoanCreateOneFine() throws Exception {
		Prestamo prestamo = vencido(socio(), ejemplar());

		List<Resultado> resultados = enParalelo(List.of(
				() -> prestamosServicio.devolver(prestamo.getId()),
				() -> prestamosServicio.devolver(prestamo.getId())));

		assertThat(exitos(resultados)).isEqualTo(1);
		assertThat(fallosCon(resultados, ErrorCode.LOAN_ALREADY_RETURNED)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from multa where prestamo_id = ?", Integer.class, prestamo.getId())).isEqualTo(1);
	}

	/**
	 * An invariant check, not a proof that the member lock in a return is needed. Removing that lock
	 * does not break it: with the current order of the checks the race is already safe in READ
	 * COMMITTED. The test guards the rule if the checks are ever reordered or changed.
	 */
	@Test
	void aReturnAndANewLoanForTheSameMemberCanNeverBothSucceed() throws Exception {
		Set<ErrorCode> vistos = EnumSet.noneOf(ErrorCode.class);
		for (int ronda = 0; ronda < 10; ronda++) {
			Socio socio = socio();
			Prestamo atrasado = vencido(socio, ejemplar());
			Ejemplar libre = ejemplar();

			List<Resultado> resultados = enParalelo(List.of(
					() -> prestamosServicio.devolver(atrasado.getId()),
					prestar(socio, libre)));

			Resultado devolucion = resultados.get(0);
			Resultado prestamoNuevo = resultados.get(1);
			assertThat(devolucion.ok()).as("round %d: the return", ronda).isTrue();
			assertThat(prestamoNuevo.ok()).as("round %d: the new loan must be refused", ronda).isFalse();
			assertThat(prestamoNuevo.codigo())
					.as("round %d", ronda)
					.isIn(ErrorCode.MEMBER_HAS_OVERDUE_LOAN, ErrorCode.MEMBER_HAS_PENDING_FINE);
			vistos.add(prestamoNuevo.codigo());
			assertThat(prestamos.countBySocioIdAndFechaDevolucionIsNull(socio.getId())).isZero();
		}
		assertThat(vistos).isNotEmpty();
	}

	@Test
	void retiringACopyAndLendingItAtTheSameTimeNeverLeavesALoanedRetiredCopy() throws Exception {
		for (int ronda = 0; ronda < 10; ronda++) {
			Socio socio = socio();
			Ejemplar ejemplar = ejemplar();

			List<Resultado> resultados = enParalelo(List.of(
					() -> ejemplaresServicio.darDeBaja(ejemplar.getId()),
					prestar(socio, ejemplar)));

			Resultado baja = resultados.get(0);
			Resultado prestamo = resultados.get(1);
			assertThat(baja.ok() ^ prestamo.ok()).as("round %d: exactly one wins", ronda).isTrue();
			if (baja.ok()) {
				assertThat(prestamo.codigo()).isEqualTo(ErrorCode.COPY_INACTIVE);
			} else {
				assertThat(baja.codigo()).isEqualTo(ErrorCode.COPY_HAS_ACTIVE_LOAN);
			}
			boolean prestado = prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId());
			boolean activo = ejemplares.findById(ejemplar.getId()).orElseThrow().isActivo();
			assertThat(prestado && !activo).as("round %d: a loaned copy must be active", ronda).isFalse();
		}
	}

	@Test
	void twoSimultaneousRenewalsWithTwoUsedLeaveExactlyThreeRenewals() throws Exception {
		PrestamoResponse creado = prestamosServicio.crear(new PrestamoRequest(socio().getId(), ejemplar().getId()));
		prestamosServicio.renovar(creado.id());
		prestamosServicio.renovar(creado.id());

		List<Resultado> resultados = enParalelo(List.of(
				() -> prestamosServicio.renovar(creado.id()),
				() -> prestamosServicio.renovar(creado.id())));

		assertThat(exitos(resultados)).isEqualTo(1);
		assertThat(fallosCon(resultados, ErrorCode.RENEWAL_LIMIT_REACHED)).isEqualTo(1);
		assertThat(prestamos.findById(creado.id()).orElseThrow().getRenovaciones()).isEqualTo(3);
	}

	@Test
	void twoSimultaneousPaymentsOfTheSameFineSucceedOnlyOnce() throws Exception {
		Prestamo atrasado = vencido(socio(), ejemplar());
		Long multaId = prestamosServicio.devolver(atrasado.getId()).multa().id();

		List<Resultado> resultados = enParalelo(List.of(
				() -> multasServicio.pagar(multaId),
				() -> multasServicio.pagar(multaId)));

		assertThat(exitos(resultados)).isEqualTo(1);
		assertThat(fallosCon(resultados, ErrorCode.FINE_ALREADY_PAID)).isEqualTo(1);
	}

	@Test
	void aLockHeldTooLongEndsInATimeoutInsteadOfWaitingForever() throws Exception {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();
		CountDownLatch bloqueado = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		ExecutorService retenedor = Executors.newSingleThreadExecutor();
		try {
			Future<?> bloqueo = retenedor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
				socios.findByIdForUpdate(socio.getId());
				bloqueado.countDown();
				try {
					soltar.await(30, TimeUnit.SECONDS);
				} catch (InterruptedException error) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

			long inicio = System.nanoTime();
			assertThatThrownBy(() -> prestamosServicio.crear(new PrestamoRequest(socio.getId(), ejemplar.getId())))
					.isInstanceOfAny(PessimisticLockingFailureException.class, LockTimeoutException.class, PessimisticLockException.class);
			long esperaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

			assertThat(esperaMs).as("it waited about the 5 seconds of the lock timeout").isBetween(3_500L, 15_000L);
			soltar.countDown();
			bloqueo.get(10, TimeUnit.SECONDS);
			assertThat(prestamos.existsByEjemplarId(ejemplar.getId())).isFalse();
		} finally {
			soltar.countDown();
			retenedor.shutdownNow();
		}
	}

	@Test
	void afterTheLockIsReleasedTheSameRequestSucceeds() {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();

		assertThat(prestamosServicio.crear(new PrestamoRequest(socio.getId(), ejemplar.getId())).activo()).isTrue();
	}

}
