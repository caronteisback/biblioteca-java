package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Libro;
import io.swagger.v3.oas.annotations.media.Schema;

public record LibroResponse(
		Long id,
		@Schema(description = "Stored without hyphens or spaces: 10 or 13 characters.", example = "9780306406157") String isbn,
		String titulo,
		String autor,
		@Schema(nullable = true, description = "Year of publication, when known.") Integer anioPublicacion) {

	public static LibroResponse de(Libro libro) {
		return new LibroResponse(libro.getId(), libro.getIsbn(), libro.getTitulo(), libro.getAutor(), libro.getAnioPublicacion());
	}

}
