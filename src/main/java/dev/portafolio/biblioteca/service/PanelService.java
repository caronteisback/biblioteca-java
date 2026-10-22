package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.CasoDeAtencion;
import dev.portafolio.biblioteca.dto.LibroMasPrestado;
import dev.portafolio.biblioteca.dto.PanelResponse;
import dev.portafolio.biblioteca.dto.ResumenDeMultas;
import dev.portafolio.biblioteca.dto.ResumenDePrestamos;
import dev.portafolio.biblioteca.dto.SemanaDePrestamos;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.repository.ConteoDeMultas;
import dev.portafolio.biblioteca.repository.ConteoDePrestamos;
import dev.portafolio.biblioteca.repository.EjemplarRepository;
import dev.portafolio.biblioteca.repository.MultaRepository;
import dev.portafolio.biblioteca.repository.PrestamoRepository;
import dev.portafolio.biblioteca.repository.PrestamosDelDia;
import dev.portafolio.biblioteca.repository.SocioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The figures of the librarian's home. Everything is counted by the database in a handful of queries whose number does
 * not depend on how many loans there are, and nothing is stored: like the score and the alerts, it is as of today.
 */
@Service
@RequiredArgsConstructor
public class PanelService {

	/** Weeks in the loans-per-week series, the current one included. */
	static final int SEMANAS = 6;
	/** Days back that the ranking of the most borrowed books looks at. */
	static final int DIAS_DEL_RANKING = 90;
	/** Rows of the ranking and of the list of loans that need attention. */
	static final int FILAS = 5;

	private final PrestamoRepository prestamos;
	private final MultaRepository multas;
	private final EjemplarRepository ejemplares;
	private final SocioRepository socios;
	private final BibliotecaProperties properties;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PanelResponse resumen() {
		LocalDate hoy = LocalDate.now(clock);
		int dias = properties.panel().diasPorVencer();
		LocalDate limite = hoy.plusDays(dias);

		ConteoDePrestamos activos = prestamos.contarActivos(hoy, limite);
		ConteoDeMultas pendientes = multas.contar(EstadoMulta.PENDING);
		BigDecimal monto = pendientes.monto() == null ? BigDecimal.ZERO : pendientes.monto();

		return new PanelResponse(
				hoy,
				new ResumenDePrestamos(activos.activos(), activos.vencidos(), activos.porVencer(), dias),
				ejemplares.countByActivoTrue(),
				socios.countByActivoTrue(),
				new ResumenDeMultas(pendientes.pendientes(), monto, pendientes.socios()),
				porSemana(hoy),
				prestamos.masPrestados(hoy.minusDays(DIAS_DEL_RANKING), PageRequest.of(0, FILAS)),
				requierenAtencion(hoy, limite));
	}

	/** One entry for each of the last weeks, oldest first, with zero for a week without loans. A week starts on Monday. */
	private List<SemanaDePrestamos> porSemana(LocalDate hoy) {
		LocalDate estaSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate primera = estaSemana.minusWeeks(SEMANAS - 1L);
		long[] totales = new long[SEMANAS];
		for (PrestamosDelDia dia : prestamos.contarPorDia(primera)) {
			int semana = (int) ChronoUnit.WEEKS.between(primera, dia.dia().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
			if (semana >= 0 && semana < SEMANAS) {
				totales[semana] += dia.total();
			}
		}
		List<SemanaDePrestamos> serie = new ArrayList<>(SEMANAS);
		for (int i = 0; i < SEMANAS; i++) {
			serie.add(new SemanaDePrestamos(primera.plusWeeks(i), totales[i]));
		}
		return serie;
	}

	/** Overdue loans first (the most late first), then the ones due soon (the soonest first): one ordering by due date does both. */
	private List<CasoDeAtencion> requierenAtencion(LocalDate hoy, LocalDate limite) {
		return prestamos.requierenAtencion(limite, PageRequest.of(0, FILAS)).stream()
				.map(prestamo -> caso(prestamo, hoy))
				.toList();
	}

	private static CasoDeAtencion caso(Prestamo prestamo, LocalDate hoy) {
		return new CasoDeAtencion(
				prestamo.getId(),
				prestamo.getSocio().getId(),
				prestamo.getSocio().getNombre(),
				prestamo.getEjemplar().getLibro().getTitulo(),
				prestamo.getEjemplar().getCodigo(),
				prestamo.getFechaVencimiento(),
				prestamo.diasRetraso(hoy),
				prestamo.isVencido(hoy) ? AlertaCodigo.OVERDUE : AlertaCodigo.DUE_SOON);
	}

}
