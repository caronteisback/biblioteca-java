package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.EjemplarRequest;
import dev.portafolio.biblioteca.dto.EjemplarResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EjemplarServiceTests extends BaseIntegracionTest {

	@Autowired
	private EjemplarService servicio;

	private Prestamo prestar(Ejemplar ejemplar) {
		return prestamos.saveAndFlush(prestamo(ejemplar, socio(), HOY, 14));
	}

	@Test
	void createsACopyOfABookActiveAndAvailable() {
		Libro libro = libro();

		EjemplarResponse creado = servicio.crear(libro.getId(), new EjemplarRequest("  EJ-PRUEBA-1  "));

		assertThat(creado.id()).isNotNull();
		assertThat(creado.libroId()).isEqualTo(libro.getId());
		assertThat(creado.codigo()).isEqualTo("EJ-PRUEBA-1");
		assertThat(creado.activo()).isTrue();
		assertThat(creado.disponible()).isTrue();
	}

	@Test
	void aCopyOfAnUnknownBookIsRefused() {
		assertError(() -> servicio.crear(-1L, new EjemplarRequest("EJ-X")), ErrorCode.BOOK_NOT_FOUND);
	}

	@Test
	void aDuplicateCodeIsRefusedEvenWithSpaces() {
		Libro libro = libro();
		servicio.crear(libro.getId(), new EjemplarRequest("EJ-DUP"));

		assertError(() -> servicio.crear(libro.getId(), new EjemplarRequest(" EJ-DUP ")), ErrorCode.DUPLICATE_COPY_CODE);
	}

	@Test
	void availabilityIsCalculatedFromTheActiveLoan() {
		Ejemplar ejemplar = ejemplar();
		assertThat(servicio.obtener(ejemplar.getId()).disponible()).isTrue();

		Prestamo prestamo = prestar(ejemplar);
		assertThat(servicio.obtener(ejemplar.getId()).disponible()).isFalse();
		assertThat(servicio.obtener(ejemplar.getId()).activo()).isTrue();

		prestamo.devolver(HOY);
		prestamos.saveAndFlush(prestamo);
		assertThat(servicio.obtener(ejemplar.getId()).disponible()).isTrue();
	}

	@Test
	void anUnknownCopyIsNotFoundInEveryOperation() {
		assertError(() -> servicio.obtener(-1L), ErrorCode.COPY_NOT_FOUND);
		assertError(() -> servicio.darDeBaja(-1L), ErrorCode.COPY_NOT_FOUND);
		assertError(() -> servicio.reactivar(-1L), ErrorCode.COPY_NOT_FOUND);
		assertError(() -> servicio.eliminar(-1L), ErrorCode.COPY_NOT_FOUND);
	}

	@Test
	void aCopyOnLoanCannotBeRetired() {
		Ejemplar ejemplar = ejemplar();
		prestar(ejemplar);

		assertError(() -> servicio.darDeBaja(ejemplar.getId()), ErrorCode.COPY_HAS_ACTIVE_LOAN);
		assertThat(servicio.obtener(ejemplar.getId()).activo()).isTrue();
	}

	@Test
	void aFreeCopyCanBeRetiredAndBroughtBack() {
		Ejemplar ejemplar = ejemplar();

		EjemplarResponse baja = servicio.darDeBaja(ejemplar.getId());
		assertThat(baja.activo()).isFalse();
		assertThat(baja.disponible()).isFalse();

		EjemplarResponse alta = servicio.reactivar(ejemplar.getId());
		assertThat(alta.activo()).isTrue();
		assertThat(alta.disponible()).isTrue();
	}

	@Test
	void retiringTwiceIsHarmless() {
		Ejemplar ejemplar = ejemplar();
		servicio.darDeBaja(ejemplar.getId());

		assertThat(servicio.darDeBaja(ejemplar.getId()).activo()).isFalse();
	}

	@Test
	void aCopyWithoutHistoryCanBeDeleted() {
		Ejemplar ejemplar = ejemplar();

		servicio.eliminar(ejemplar.getId());

		assertError(() -> servicio.obtener(ejemplar.getId()), ErrorCode.COPY_NOT_FOUND);
	}

	@Test
	void aCopyThatAppearsInALoanIsNeverDeleted() {
		Ejemplar ejemplar = ejemplar();
		Prestamo prestamo = prestar(ejemplar);
		prestamo.devolver(HOY);
		prestamos.saveAndFlush(prestamo);

		assertError(() -> servicio.eliminar(ejemplar.getId()), ErrorCode.COPY_HAS_HISTORY);
		assertThat(servicio.obtener(ejemplar.getId()).codigo()).isEqualTo(ejemplar.getCodigo());
	}

	@Test
	void listsTheCopiesOfABookPaginated() {
		Libro libro = libro();
		for (int index = 0; index < 3; index++) {
			ejemplar(libro);
		}
		ejemplar(libro());

		PaginaDto<EjemplarResponse> pagina = servicio.listarPorLibro(libro.getId(), 0, 2);

		assertThat(pagina.items()).hasSize(2).allMatch(item -> item.libroId().equals(libro.getId()));
		assertThat(pagina.totalElements()).isEqualTo(3);
		assertThat(pagina.totalPages()).isEqualTo(2);
		assertThat(servicio.listarPorLibro(libro.getId(), 1, 2).items()).hasSize(1);
	}

	@Test
	void listingTheCopiesOfAnUnknownBookIsRefused() {
		assertError(() -> servicio.listarPorLibro(-1L, 0, 20), ErrorCode.BOOK_NOT_FOUND);
	}

}
