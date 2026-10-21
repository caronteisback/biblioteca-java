package dev.portafolio.biblioteca.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.service.EjemplarService;
import dev.portafolio.biblioteca.service.MultaService;
import dev.portafolio.biblioteca.service.PrestamoService;
import dev.portafolio.biblioteca.service.SocioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The demo library, loaded on the real database and read back through the services, as a screen would see it.
 * It runs outside a transaction because the loader moves loan dates with SQL between service calls, and the
 * data is deleted after each test.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatosDeEjemploTests extends BaseIntegracionTest {

	@Autowired
	private DatosDeEjemplo datos;

	@Autowired
	private SocioService socioService;

	@Autowired
	private PrestamoService prestamoService;

	@Autowired
	private MultaService multaService;

	@Autowired
	private EjemplarService ejemplarService;

	@AfterEach
	void limpiar() {
		jdbc.update("delete from multa");
		jdbc.update("delete from prestamo");
		jdbc.update("delete from ejemplar");
		jdbc.update("delete from socio");
		jdbc.update("delete from libro");
	}

	@Test
	void loadsTheLibraryAndDoesNothingTheSecondTime() {
		assertThat(datos.cargar()).isTrue();
		assertThat(libros.count()).isEqualTo(20);
		assertThat(socios.count()).isEqualTo(8);
		assertThat(ejemplares.count()).isGreaterThan(20);

		assertThat(datos.cargar()).isFalse();
		assertThat(libros.count()).isEqualTo(20);
	}

	@Test
	void everyStateOfALoanAppears() {
		datos.cargar();

		var activos = prestamoService.listar(null, true, 0, 100).items();
		assertThat(activos).anyMatch(p -> p.diasRetraso() == 4);
		assertThat(activos).anyMatch(p -> p.renovaciones() == 1);
		assertThat(activos).anyMatch(p -> p.alertas().stream().anyMatch(a -> a.code() == AlertaCodigo.DUE_SOON));
		assertThat(prestamoService.listar(null, false, 0, 100).items()).isNotEmpty();
	}

	@Test
	void thereIsOnePendingFineAndOnePaidOne() {
		datos.cargar();

		var pendientes = multaService.listar(null, EstadoMulta.PENDING, 0, 10).items();
		var pagadas = multaService.listar(null, EstadoMulta.PAID, 0, 10).items();

		assertThat(pendientes).singleElement().satisfies(m -> assertThat(m.diasRetraso()).isEqualTo(11));
		assertThat(pagadas).singleElement().satisfies(m -> assertThat(m.diasRetraso()).isEqualTo(8));
	}

	@Test
	void theMembersShowTheirScoreAndAlerts() {
		datos.cargar();

		var lista = socioService.listar(null, 0, 20).items();
		var conMulta = lista.stream().filter(s -> s.nombre().equals("Mateo Quispe")).findFirst().orElseThrow();
		var puntual = lista.stream().filter(s -> s.nombre().equals("Diego Salazar")).findFirst().orElseThrow();
		var desactivado = lista.stream().filter(s -> s.nombre().equals("Hugo Rivas")).findFirst().orElseThrow();

		var mateo = socioService.obtener(conMulta.id());
		assertThat(mateo.score()).isLessThan(100);
		assertThat(mateo.alertas()).anyMatch(a -> a.code() == AlertaCodigo.PENDING_FINE);
		assertThat(socioService.obtener(puntual.id()).score()).isEqualTo(100);
		assertThat(desactivado.activo()).isFalse();
	}

	@Test
	void aCopyIsRetiredAndTheIsbnsAreWellFormed() {
		datos.cargar();

		assertThat(ejemplares.findAll()).anyMatch(e -> !e.isActivo());
		assertThat(libros.findAll()).allSatisfy(l -> assertThat(l.getIsbn()).matches("978[0-9]{10}"));
		assertThat(libros.findAll().stream().map(l -> l.getIsbn()).distinct().count()).isEqualTo(20);
	}

}
