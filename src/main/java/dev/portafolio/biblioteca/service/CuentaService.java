package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.dto.UsuarioResponse;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Usuario;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an account may see about itself. The member comes from the account, never from a parameter of the
 * request: a member cannot ask for another member's data because there is nothing to ask with.
 */
@Service
@RequiredArgsConstructor
public class CuentaService {

	private final UsuarioRepository usuarios;
	private final SocioService socioService;
	private final PrestamoService prestamoService;
	private final MultaService multaService;

	/** The account, as it is now. An account that no longer exists or is switched off is treated as signed out. */
	@Transactional(readOnly = true)
	public UsuarioResponse yo(Long usuarioId) {
		Usuario usuario = usuarios.findById(usuarioId)
				.filter(Usuario::isActivo)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
		return respuesta(usuario);
	}

	@Transactional(readOnly = true)
	public PaginaDto<PrestamoResponse> misPrestamos(Long socioId, Boolean activo, int page, int size) {
		return prestamoService.listar(exigirSocio(socioId), activo, page, size);
	}

	@Transactional(readOnly = true)
	public PaginaDto<MultaResponse> misMultas(Long socioId, EstadoMulta estado, int page, int size) {
		return multaService.listar(exigirSocio(socioId), estado, page, size);
	}

	/** The response for an account: for a member it carries the member data, with score and alerts. */
	UsuarioResponse respuesta(Usuario usuario) {
		SocioResponse socio = usuario.getSocio() == null ? null : socioService.obtener(usuario.getSocio().getId());
		return UsuarioResponse.de(usuario, socio);
	}

	private static Long exigirSocio(Long socioId) {
		if (socioId == null) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return socioId;
	}

}
