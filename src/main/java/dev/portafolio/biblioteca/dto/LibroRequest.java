package dev.portafolio.biblioteca.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The ISBN may be written with hyphens or spaces; it is stored as 10 or 13 digits. */
public record LibroRequest(
		@NotBlank @Size(max = 20) String isbn,
		@NotBlank @Size(max = 200) String titulo,
		@NotBlank @Size(max = 150) String autor,
		@Min(1000) @Max(2100) Integer anioPublicacion) {
}
