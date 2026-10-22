package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Socio;
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

public interface SocioRepository extends JpaRepository<Socio, Long> {

	/** Members that can borrow: the ones not deactivated. */
	long countByActivoTrue();

	Optional<Socio> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	/** Name or email contains the text, ignoring case. Wildcard characters in the text are escaped. */
	Page<Socio> findByNombreContainingIgnoreCaseOrEmailContainingIgnoreCase(String nombre, String email, Pageable pageable);

	/**
	 * Reads the member with {@code SELECT ... FOR UPDATE}. Creating a loan calls this first, so a
	 * member's loans are processed one at a time and the limit of active loans cannot be exceeded.
	 * The wait is capped at 5 seconds instead of blocking forever.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
	@Query("select s from Socio s where s.id = :id")
	Optional<Socio> findByIdForUpdate(@Param("id") Long id);

}
