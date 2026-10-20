package dev.portafolio.biblioteca.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocioRequest(
		@NotBlank @Size(max = 120) String nombre,
		@NotBlank @Email @Size(max = 150) String email) {
}
