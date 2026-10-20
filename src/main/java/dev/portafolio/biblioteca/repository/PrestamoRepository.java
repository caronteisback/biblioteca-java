package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Prestamo;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

	/** Locks the loan row while it is renewed or returned, so a second request sees the new state. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
	@Query("select p from Prestamo p where p.id = :id")
	Optional<Prestamo> findByIdForUpdate(@Param("id") Long id);

	long countBySocioIdAndFechaDevolucionIsNull(Long socioId);

	List<Prestamo> findBySocioIdAndFechaDevolucionIsNull(Long socioId);

	boolean existsByEjemplarIdAndFechaDevolucionIsNull(Long ejemplarId);

	/** True when the member has an active loan whose due date is before {@code hoy}. */
	boolean existsBySocioIdAndFechaDevolucionIsNullAndFechaVencimientoBefore(Long socioId, LocalDate hoy);

	/** Whole history of a member: the input of the score calculation. */
	List<Prestamo> findBySocioId(Long socioId);

	boolean existsBySocioId(Long socioId);

	boolean existsByEjemplarId(Long ejemplarId);

}
