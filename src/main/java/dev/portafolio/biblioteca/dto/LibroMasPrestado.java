package dev.portafolio.biblioteca.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A book and the loans of any of its copies in the period of the ranking. */
public record LibroMasPrestado(
		Long libroId,
		String titulo,
		@Schema(description = "Loans of any copy of the book started in the last 90 days.", minimum = "1") Long prestamos) {
}
