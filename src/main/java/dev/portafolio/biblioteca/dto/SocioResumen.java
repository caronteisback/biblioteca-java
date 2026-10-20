package dev.portafolio.biblioteca.dto;

import dev.portafolio.biblioteca.entity.Socio;
import java.time.LocalDate;

/**
 * Row of the member list. It carries no score: computing it needs the whole loan history of each
 * member, which would turn a page of 20 into 20 extra queries. Ask for one member to get it.
 */
public record SocioResumen(Long id, String nombre, String email, boolean activo, LocalDate fechaRegistro) {

	public static SocioResumen de(Socio socio) {
		return new SocioResumen(socio.getId(), socio.getNombre(), socio.getEmail(), socio.isActivo(), socio.getFechaRegistro());
	}

}
