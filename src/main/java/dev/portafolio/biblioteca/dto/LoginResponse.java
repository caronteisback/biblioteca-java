package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record LoginResponse(
		@Schema(description = "Send it as `Authorization: Bearer <token>`.") String token,
		@Schema(allowableValues = "Bearer") String tipo,
		@Schema(description = "The moment the token stops being valid.") Instant expiraEn,
		UsuarioResponse usuario) {

	/** A token must not reach a log line through a stray {@code toString()}. */
	@Override
	public String toString() {
		return "LoginResponse[usuario=" + usuario + ", expiraEn=" + expiraEn + ", token=***]";
	}

}
