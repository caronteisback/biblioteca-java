package dev.portafolio.biblioteca.security;

import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Slows down guessing a password: after {@code intentosMaximos} failures in a row for the same email,
 * that email is locked for {@code bloqueo}, even if the next password is right.
 *
 * <p>It counts emails that do not exist too, so the lock reveals nothing about which ones do. The counts live in
 * this process's memory: a restart clears them, and several instances would each count their own. That is
 * enough for one instance; a deployment with several would move the counts to a shared store.
 * The price of locking by email is that someone can lock another person's account on purpose for a few minutes;
 * locking by address instead would be defeated by changing address.
 */
@Component
public class ControlDeIntentos {

	/** Above this many entries, the ones whose lock has passed are dropped, so a flood of made-up emails cannot fill the memory. */
	private static final int LIMITE_DE_ENTRADAS = 10_000;

	private record Registro(int fallos, Instant bloqueadoHasta) {
	}

	private final Map<String, Registro> registros = new ConcurrentHashMap<>();
	private final Clock clock;
	private final SeguridadProperties propiedades;

	public ControlDeIntentos(Clock clock, SeguridadProperties propiedades) {
		this.clock = clock;
		this.propiedades = propiedades;
	}

	/** Throws TOO_MANY_ATTEMPTS while the email is locked. */
	public void exigirNoBloqueado(String email) {
		Registro registro = registros.get(clave(email));
		if (registro != null && registro.bloqueadoHasta() != null && clock.instant().isBefore(registro.bloqueadoHasta())) {
			throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS);
		}
	}

	public void registrarFallo(String email) {
		limpiarSiHaceFalta();
		Instant ahora = clock.instant();
		registros.compute(clave(email), (clave, actual) -> {
			// A lock that already passed starts the count again from this failure.
			boolean empiezaDeNuevo = actual == null || (actual.bloqueadoHasta() != null && !ahora.isBefore(actual.bloqueadoHasta()));
			int fallos = (empiezaDeNuevo ? 0 : actual.fallos()) + 1;
			return new Registro(fallos, fallos >= propiedades.intentosMaximos() ? ahora.plus(propiedades.bloqueo()) : null);
		});
	}

	public void registrarExito(String email) {
		registros.remove(clave(email));
	}

	private void limpiarSiHaceFalta() {
		if (registros.size() > LIMITE_DE_ENTRADAS) {
			Instant ahora = clock.instant();
			registros.values().removeIf(registro -> registro.bloqueadoHasta() == null || !ahora.isBefore(registro.bloqueadoHasta()));
		}
	}

	private static String clave(String email) {
		return email.strip().toLowerCase(java.util.Locale.ROOT);
	}

}
