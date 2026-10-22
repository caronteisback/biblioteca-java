package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Prestamo;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import dev.portafolio.biblioteca.dto.LibroMasPrestado;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long>, JpaSpecificationExecutor<Prestamo> {

	/** Locks the loan row while it is renewed or returned, so a second request sees the new state. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
	@Query("select p from Prestamo p where p.id = :id")
	Optional<Prestamo> findByIdForUpdate(@Param("id") Long id);

	/**
	 * Only the member id, without loading the loan. A return must lock the member before the loan;
	 * loading the loan first would leave a copy in the persistence context that the later lock does
	 * not refresh, and the return could act on stale data.
	 */
	@Query("select p.socio.id from Prestamo p where p.id = :id")
	Optional<Long> findSocioIdById(@Param("id") Long id);

	long countBySocioIdAndFechaDevolucionIsNull(Long socioId);

	List<Prestamo> findBySocioIdAndFechaDevolucionIsNull(Long socioId);

	boolean existsByEjemplarIdAndFechaDevolucionIsNull(Long ejemplarId);

	/** True when the member has an active loan whose due date is before {@code hoy}. */
	boolean existsBySocioIdAndFechaDevolucionIsNullAndFechaVencimientoBefore(Long socioId, LocalDate hoy);

	/** Whole history of a member: the input of the score calculation. */
	List<Prestamo> findBySocioId(Long socioId);

	boolean existsBySocioId(Long socioId);

	boolean existsByEjemplarId(Long ejemplarId);

	// ---- the dashboard: counted by the database, never by loading loans

	/** Active loans, overdue ones (due before {@code hoy}) and the ones due from {@code hoy} to {@code limite}, in one query. */
	@Query("""
			select new dev.portafolio.biblioteca.repository.ConteoDePrestamos(
				count(p),
				count(case when p.fechaVencimiento < :hoy then 1 end),
				count(case when p.fechaVencimiento >= :hoy and p.fechaVencimiento <= :limite then 1 end))
			from Prestamo p where p.fechaDevolucion is null""")
	ConteoDePrestamos contarActivos(@Param("hoy") LocalDate hoy, @Param("limite") LocalDate limite);

	/** Loans started per day since {@code desde}: one row per day with loans. */
	@Query("""
			select new dev.portafolio.biblioteca.repository.PrestamosDelDia(p.fechaPrestamo, count(p))
			from Prestamo p where p.fechaPrestamo >= :desde group by p.fechaPrestamo""")
	List<PrestamosDelDia> contarPorDia(@Param("desde") LocalDate desde);

	/** The books with the most loans started since {@code desde}; the page limits how many. */
	@Query("""
			select new dev.portafolio.biblioteca.dto.LibroMasPrestado(l.id, l.titulo, count(p))
			from Prestamo p join p.ejemplar e join e.libro l
			where p.fechaPrestamo >= :desde
			group by l.id, l.titulo
			order by count(p) desc, l.titulo asc, l.id asc""")
	List<LibroMasPrestado> masPrestados(@Param("desde") LocalDate desde, Pageable limite);

	/** Active loans due on or before {@code limite}, the oldest due date first, with member, copy and book; the page limits how many. */
	@Query("""
			select p from Prestamo p
			join fetch p.socio
			join fetch p.ejemplar e
			join fetch e.libro
			where p.fechaDevolucion is null and p.fechaVencimiento <= :limite
			order by p.fechaVencimiento asc, p.id asc""")
	List<Prestamo> requierenAtencion(@Param("limite") LocalDate limite, Pageable pagina);

}
