package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.dto.LibroRequest;
import dev.portafolio.biblioteca.dto.LibroResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.EjemplarRepository;
import dev.portafolio.biblioteca.repository.LibroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibroService {

	private final LibroRepository libros;
	private final EjemplarRepository ejemplares;

	@Transactional
	public LibroResponse crear(LibroRequest request) {
		String isbn = Isbn.normalizar(request.isbn());
		if (libros.existsByIsbn(isbn)) {
			throw new BusinessException(ErrorCode.DUPLICATE_ISBN);
		}
		return LibroResponse.de(libros.save(Libro.builder()
				.isbn(isbn)
				.titulo(request.titulo().strip())
				.autor(request.autor().strip())
				.anioPublicacion(request.anioPublicacion())
				.build()));
	}

	@Transactional(readOnly = true)
	public LibroResponse obtener(Long id) {
		return LibroResponse.de(buscar(id));
	}

	@Transactional(readOnly = true)
	public PaginaDto<LibroResponse> listar(String texto, int page, int size) {
		Page<Libro> pagina = Paginas.sinFiltro(texto)
				? libros.findAll(Paginas.por(page, size))
				: libros.findByTituloContainingIgnoreCaseOrAutorContainingIgnoreCase(texto.strip(), texto.strip(), Paginas.por(page, size));
		return PaginaDto.de(pagina, LibroResponse::de);
	}

	@Transactional
	public LibroResponse actualizar(Long id, LibroRequest request) {
		Libro libro = buscar(id);
		String isbn = Isbn.normalizar(request.isbn());
		if (libros.existsByIsbnAndIdNot(isbn, id)) {
			throw new BusinessException(ErrorCode.DUPLICATE_ISBN);
		}
		libro.setIsbn(isbn);
		libro.setTitulo(request.titulo().strip());
		libro.setAutor(request.autor().strip());
		libro.setAnioPublicacion(request.anioPublicacion());
		return LibroResponse.de(libros.save(libro));
	}

	/** A book is deleted only while it has no copies. */
	@Transactional
	public void eliminar(Long id) {
		Libro libro = buscar(id);
		if (ejemplares.existsByLibroId(id)) {
			throw new BusinessException(ErrorCode.BOOK_HAS_COPIES);
		}
		libros.delete(libro);
	}

	private Libro buscar(Long id) {
		return libros.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
	}

}
