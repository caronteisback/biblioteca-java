package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.dto.LoginRequest;
import dev.portafolio.biblioteca.dto.LoginResponse;
import dev.portafolio.biblioteca.dto.UsuarioResponse;
import dev.portafolio.biblioteca.entity.Usuario;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import dev.portafolio.biblioteca.security.ControlDeIntentos;
import dev.portafolio.biblioteca.security.EmisorDeTokens;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing in. Three things make guessing harder:
 * <ul>
 * <li>a wrong email and a wrong password give the same answer;
 * <li>the password is always compared against a hash, a made-up one when the email does not exist, so the answer
 * takes about the same time either way;
 * <li>after a few failures in a row the email is locked ({@link ControlDeIntentos}).
 * </ul>
 */
@Service
public class AutenticacionService {

	private final UsuarioRepository usuarios;
	private final PasswordEncoder passwordEncoder;
	private final EmisorDeTokens emisor;
	private final ControlDeIntentos intentos;
	private final CuentaService cuenta;
	/** Compared when the email does not exist, to spend the same time as for a real account. */
	private final String hashFalso;

	public AutenticacionService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, EmisorDeTokens emisor,
			ControlDeIntentos intentos, CuentaService cuenta) {
		this.usuarios = usuarios;
		this.passwordEncoder = passwordEncoder;
		this.emisor = emisor;
		this.intentos = intentos;
		this.cuenta = cuenta;
		this.hashFalso = passwordEncoder.encode("not-a-real-password");
	}

	@Transactional(readOnly = true)
	public LoginResponse entrar(LoginRequest pedido) {
		String email = pedido.email().strip();
		intentos.exigirNoBloqueado(email);

		Optional<Usuario> encontrado = usuarios.findByEmailIgnoreCase(email);
		boolean coincide = passwordEncoder.matches(pedido.password(), encontrado.map(Usuario::getPasswordHash).orElse(hashFalso));
		if (encontrado.isEmpty() || !coincide || !encontrado.get().isActivo()) {
			intentos.registrarFallo(email);
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		intentos.registrarExito(email);
		Usuario usuario = encontrado.get();
		EmisorDeTokens.Token token = emisor.emitir(usuario);
		UsuarioResponse respuesta = cuenta.respuesta(usuario);
		return new LoginResponse(token.valor(), "Bearer", token.expiraEn(), respuesta);
	}

}
