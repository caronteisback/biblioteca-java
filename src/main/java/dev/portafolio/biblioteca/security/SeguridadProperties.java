package dev.portafolio.biblioteca.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the sign-in. The secret has no default on purpose: without it the application stops at
 * startup instead of running with a key that anyone could guess.
 *
 * @param jwtSecreto key that signs the tokens (HMAC-SHA256); at least 32 bytes, from the environment
 * @param jwtDuracion how long a token is valid
 * @param intentosMaximos failed sign-ins in a row (per email) before it is locked
 * @param bloqueo how long the lock lasts
 * @param adminEmail email of the first librarian, created at startup only when there are no accounts
 * @param adminPassword password of that first librarian; at least 12 characters
 * @param demoPassword password of the demo accounts, read only when the demo profile loads them
 */
@Validated
@ConfigurationProperties(prefix = "biblioteca.seguridad")
public record SeguridadProperties(
		@NotBlank String jwtSecreto,
		@NotNull Duration jwtDuracion,
		@Positive int intentosMaximos,
		@NotNull Duration bloqueo,
		String adminEmail,
		String adminPassword,
		String demoPassword) {

	public static final int LONGITUD_MINIMA_SECRETO = 32;

	@AssertTrue(message = "biblioteca.seguridad.jwt-secreto (JWT_SECRET) must have at least 32 bytes")
	boolean isSecretoLargoSuficiente() {
		return jwtSecreto != null && jwtSecreto.getBytes(StandardCharsets.UTF_8).length >= LONGITUD_MINIMA_SECRETO;
	}

	@AssertTrue(message = "biblioteca.seguridad.jwt-duracion must be positive")
	boolean isDuracionPositiva() {
		return jwtDuracion != null && !jwtDuracion.isNegative() && !jwtDuracion.isZero();
	}

	@AssertTrue(message = "biblioteca.seguridad.bloqueo must be positive")
	boolean isBloqueoPositivo() {
		return bloqueo != null && !bloqueo.isNegative() && !bloqueo.isZero();
	}

}
