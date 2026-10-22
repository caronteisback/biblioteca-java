package dev.portafolio.biblioteca.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.LoginRequest;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import dev.portafolio.biblioteca.security.SeguridadProperties;
import dev.portafolio.biblioteca.service.AutenticacionService;
import dev.portafolio.biblioteca.service.EjemplarService;
import dev.portafolio.biblioteca.service.LibroService;
import dev.portafolio.biblioteca.service.UsuarioService;
import dev.portafolio.biblioteca.repository.SocioRepository;
import dev.portafolio.biblioteca.service.MultaService;
import dev.portafolio.biblioteca.service.PanelService;
import dev.portafolio.biblioteca.service.PrestamoService;
import dev.portafolio.biblioteca.service.SocioService;
import java.time.Duration;
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
	private PanelService panel;

	@Autowired
	private PrestamoService prestamoService;

	@Autowired
	private MultaService multaService;

	@Autowired
	private EjemplarService ejemplarService;

	@Autowired
	private AutenticacionService autenticacion;

	@Autowired
	private UsuarioRepository usuariosRepo;

	@Autowired
	private SocioRepository socioRepository;

	@Autowired
	private UsuarioService usuarioService;

	@Autowired
	private LibroService libroService;

	@Autowired
	private SeguridadProperties seguridad;

	@AfterEach
	void limpiar() {
		jdbc.update("delete from usuario");
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
	void theDemoGivesTheDashboardSomethingToShow() {
		datos.cargar();

		var resumen = panel.resumen();

		assertThat(resumen.prestamos().activos()).isEqualTo(5);
		assertThat(resumen.prestamos().vencidos()).as("Rosa's loan").isEqualTo(1);
		assertThat(resumen.prestamos().porVencer()).as("Andrés's loan falls due in two days").isEqualTo(1);
		assertThat(resumen.multas().pendientes()).isEqualTo(1);
		assertThat(resumen.multas().montoPendiente()).isEqualByComparingTo("11.00");
		assertThat(resumen.multas().sociosConMultaPendiente()).isEqualTo(1);
		assertThat(resumen.sociosActivos()).isEqualTo(7);

		assertThat(resumen.prestamosPorSemana()).hasSize(6);
		assertThat(resumen.prestamosPorSemana().stream().filter(semana -> semana.prestamos() > 0)).as("weeks with loans").hasSizeGreaterThanOrEqualTo(5);

		assertThat(resumen.masPrestados()).hasSize(5);
		assertThat(resumen.masPrestados().get(0).titulo()).isEqualTo("El Faro Imaginado");
		assertThat(resumen.masPrestados().get(0).prestamos()).isEqualTo(5);
		assertThat(resumen.masPrestados().get(1).titulo()).isEqualTo("Cartografía de la Niebla");
		assertThat(resumen.masPrestados().stream().map(libro -> libro.prestamos()).toList()).isSortedAccordingTo(java.util.Comparator.reverseOrder());

		assertThat(resumen.requierenAtencion()).hasSize(2);
		assertThat(resumen.requierenAtencion().get(0).nombreSocio()).isEqualTo("Rosa Valdivia");
		assertThat(resumen.requierenAtencion().get(0).diasRetraso()).isEqualTo(4);
		assertThat(resumen.requierenAtencion().get(1).nombreSocio()).isEqualTo("Andrés Paredes");
		assertThat(resumen.requierenAtencion().get(1).alerta()).isEqualTo(AlertaCodigo.DUE_SOON);
	}

	@Test
	void theHistoryIsReturnedOnTimeSoItDoesNotChangeAnyScoreOrFine() {
		datos.cargar();

		assertThat(multaService.listar(null, null, 0, 100).items()).hasSize(2);
		assertThat(socioService.obtener(socios.findByEmailIgnoreCase("andres.paredes@example.org").orElseThrow().getId()).score()).isEqualTo(100);
		assertThat(prestamoService.listar(null, false, 0, 100).items().size()).isGreaterThanOrEqualTo(19);
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

	@Test
	void theDemoCreatesThreeAccountsThatCanSignInWithThePasswordOfTheEnvironment() {
		datos.cargar();

		assertThat(usuariosRepo.count()).isEqualTo(3);
		var bibliotecaria = autenticacion.entrar(new LoginRequest("bibliotecaria@example.org", seguridad.demoPassword()));
		var rosa = autenticacion.entrar(new LoginRequest("rosa.valdivia@example.org", seguridad.demoPassword()));
		var mateo = autenticacion.entrar(new LoginRequest("mateo.quispe@example.org", seguridad.demoPassword()));

		assertThat(bibliotecaria.usuario().rol().name()).isEqualTo("LIBRARIAN");
		assertThat(rosa.usuario().rol().name()).isEqualTo("MEMBER");
		assertThat(rosa.usuario().socio().nombre()).isEqualTo("Rosa Valdivia");
		assertThat(mateo.usuario().socio().alertas()).anyMatch(a -> a.code() == AlertaCodigo.PENDING_FINE);
		assertThat(mateo.usuario().socio().score()).isLessThan(100);
	}

	@Test
	void loadingTwiceDoesNotDuplicateTheAccounts() {
		datos.cargar();
		datos.cargar();

		assertThat(usuariosRepo.count()).isEqualTo(3);
	}

	@Test
	void anExistingDemoDatabaseWithoutAccountsGetsThemOnTheNextStart() {
		datos.cargar();
		jdbc.update("delete from usuario");

		assertThat(datos.cargar()).as("the catalog is not loaded again").isFalse();

		assertThat(usuariosRepo.count()).isEqualTo(3);
	}

	@Test
	void withoutAPasswordTheDemoStopsInsteadOfCreatingAccountsWithAGuessableOne() {
		SeguridadProperties sinPassword = new SeguridadProperties("k".repeat(32), Duration.ofMinutes(60), 5, Duration.ofMinutes(5), null, null, "");
		DatosDeEjemplo sinClave = new DatosDeEjemplo(libros, libroService, ejemplarService, socioService, prestamoService, multaService,
				jdbc, socioRepository, usuariosRepo, usuarioService, sinPassword);

		assertThatThrownBy(sinClave::cargar).isInstanceOf(IllegalStateException.class).hasMessageContaining("BIBLIOTECA_DEMO_PASSWORD");
		assertThat(usuariosRepo.count()).isZero();
	}

}
