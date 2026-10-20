package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Ejemplar;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EjemplarRepository extends JpaRepository<Ejemplar, Long> {

	Optional<Ejemplar> findByCodigo(String codigo);

	boolean existsByCodigo(String codigo);

	/** Used to refuse deleting a book that still has copies. */
	boolean existsByLibroId(Long libroId);

}
