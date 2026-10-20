package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.LibroRequest;
import dev.portafolio.biblioteca.dto.LibroResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LibroServiceTests extends BaseIntegracionTest {

	@Autowired
	private LibroService servicio;

	private LibroRequest pedido(String isbn, String titulo, String autor) {
		return new LibroRequest(isbn, titulo, autor, 1999);
	}

	@Test
	void createsABookStoringTheIsbnWithoutSeparatorsAndTheTextStripped() {
		LibroResponse creado = servicio.crear(pedido("978-0-306-40615-7", "  Libro Uno  ", " Autora Ficticia "));

		assertThat(creado.id()).isNotNull();
		assertThat(creado.isbn()).isEqualTo("9780306406157");
		assertThat(creado.titulo()).isEqualTo("Libro Uno");
		assertThat(creado.autor()).isEqualTo("Autora Ficticia");
		assertThat(creado.anioPublicacion()).isEqualTo(1999);
	}

	@Test
	void anIsbnTenAcceptsALowercaseXAndStoresItUppercase() {
		assertThat(servicio.crear(pedido("0-8044-2957-x", "Diez", "Autor")).isbn()).isEqualTo("080442957X");
	}

	@Test
	void anIsbnThatIsNotTenOrThirteenDigitsIsRejected() {
		assertError(() -> servicio.crear(pedido("123", "Corto", "Autor")), ErrorCode.VALIDATION_ERROR);
		assertError(() -> servicio.crear(pedido("97803064061570", "Largo", "Autor")), ErrorCode.VALIDATION_ERROR);
		assertError(() -> servicio.crear(pedido("97803064X6157", "Con X en medio", "Autor")), ErrorCode.VALIDATION_ERROR);
	}

	@Test
	void twoSpellingsOfTheSameIsbnAreDuplicates() {
		servicio.crear(pedido("9780306406157", "Primero", "Autor"));

		assertError(() -> servicio.crear(pedido("978-0-306-40615-7", "Segundo", "Autor")), ErrorCode.DUPLICATE_ISBN);
	}

	@Test
	void anUnknownBookIsNotFound() {
		assertError(() -> servicio.obtener(-1L), ErrorCode.BOOK_NOT_FOUND);
		assertError(() -> servicio.actualizar(-1L, pedido("9780306406157", "X", "Y")), ErrorCode.BOOK_NOT_FOUND);
		assertError(() -> servicio.eliminar(-1L), ErrorCode.BOOK_NOT_FOUND);
	}

	@Test
	void updatesTheFieldsAndKeepsItsOwnIsbn() {
		LibroResponse creado = servicio.crear(pedido("9780306406157", "Viejo", "Autor"));

		LibroResponse cambiado = servicio.actualizar(creado.id(), new LibroRequest("978-0-306-40615-7", "Nuevo", "Otra Autora", 2005));

		assertThat(cambiado.id()).isEqualTo(creado.id());
		assertThat(cambiado.isbn()).isEqualTo("9780306406157");
		assertThat(cambiado.titulo()).isEqualTo("Nuevo");
		assertThat(cambiado.autor()).isEqualTo("Otra Autora");
		assertThat(cambiado.anioPublicacion()).isEqualTo(2005);
		assertThat(servicio.obtener(creado.id()).titulo()).isEqualTo("Nuevo");
	}

	@Test
	void anUpdateCannotTakeTheIsbnOfAnotherBook() {
		servicio.crear(pedido("9780306406157", "Uno", "Autor"));
		LibroResponse otro = servicio.crear(pedido("0804429577", "Dos", "Autor"));

		assertError(() -> servicio.actualizar(otro.id(), pedido("978-0-306-40615-7", "Dos", "Autor")), ErrorCode.DUPLICATE_ISBN);
	}

	@Test
	void deletesABookWithoutCopies() {
		LibroResponse creado = servicio.crear(pedido("9780306406157", "Efimero", "Autor"));

		servicio.eliminar(creado.id());

		assertError(() -> servicio.obtener(creado.id()), ErrorCode.BOOK_NOT_FOUND);
	}

	@Test
	void aBookWithCopiesCannotBeDeleted() {
		LibroResponse creado = servicio.crear(pedido("9780306406157", "Con copias", "Autor"));
		ejemplar(libros.findById(creado.id()).orElseThrow());

		assertError(() -> servicio.eliminar(creado.id()), ErrorCode.BOOK_HAS_COPIES);
		assertThat(servicio.obtener(creado.id()).titulo()).isEqualTo("Con copias");
	}

	@Test
	void filtersByTitleOrAuthorIgnoringCase() {
		servicio.crear(pedido("9780306406157", "El Faro del Fin", "Lucia Ficticia"));
		servicio.crear(pedido("0804429577", "Mapas del Mar", "Mateo Faro"));
		servicio.crear(pedido("9781234567897", "Cuentos de Invierno", "Ana Ficticia"));

		PaginaDto<LibroResponse> porTitulo = servicio.listar("faro", 0, 20);

		assertThat(porTitulo.items()).extracting(LibroResponse::titulo).containsExactlyInAnyOrder("El Faro del Fin", "Mapas del Mar");
		assertThat(servicio.listar("  INVIERNO ", 0, 20).items()).extracting(LibroResponse::titulo).containsExactly("Cuentos de Invierno");
		assertThat(servicio.listar("ficticia", 0, 20).totalElements()).isEqualTo(2);
	}

	@Test
	void aPercentSignInTheFilterIsLiteralNotAWildcard() {
		servicio.crear(pedido("9780306406157", "Cien por cien", "Autor"));

		assertThat(servicio.listar("%", 0, 20).items()).isEmpty();
		assertThat(servicio.listar("_", 0, 20).items()).isEmpty();
	}

	@Test
	void pagesInStableIdOrderAndReportsTheTotals() {
		for (int index = 0; index < 5; index++) {
			servicio.crear(pedido("97800000000" + (10 + index), "Paginado " + index, "Autor Paginas"));
		}

		PaginaDto<LibroResponse> primera = servicio.listar("Autor Paginas", 0, 2);
		PaginaDto<LibroResponse> ultima = servicio.listar("Autor Paginas", 2, 2);

		assertThat(primera.items()).extracting(LibroResponse::titulo).containsExactly("Paginado 0", "Paginado 1");
		assertThat(primera.page()).isZero();
		assertThat(primera.size()).isEqualTo(2);
		assertThat(primera.totalElements()).isEqualTo(5);
		assertThat(primera.totalPages()).isEqualTo(3);
		assertThat(ultima.items()).extracting(LibroResponse::titulo).containsExactly("Paginado 4");
	}

	@Test
	void aBlankFilterListsEverything() {
		servicio.crear(pedido("9780306406157", "Uno", "Autor"));

		assertThat(servicio.listar(null, 0, 20).totalElements()).isGreaterThanOrEqualTo(1);
		assertThat(servicio.listar("   ", 0, 20).totalElements()).isEqualTo(servicio.listar(null, 0, 20).totalElements());
	}

}
