package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import dev.portafolio.biblioteca.entity.Prestamo;
import java.time.LocalDate;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reliability score of a member, derived from the loan history every time it is asked for.
 *
 * <pre>
 * penalty(loan) = base + days late x per-day      (only loans with delay above zero)
 * score         = max(0, initial - sum of penalties)
 * </pre>
 *
 * A returned loan counts its delay up to the return date; an active overdue loan counts it up to
 * today, so its penalty keeps growing until the copy is returned. The score can only go down:
 * history is never erased and returning on time adds nothing.
 */
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

	private final BibliotecaProperties properties;

	public int calcular(Collection<Prestamo> prestamos, LocalDate hoy) {
		BibliotecaProperties.Score score = properties.score();
		int perdidos = 0;
		for (Prestamo prestamo : prestamos) {
			int dias = prestamo.diasRetraso(hoy);
			if (dias > 0) {
				perdidos += score.penalizacionBase() + score.penalizacionPorDia() * dias;
			}
		}
		return Math.max(0, score.inicial() - perdidos);
	}

	public boolean estaBloqueado(int score) {
		return score < properties.score().umbralBloqueo();
	}

}
