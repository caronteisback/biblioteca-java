package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.MultaRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MultaService {

	private final MultaRepository multas;
	private final Clock clock;

	@Transactional(readOnly = true)
	public MultaResponse obtener(Long id) {
		return MultaResponse.de(multas.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.FINE_NOT_FOUND)));
	}

	/** Filters are optional: {@code socioId} (fines of that member's loans) and {@code estado}. */
	@Transactional(readOnly = true)
	public PaginaDto<MultaResponse> listar(Long socioId, EstadoMulta estado, int page, int size) {
		Specification<Multa> filtro = (root, query, builder) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (socioId != null) {
				condiciones.add(builder.equal(root.get("prestamo").get("socio").get("id"), socioId));
			}
			if (estado != null) {
				condiciones.add(builder.equal(root.get("estado"), estado));
			}
			return builder.and(condiciones.toArray(new Predicate[0]));
		};
		return PaginaDto.de(multas.findAll(filtro, Paginas.por(page, size)), MultaResponse::de);
	}

	/**
	 * Pays the whole fine: there is no gateway and no partial payment. Paying does not change the
	 * score. The row is locked, so two simultaneous payments cannot both succeed.
	 */
	@Transactional
	public MultaResponse pagar(Long id) {
		Multa multa = multas.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.FINE_NOT_FOUND));
		if (!multa.isPendiente()) {
			throw new BusinessException(ErrorCode.FINE_ALREADY_PAID);
		}
		multa.pagar(LocalDate.now(clock));
		return MultaResponse.de(multas.save(multa));
	}

}
