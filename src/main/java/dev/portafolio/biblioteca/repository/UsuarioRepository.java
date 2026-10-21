package dev.portafolio.biblioteca.repository;

import dev.portafolio.biblioteca.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** Emails are unique ignoring case (function-based unique index), so the lookup ignores case too. */
	Optional<Usuario> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	/** A member that is deleted takes its account with it. */
	@Modifying
	@Query("delete from Usuario u where u.socio.id = :socioId")
	void deleteBySocioId(@Param("socioId") Long socioId);

}
