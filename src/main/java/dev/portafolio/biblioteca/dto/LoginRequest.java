package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials. The password is never echoed back, never logged and never part of any response. */
public record LoginRequest(
		@NotBlank @Email @Size(max = 150) String email,
		@Schema(writeOnly = true) @NotBlank @Size(max = 128) String password) {

	/** The password must not reach a log line through a stray {@code toString()}. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
