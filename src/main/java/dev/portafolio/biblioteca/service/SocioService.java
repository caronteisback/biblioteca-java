package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.SocioRequest;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.dto.SocioResumen;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.MultaRepository;
import dev.portafolio.biblioteca.repository.PrestamoRepository;
import dev.portafolio.biblioteca.repository.SocioRepository;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocioService {

	private final SocioRepository socios;
	private final UsuarioRepository usuarios;
	private final PrestamoRepository prestamos;
	private final MultaRepository multas;
	private final ScoreCalculator score;
	private final AlertaCalculator alertas;
	private final Clock clock;

	@Transactional
	public SocioResponse crear(SocioRequest request) {
		String email = request.email().strip();
		if (socios.existsByEmailIgnoreCase(email)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		Socio socio = socios.save(Socio.builder()
				.nombre(request.nombre().strip())
				.email(email)
				.fechaRegistro(LocalDate.now(clock))
				.build());
		return respuesta(socio);
	}

	/** One member with the score and the alerts, calculated now from the loan history. */
	@Transactional(readOnly = true)
	public SocioResponse obtener(Long id) {
		return respuesta(buscar(id));
	}

	@Transactional(readOnly = true)
	public PaginaDto<SocioResumen> listar(String texto, int page, int size) {
		Page<Socio> pagina = Paginas.sinFiltro(texto)
				? socios.findAll(Paginas.por(page, size))
				: socios.findByNombreContainingIgnoreCaseOrEmailContainingIgnoreCase(texto.strip(), texto.strip(), Paginas.por(page, size));
		return PaginaDto.de(pagina, SocioResumen::de);
	}

	@Transactional
	public SocioResponse actualizar(Long id, SocioRequest request) {
		Socio socio = buscar(id);
		String email = request.email().strip();
		if (socios.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		socio.setNombre(request.nombre().strip());
		socio.setEmail(email);
		return respuesta(socios.save(socio));
	}

	/**
	 * A member with active loans cannot be deactivated. The row is locked, as when a loan is created,
	 * so a loan cannot slip in between the check and the change.
	 */
	@Transactional
	public SocioResponse desactivar(Long id) {
		Socio socio = bloquear(id);
		if (prestamos.countBySocioIdAndFechaDevolucionIsNull(id) > 0) {
			throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_LOANS);
		}
		socio.desactivar();
		return respuesta(socios.save(socio));
	}

	@Transactional
	public SocioResponse reactivar(Long id) {
		Socio socio = bloquear(id);
		socio.reactivar();
		return respuesta(socios.save(socio));
	}

	/** A member that appears in any loan is never deleted, only deactivated. */
	@Transactional
	public void eliminar(Long id) {
		Socio socio = bloquear(id);
		if (prestamos.existsBySocioId(id)) {
			throw new BusinessException(ErrorCode.MEMBER_HAS_HISTORY);
		}
		usuarios.deleteBySocioId(id);
		socios.delete(socio);
	}

	private Socio buscar(Long id) {
		return socios.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
	}

	private Socio bloquear(Long id) {
		return socios.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
	}

	private SocioResponse respuesta(Socio socio) {
		LocalDate hoy = LocalDate.now(clock);
		List<Prestamo> historial = prestamos.findBySocioId(socio.getId());
		int puntos = score.calcular(historial, hoy);
		boolean multaPendiente = multas.existsByPrestamoSocioIdAndEstado(socio.getId(), EstadoMulta.PENDING);
		return SocioResponse.de(socio, puntos, alertas.delSocio(puntos, multaPendiente));
	}

}
