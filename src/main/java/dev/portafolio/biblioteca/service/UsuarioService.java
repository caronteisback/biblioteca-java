package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.entity.Rol;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.entity.Usuario;
import dev.portafolio.biblioteca.repository.SocioRepository;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts. It is not reachable from the API: accounts are created by the operator at startup
 * (the first librarian) or by the demo data. A refused account is an error of whoever configured it, so it
 * stops with a plain message instead of an API error code.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

	public static final int LONGITUD_MINIMA_PASSWORD = 12;
	private static final int LONGITUD_MAXIMA_PASSWORD = 128;

	private final UsuarioRepository usuarios;
	private final SocioRepository socios;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public Usuario crearBibliotecario(String email, String password) {
		return crear(email, password, Rol.LIBRARIAN, null);
	}

	@Transactional
	public Usuario crearSocio(String email, String password, Long socioId) {
		Socio socio = socios.findById(socioId).orElseThrow(() -> new IllegalArgumentException("The member " + socioId + " does not exist."));
		return crear(email, password, Rol.MEMBER, socio);
	}

	private Usuario crear(String email, String password, Rol rol, Socio socio) {
		String limpio = email == null ? "" : email.strip();
		if (limpio.isEmpty() || limpio.length() > 150 || !limpio.contains("@")) {
			throw new IllegalArgumentException("The email of the account is not valid.");
		}
		if (password == null || password.length() < LONGITUD_MINIMA_PASSWORD || password.length() > LONGITUD_MAXIMA_PASSWORD) {
			throw new IllegalArgumentException("The password of the account must have between " + LONGITUD_MINIMA_PASSWORD
					+ " and " + LONGITUD_MAXIMA_PASSWORD + " characters.");
		}
		if (usuarios.existsByEmailIgnoreCase(limpio)) {
			throw new IllegalArgumentException("An account with the email " + limpio + " already exists.");
		}
		return usuarios.save(Usuario.builder()
				.email(limpio)
				.passwordHash(passwordEncoder.encode(password))
				.rol(rol)
				.socio(socio)
				.build());
	}

}
