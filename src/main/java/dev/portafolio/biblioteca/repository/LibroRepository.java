package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Libro;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibroRepository extends JpaRepository<Libro, Long> {

	Optional<Libro> findByIsbn(String isbn);

	boolean existsByIsbn(String isbn);

	boolean existsByIsbnAndIdNot(String isbn, Long id);

	/** Title or author contains the text, ignoring case. Wildcard characters in the text are escaped. */
	Page<Libro> findByTituloContainingIgnoreCaseOrAutorContainingIgnoreCase(String titulo, String autor, Pageable pageable);

}
