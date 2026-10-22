package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.CasoDeAtencion;
import dev.portafolio.biblioteca.dto.PanelResponse;
import dev.portafolio.biblioteca.dto.SemanaDePrestamos;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The figures of the librarian's home against Oracle. The clock is frozen at Monday 2026-10-19. Other tests may leave
 * rows behind, so a figure is checked as the difference between the dashboard before and after the data of the test;
 * the rankings use data far more extreme than anything else, so they cannot be disturbed.
 */
class PanelServiceTests extends BaseIntegracionTest {

	@Autowired
	private PanelService panel;

	@Autowired
	private EntityManagerFactory fabrica;

	@PersistenceContext
	private EntityManager em;

	private Prestamo activo(Ejemplar ejemplar, Socio socio, LocalDate vence) {
		return prestamos.saveAndFlush(prestamo(ejemplar, socio, vence.minusDays(14), 14));
	}

	private Prestamo devuelto(Ejemplar ejemplar, Socio socio, LocalDate desde) {
		Prestamo prestamo = prestamo(ejemplar, socio, desde, 14);
		prestamo.devolver(desde.plusDays(3));
		return prestamos.saveAndFlush(prestamo);
	}

	private Multa multa(Prestamo prestamo, String monto, boolean pagada) {
		Multa multa = Multa.builder().prestamo(prestamo).diasRetraso(1).monto(new BigDecimal(monto)).fechaGeneracion(HOY).build();
		if (pagada) {
			multa.pagar(HOY);
		}
		return multas.saveAndFlush(multa);
	}

	@Test
	void todayIsTheDayOfTheLibraryClockAndTheWindowComesFromTheConfiguration() {
		PanelResponse respuesta = panel.resumen();

		assertThat(respuesta.fecha()).isEqualTo(HOY);
		assertThat(respuesta.prestamos().diasPorVencer()).isEqualTo(7);
	}

	@Test
	void countsActiveOverdueAndSoonToBeDueLoansAndIgnoresReturnedOnes() {
		PanelResponse antes = panel.resumen();
		Socio socio = socio();
		activo(ejemplar(), socio, HOY.plusDays(30));
		activo(ejemplar(), socio, HOY);
		activo(ejemplar(), socio, HOY.plusDays(7));
		activo(ejemplar(), socio, HOY.plusDays(8));
		activo(ejemplar(), socio, HOY.minusDays(1));
		devuelto(ejemplar(), socio, HOY.minusDays(40));

		PanelResponse despues = panel.resumen();

		assertThat(despues.prestamos().activos() - antes.prestamos().activos()).as("active").isEqualTo(5);
		assertThat(despues.prestamos().vencidos() - antes.prestamos().vencidos()).as("overdue: due before today").isEqualTo(1);
		assertThat(despues.prestamos().porVencer() - antes.prestamos().porVencer()).as("due soon: today to 7 days ahead").isEqualTo(2);
	}

	@Test
	void countsPendingFinesTheirTotalAndTheMembersThatOweThem() {
		PanelResponse antes = panel.resumen();
		Socio debeDos = socio();
		Socio debeUna = socio();
		multa(devuelto(ejemplar(), debeDos, HOY.minusDays(60)), "5.00", false);
		multa(devuelto(ejemplar(), debeDos, HOY.minusDays(50)), "2.50", false);
		multa(devuelto(ejemplar(), debeUna, HOY.minusDays(40)), "11.00", false);
		multa(devuelto(ejemplar(), debeUna, HOY.minusDays(30)), "99.00", true);

		PanelResponse despues = panel.resumen();

		assertThat(despues.multas().pendientes() - antes.multas().pendientes()).isEqualTo(3);
		assertThat(despues.multas().montoPendiente().subtract(antes.multas().montoPendiente())).isEqualByComparingTo("18.50");
		assertThat(despues.multas().sociosConMultaPendiente() - antes.multas().sociosConMultaPendiente()).isEqualTo(2);
	}

	@Test
	void countsTheCopiesThatCanBeLentAndTheMembersThatCanBorrow() {
		PanelResponse antes = panel.resumen();
		ejemplar();
		Ejemplar retirado = ejemplar();
		retirado.darDeBaja();
		ejemplares.saveAndFlush(retirado);
		socio();
		Socio desactivado = socio();
		desactivado.desactivar();
		socios.saveAndFlush(desactivado);

		PanelResponse despues = panel.resumen();

		assertThat(despues.ejemplaresActivos() - antes.ejemplaresActivos()).isEqualTo(1);
		assertThat(despues.sociosActivos() - antes.sociosActivos()).isEqualTo(1);
	}

	@Test
	void givesSixWeeksStartingOnMondayOldestFirstAndCountsEachLoanInItsWeek() {
		List<SemanaDePrestamos> antes = panel.resumen().prestamosPorSemana();
		Socio socio = socio();
		LocalDate estaSemana = HOY; // Monday
		devuelto(ejemplar(), socio, estaSemana);
		devuelto(ejemplar(), socio, estaSemana.minusDays(1)); // Sunday: last week
		devuelto(ejemplar(), socio, estaSemana.minusDays(1));
		devuelto(ejemplar(), socio, estaSemana.minusWeeks(5)); // Monday of the oldest week
		devuelto(ejemplar(), socio, estaSemana.minusWeeks(5).minusDays(1)); // Sunday before it: out of the series

		List<SemanaDePrestamos> despues = panel.resumen().prestamosPorSemana();

		assertThat(despues).hasSize(6);
		assertThat(despues.get(5).semana()).isEqualTo(HOY);
		assertThat(despues.get(0).semana()).isEqualTo(HOY.minusWeeks(5));
		assertThat(despues).allSatisfy(semana -> assertThat(semana.semana().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
		assertThat(despues).extracting(SemanaDePrestamos::semana).isSorted();
		assertThat(delta(despues, antes, 5)).as("this week").isEqualTo(1);
		assertThat(delta(despues, antes, 4)).as("last week").isEqualTo(2);
		assertThat(delta(despues, antes, 0)).as("oldest week").isEqualTo(1);
		assertThat(delta(despues, antes, 1) + delta(despues, antes, 2) + delta(despues, antes, 3)).as("the weeks in between").isZero();
	}

	private static long delta(List<SemanaDePrestamos> despues, List<SemanaDePrestamos> antes, int i) {
		return despues.get(i).prestamos() - antes.get(i).prestamos();
	}

	@Test
	void everyWeekOfTheSeriesIsThereEvenWithoutLoans() {
		assertThat(panel.resumen().prestamosPorSemana()).extracting(SemanaDePrestamos::semana)
				.containsExactly(HOY.minusWeeks(5), HOY.minusWeeks(4), HOY.minusWeeks(3), HOY.minusWeeks(2), HOY.minusWeeks(1), HOY);
	}

	@Test
	void ranksTheMostBorrowedBooksOfTheLast90DaysMostBorrowedFirst() {
		Socio socio = socio();
		Libro masPedido = libro();
		Libro segundo = libro();
		Ejemplar deMasPedido = ejemplar(masPedido);
		Ejemplar deSegundo = ejemplar(segundo);
		for (int i = 0; i < 60; i++) {
			devuelto(deMasPedido, socio, HOY.minusDays(i % 80));
		}
		for (int i = 0; i < 50; i++) {
			devuelto(deSegundo, socio, HOY.minusDays(i % 80));
		}
		// Loans older than 90 days do not count, however many there are.
		for (int i = 0; i < 100; i++) {
			devuelto(deSegundo, socio, HOY.minusDays(100 + i));
		}

		var ranking = panel.resumen().masPrestados();

		assertThat(ranking).hasSizeLessThanOrEqualTo(5);
		assertThat(ranking.get(0).libroId()).isEqualTo(masPedido.getId());
		assertThat(ranking.get(0).prestamos()).isEqualTo(60);
		assertThat(ranking.get(1).libroId()).isEqualTo(segundo.getId());
		assertThat(ranking.get(1).prestamos()).as("only the loans of the last 90 days").isEqualTo(50);
		assertThat(ranking.get(0).titulo()).isEqualTo(masPedido.getTitulo());
	}

	@Test
	void listsOverdueLoansFirstTheMostLateFirstAndThenTheOnesDueSoonTheSoonestFirst() {
		Socio ana = socio();
		Socio berta = socio();
		Prestamo masVencido = activo(ejemplar(), ana, HOY.minusDays(4000));
		Prestamo vencido = activo(ejemplar(), berta, HOY.minusDays(3500));
		Prestamo otroVencido = activo(ejemplar(), ana, HOY.minusDays(3000));
		Prestamo pronto = activo(ejemplar(), berta, HOY.plusDays(1));
		Prestamo mas = activo(ejemplar(), ana, HOY.plusDays(5));
		activo(ejemplar(), ana, HOY.plusDays(40)); // not soon: not listed
		devuelto(ejemplar(), ana, HOY.minusDays(5000)); // returned: not listed

		List<CasoDeAtencion> casos = panel.resumen().requierenAtencion();

		assertThat(casos).hasSize(5);
		assertThat(casos).extracting(CasoDeAtencion::prestamoId)
				.containsExactly(masVencido.getId(), vencido.getId(), otroVencido.getId(), pronto.getId(), mas.getId());
		assertThat(casos.get(0)).satisfies(caso -> {
			assertThat(caso.alerta()).isEqualTo(AlertaCodigo.OVERDUE);
			assertThat(caso.diasRetraso()).isEqualTo(4000);
			assertThat(caso.nombreSocio()).isEqualTo(ana.getNombre());
			assertThat(caso.socioId()).isEqualTo(ana.getId());
			assertThat(caso.tituloLibro()).isEqualTo(masVencido.getEjemplar().getLibro().getTitulo());
			assertThat(caso.codigoEjemplar()).isEqualTo(masVencido.getEjemplar().getCodigo());
			assertThat(caso.fechaVencimiento()).isEqualTo(HOY.minusDays(4000));
		});
		assertThat(casos.get(3)).satisfies(caso -> {
			assertThat(caso.alerta()).isEqualTo(AlertaCodigo.DUE_SOON);
			assertThat(caso.diasRetraso()).isZero();
		});
	}

	@Test
	void theNumberOfQueriesDoesNotGrowWithTheNumberOfLoansFinesOrBooks() {
		Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		long conPocos = consultas(estadisticas);

		Socio socio = socio();
		for (int i = 0; i < 12; i++) {
			Prestamo prestamo = devuelto(ejemplar(), socio, HOY.minusDays(i * 7));
			multa(prestamo, "3.00", i % 2 == 0);
			activo(ejemplar(), socio, HOY.plusDays(i % 9 - 3));
		}
		long conMuchos = consultas(estadisticas);

		assertThat(conMuchos).as("statements with many loans vs few").isEqualTo(conPocos);
		assertThat(conMuchos).as("a handful of aggregate queries").isLessThanOrEqualTo(10);
	}

	private long consultas(Statistics estadisticas) {
		em.flush();
		em.clear();
		estadisticas.clear();
		panel.resumen();
		return estadisticas.getPrepareStatementCount();
	}

}
