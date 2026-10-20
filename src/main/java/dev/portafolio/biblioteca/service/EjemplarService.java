package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.dto.EjemplarRequest;
import dev.portafolio.biblioteca.dto.EjemplarResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.EjemplarRepository;
import dev.portafolio.biblioteca.repository.LibroRepository;
import dev.portafolio.biblioteca.repository.PrestamoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EjemplarService {

	private final EjemplarRepository ejemplares;
	private final LibroRepository libros;
	private final PrestamoRepository prestamos;

	@Transactional
	public EjemplarResponse crear(Long libroId, EjemplarRequest request) {
		Libro libro = libros.findById(libroId).orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
		String codigo = request.codigo().strip();
		if (ejemplares.existsByCodigo(codigo)) {
			throw new BusinessException(ErrorCode.DUPLICATE_COPY_CODE);
		}
		Ejemplar ejemplar = ejemplares.save(Ejemplar.builder().libro(libro).codigo(codigo).build());
		return respuesta(ejemplar);
	}

	@Transactional(readOnly = true)
	public EjemplarResponse obtener(Long id) {
		return respuesta(buscar(id));
	}

	@Transactional(readOnly = true)
	public PaginaDto<EjemplarResponse> listarPorLibro(Long libroId, int page, int size) {
		if (!libros.existsById(libroId)) {
			throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
		}
		return PaginaDto.de(ejemplares.findByLibroId(libroId, Paginas.por(page, size)), this::respuesta);
	}

	/** Retires a damaged or lost copy. A copy that is on loan cannot be retired. */
	@Transactional
	public EjemplarResponse darDeBaja(Long id) {
		Ejemplar ejemplar = bloquear(id);
		if (prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(id)) {
			throw new BusinessException(ErrorCode.COPY_HAS_ACTIVE_LOAN);
		}
		ejemplar.darDeBaja();
		return respuesta(ejemplares.save(ejemplar));
	}

	@Transactional
	public EjemplarResponse reactivar(Long id) {
		Ejemplar ejemplar = bloquear(id);
		ejemplar.reactivar();
		return respuesta(ejemplares.save(ejemplar));
	}

	/** A copy that appears in any loan is never deleted, only retired. */
	@Transactional
	public void eliminar(Long id) {
		Ejemplar ejemplar = bloquear(id);
		if (prestamos.existsByEjemplarId(id)) {
			throw new BusinessException(ErrorCode.COPY_HAS_HISTORY);
		}
		ejemplares.delete(ejemplar);
	}

	private Ejemplar buscar(Long id) {
		return ejemplares.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.COPY_NOT_FOUND));
	}

	private Ejemplar bloquear(Long id) {
		return ejemplares.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.COPY_NOT_FOUND));
	}

	private EjemplarResponse respuesta(Ejemplar ejemplar) {
		return EjemplarResponse.de(ejemplar, prestamos.existsByEjemplarIdAndFechaDevolucionIsNull(ejemplar.getId()));
	}

}
