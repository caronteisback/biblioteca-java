package dev.portafolio.biblioteca.libro;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibroRepository extends JpaRepository<Libro, Long> {

	Optional<Libro> findByIsbn(String isbn);

	boolean existsByIsbn(String isbn);

}
