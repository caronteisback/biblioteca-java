package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Libro;

public record LibroResponse(Long id, String isbn, String titulo, String autor, Integer anioPublicacion) {

	public static LibroResponse de(Libro libro) {
		return new LibroResponse(libro.getId(), libro.getIsbn(), libro.getTitulo(), libro.getAutor(), libro.getAnioPublicacion());
	}

}
