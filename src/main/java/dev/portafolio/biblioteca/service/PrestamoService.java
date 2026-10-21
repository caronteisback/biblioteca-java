package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.config.BibliotecaProperties;
import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.EjemplarRepository;
import dev.portafolio.biblioteca.repository.MultaRepository;
import dev.portafolio.biblioteca.repository.PrestamoRepository;
import dev.portafolio.biblioteca.repository.SocioRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loans, renewals and returns. The rules are in docs/business-rules.md; the order of the checks is
 * part of the contract, so the same situation always gives the same error.
 *
 * <p><b>Lock order: member, then copy, then loan.</b> Every operation that takes more than one lock
 * takes them in this order, so two requests can never wait for each other. Each wait is capped at
 * 5 seconds and ends in {@code CONCURRENT_UPDATE}. Creating a loan locks member and copy; returning
 * locks member and loan, because a return can create a fine that a loan being created must see.
 */
@Service
@RequiredArgsConstructor
public class PrestamoService {

	private final PrestamoRepository prestamos;
	private final SocioRepository socios;
	private final EjemplarRepository ejemplares;
	private final MultaRepository multas;
	private final BibliotecaProperties properties;
	private final ScoreCalculator score;
	private final MultaCalculator calculadoraMultas;
	private final AlertaCalculator alertas;
	private final Clock clock;

	/** The eight checks of "create a loan", in documented order. Nothing is written if one fails. */
	@Transactional
	public PrestamoResponse crear(PrestamoRequest request) {
		LocalDate hoy = LocalDate.now(clock);
		Socio socio = socios.findByIdForUpdate(request.socioId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		Ejemplar ejemplar = ejemplares.findByIdForUpdate(request.ejemplarId())
				.orElseThrow(() -> new BusinessException(ErrorCode.COPY_NOT_FOUND));

		if (!socio.isActivo()) {
			throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
		}
		if (!ejemplar.isActivo()) {
			throw new BusinessException(ErrorCode.COPY_INACTIVE);
		}
		if (prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId())) {
			throw new BusinessException(ErrorCode.COPY_ALREADY_ON_LOAN);
		}
		if (prestamos.existsBySocioIdAndFechaDevolucionIsNullAndFechaVencimientoBefore(socio.getId(), hoy)) {
			throw new BusinessException(ErrorCode.MEMBER_HAS_OVERDUE_LOAN);
		}
		if (multas.existsByPrestamoSocioIdAndEstado(socio.getId(), EstadoMulta.PENDING)) {
			throw new BusinessException(ErrorCode.MEMBER_HAS_PENDING_FINE);
		}
		if (score.estaBloqueado(score.calcular(prestamos.findBySocioId(socio.getId()), hoy))) {
			throw new BusinessException(ErrorCode.MEMBER_BLOCKED_BY_SCORE);
		}
		int maximo = properties.prestamo().maxActivos();
		if (prestamos.countBySocioIdAndFechaDevolucionIsNull(socio.getId()) >= maximo) {
			throw new BusinessException(ErrorCode.LOAN_LIMIT_REACHED, "The member already has %d active loans.".formatted(maximo));
		}

		Prestamo prestamo = prestamos.saveAndFlush(Prestamo.builder()
				.ejemplar(ejemplar)
				.socio(socio)
				.fechaPrestamo(hoy)
				.fechaVencimiento(hoy.plusDays(properties.prestamo().diasPlazo()))
				.build());
		return respuesta(prestamo, hoy, null);
	}

	@Transactional(readOnly = true)
	public PrestamoResponse obtener(Long id) {
		Prestamo prestamo = prestamos.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_NOT_FOUND));
		Multa multa = multas.findByPrestamoId(id).orElse(null);
		return respuesta(prestamo, LocalDate.now(clock), multa);
	}

	/** Filters are optional: {@code socioId}, and {@code activo} (true: not returned, false: returned). */
	@Transactional(readOnly = true)
	public PaginaDto<PrestamoResponse> listar(Long socioId, Boolean activo, int page, int size) {
		Specification<Prestamo> filtro = (root, query, builder) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (socioId != null) {
				condiciones.add(builder.equal(root.get("socio").get("id"), socioId));
			}
			if (activo != null) {
				condiciones.add(activo ? builder.isNull(root.get("fechaDevolucion")) : builder.isNotNull(root.get("fechaDevolucion")));
			}
			return builder.and(condiciones.toArray(new Predicate[0]));
		};
		Page<Prestamo> pagina = prestamos.findAll(filtro, Paginas.por(page, size));
		Map<Long, Multa> multasDeLaPagina = multas
				.findByPrestamoIdIn(pagina.getContent().stream().map(Prestamo::getId).toList())
				.stream()
				.collect(Collectors.toMap(multa -> multa.getPrestamo().getId(), Function.identity()));
		LocalDate hoy = LocalDate.now(clock);
		return PaginaDto.de(pagina, prestamo -> respuesta(prestamo, hoy, multasDeLaPagina.get(prestamo.getId())));
	}

	/**
	 * Adds one term to the current due date (not to today) and counts the renewal. It never creates
	 * a new loan, so the unique indexes stay valid. A fine or a low score do not block a renewal.
	 */
	@Transactional
	public PrestamoResponse renovar(Long id) {
		LocalDate hoy = LocalDate.now(clock);
		Prestamo prestamo = prestamos.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_NOT_FOUND));
		if (!prestamo.isActivo()) {
			throw new BusinessException(ErrorCode.LOAN_ALREADY_RETURNED);
		}
		if (!prestamo.getSocio().isActivo()) {
			throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
		}
		if (prestamo.isVencido(hoy)) {
			throw new BusinessException(ErrorCode.RENEWAL_NOT_ALLOWED_OVERDUE);
		}
		if (prestamo.getRenovaciones() >= properties.prestamo().maxRenovaciones()) {
			throw new BusinessException(ErrorCode.RENEWAL_LIMIT_REACHED);
		}
		prestamo.renovar(properties.prestamo().diasPlazo());
		return respuesta(prestamos.save(prestamo), hoy, null);
	}

	/**
	 * Returns a loan. It is always accepted for an active loan, even if the member or the copy were
	 * deactivated meanwhile. A return after the due date creates the fine in the same transaction.
	 */
	@Transactional
	public PrestamoResponse devolver(Long id) {
		LocalDate hoy = LocalDate.now(clock);
		Long socioId = prestamos.findSocioIdById(id).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_NOT_FOUND));
		socios.findByIdForUpdate(socioId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		Prestamo prestamo = prestamos.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_NOT_FOUND));
		if (!prestamo.isActivo()) {
			throw new BusinessException(ErrorCode.LOAN_ALREADY_RETURNED);
		}
		prestamo.devolver(hoy);
		int diasRetraso = prestamo.diasRetraso(hoy);
		Multa multa = null;
		if (diasRetraso > 0) {
			multa = multas.save(Multa.builder()
					.prestamo(prestamo)
					.diasRetraso(diasRetraso)
					.monto(calculadoraMultas.calcular(diasRetraso))
					.fechaGeneracion(hoy)
					.build());
		}
		return respuesta(prestamos.save(prestamo), hoy, multa);
	}

	private PrestamoResponse respuesta(Prestamo prestamo, LocalDate hoy, Multa multa) {
		return PrestamoResponse.de(prestamo, hoy, alertas.delPrestamo(prestamo, hoy), multa == null ? null : MultaResponse.de(multa));
	}

}
