package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface MultaRepository extends JpaRepository<Multa, Long>, JpaSpecificationExecutor<Multa> {

	Optional<Multa> findByPrestamoId(Long prestamoId);

	/** Fines of several loans in one query, so a page of loans does not cost one query per loan. */
	List<Multa> findByPrestamoIdIn(Collection<Long> prestamoIds);

	/** True when any loan of the member has a fine in the given status. */
	boolean existsByPrestamoSocioIdAndEstado(Long socioId, EstadoMulta estado);

	/** Pending fines, their total and how many members owe them, in one query (the total is null when there are none). */
	@Query("""
			select new dev.portafolio.biblioteca.repository.ConteoDeMultas(count(m), sum(m.monto), count(distinct s.id))
			from Multa m join m.prestamo p join p.socio s where m.estado = :estado""")
	ConteoDeMultas contar(@Param("estado") EstadoMulta estado);

	/** Locks the fine while it is paid, so it cannot be paid twice. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
	@Query("select m from Multa m where m.id = :id")
	Optional<Multa> findByIdForUpdate(@Param("id") Long id);

}
