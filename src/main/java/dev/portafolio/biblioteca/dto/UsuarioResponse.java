package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Rol;
import dev.portafolio.biblioteca.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

/** The signed-in account. There is no field for the password or its hash, so none can leak. */
public record UsuarioResponse(
		Long id,
		String email,
		Rol rol,
		@Schema(nullable = true, description = "The member data, with score and alerts, when the role is MEMBER; null for a librarian.") SocioResponse socio,
		ReglasDePrestamo reglas) {

	public static UsuarioResponse de(Usuario usuario, SocioResponse socio, ReglasDePrestamo reglas) {
		return new UsuarioResponse(usuario.getId(), usuario.getEmail(), usuario.getRol(), socio, reglas);
	}

}
