package dev.portafolio.biblioteca.multa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MultaRepository extends JpaRepository<Multa, Long> {

	Optional<Multa> findByPrestamoId(Long prestamoId);

	/** True when any loan of the member has a fine in the given status. */
	boolean existsByPrestamoSocioIdAndEstado(Long socioId, EstadoMulta estado);

}
