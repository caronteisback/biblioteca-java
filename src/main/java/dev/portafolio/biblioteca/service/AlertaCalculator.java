package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import dev.portafolio.biblioteca.dto.Alerta;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.entity.Prestamo;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Alerts are calculated on every query from the current data and are never stored. */
@Component
@RequiredArgsConstructor
public class AlertaCalculator {

	private final BibliotecaProperties properties;
	private final MultaCalculator multas;

	/** OVERDUE (with the fine accrued so far) or DUE_SOON for an active loan; none once returned. */
	public List<Alerta> delPrestamo(Prestamo prestamo, LocalDate hoy) {
		if (!prestamo.isActivo()) {
			return List.of();
		}
		if (prestamo.isVencido(hoy)) {
			int dias = prestamo.diasRetraso(hoy);
			return List.of(new Alerta(AlertaCodigo.OVERDUE,
					"Overdue by %d day(s). Fine so far: S/ %s.".formatted(dias, multas.calcular(dias).toPlainString())));
		}
		long faltan = ChronoUnit.DAYS.between(hoy, prestamo.getFechaVencimiento());
		if (faltan <= properties.prestamo().alertaDiasPrevios()) {
			return List.of(new Alerta(AlertaCodigo.DUE_SOON, faltan == 0 ? "Due today." : "Due in %d day(s).".formatted(faltan)));
		}
		return List.of();
	}

	/** PENDING_FINE and LOW_SCORE for a member. */
	public List<Alerta> delSocio(int score, boolean multaPendiente) {
		List<Alerta> alertas = new ArrayList<>();
		if (multaPendiente) {
			alertas.add(new Alerta(AlertaCodigo.PENDING_FINE, "The member has an unpaid fine."));
		}
		if (score < properties.score().umbralBloqueo()) {
			alertas.add(new Alerta(AlertaCodigo.LOW_SCORE,
					"Score %d is below %d: no new loans.".formatted(score, properties.score().umbralBloqueo())));
		}
		return alertas;
	}

}
