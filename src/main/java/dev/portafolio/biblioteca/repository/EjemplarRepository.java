package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Ejemplar;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface EjemplarRepository extends JpaRepository<Ejemplar, Long> {

	/** Copies that can be lent: the ones not retired. */
	long countByActivoTrue();

	Optional<Ejemplar> findByCodigo(String codigo);

	boolean existsByCodigo(String codigo);

	/** Used to refuse deleting a book that still has copies. */
	boolean existsByLibroId(Long libroId);

	Page<Ejemplar> findByLibroId(Long libroId, Pageable pageable);

	/**
	 * Locks the copy while it is retired or lent, so a copy cannot be retired in the same instant a
	 * loan of it is created.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
	@Query("select e from Ejemplar e where e.id = :id")
	Optional<Ejemplar> findByIdForUpdate(@Param("id") Long id);

}
