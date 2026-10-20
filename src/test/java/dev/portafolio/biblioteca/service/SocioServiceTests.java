package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.Alerta;
import dev.portafolio.biblioteca.dto.AlertaCodigo;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.SocioRequest;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.dto.SocioResumen;
import dev.portafolio.biblioteca.entity.Multa;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SocioServiceTests extends BaseIntegracionTest {

	@Autowired
	private SocioService servicio;

	private List<AlertaCodigo> codigos(SocioResponse respuesta) {
		return respuesta.alertas().stream().map(Alerta::code).toList();
	}

	@Test
	void createsAnActiveMemberRegisteredTodayWithAPerfectScore() {
		SocioResponse creado = servicio.crear(new SocioRequest("  Rosa Ficticia ", " rosa@example.org "));

		assertThat(creado.id()).isNotNull();
		assertThat(creado.nombre()).isEqualTo("Rosa Ficticia");
		assertThat(creado.email()).isEqualTo("rosa@example.org");
		assertThat(creado.activo()).isTrue();
		assertThat(creado.fechaRegistro()).isEqualTo(HOY);
		assertThat(creado.score()).isEqualTo(100);
		assertThat(creado.alertas()).isEmpty();
	}

	@Test
	void aDuplicateEmailIsRefusedIgnoringCase() {
		servicio.crear(new SocioRequest("Rosa", "rosa@example.org"));

		assertError(() -> servicio.crear(new SocioRequest("Otra Rosa", "ROSA@Example.ORG")), ErrorCode.DUPLICATE_EMAIL);
	}

	@Test
	void anUnknownMemberIsNotFoundInEveryOperation() {
		assertError(() -> servicio.obtener(-1L), ErrorCode.MEMBER_NOT_FOUND);
		assertError(() -> servicio.actualizar(-1L, new SocioRequest("X", "x@example.org")), ErrorCode.MEMBER_NOT_FOUND);
		assertError(() -> servicio.desactivar(-1L), ErrorCode.MEMBER_NOT_FOUND);
		assertError(() -> servicio.reactivar(-1L), ErrorCode.MEMBER_NOT_FOUND);
		assertError(() -> servicio.eliminar(-1L), ErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void theScoreFollowsTheLoanHistoryAtQueryTime() {
		Socio socio = socio();
		Prestamo atrasado = prestamo(ejemplar(), socio, HOY.minusDays(17), 14);
		prestamos.saveAndFlush(atrasado);

		SocioResponse vencido = servicio.obtener(socio.getId());
		assertThat(vencido.score()).isEqualTo(87);

		atrasado.devolver(HOY);
		prestamos.saveAndFlush(atrasado);
		assertThat(servicio.obtener(socio.getId()).score()).isEqualTo(87);
	}

	@Test
	void aScoreBelowTheThresholdRaisesLowScore() {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(59), 14));

		SocioResponse respuesta = servicio.obtener(socio.getId());

		assertThat(respuesta.score()).isEqualTo(45);
		assertThat(codigos(respuesta)).containsExactly(AlertaCodigo.LOW_SCORE);
	}

	@Test
	void aPendingFineRaisesPendingFineUntilItIsPaid() {
		Socio socio = socio();
		Prestamo prestamo = prestamo(ejemplar(), socio, HOY.minusDays(30), 14);
		prestamo.devolver(HOY.minusDays(11));
		prestamos.saveAndFlush(prestamo);
		Multa multa = multas.saveAndFlush(Multa.builder().prestamo(prestamo).diasRetraso(5).monto(new BigDecimal("5.00")).fechaGeneracion(HOY).build());

		assertThat(codigos(servicio.obtener(socio.getId()))).containsExactly(AlertaCodigo.PENDING_FINE);

		multa.pagar(HOY);
		multas.saveAndFlush(multa);

		assertThat(servicio.obtener(socio.getId()).alertas()).isEmpty();
		assertThat(servicio.obtener(socio.getId()).score()).isEqualTo(85);
	}

	@Test
	void updatesNameAndEmail() {
		SocioResponse creado = servicio.crear(new SocioRequest("Rosa", "rosa@example.org"));

		SocioResponse cambiado = servicio.actualizar(creado.id(), new SocioRequest("Rosa Maria", "rosa.maria@example.org"));

		assertThat(cambiado.nombre()).isEqualTo("Rosa Maria");
		assertThat(cambiado.email()).isEqualTo("rosa.maria@example.org");
		assertThat(cambiado.fechaRegistro()).isEqualTo(creado.fechaRegistro());
	}

	@Test
	void aMemberMayKeepItsOwnEmailOrChangeOnlyItsCase() {
		SocioResponse creado = servicio.crear(new SocioRequest("Rosa", "rosa@example.org"));

		assertThat(servicio.actualizar(creado.id(), new SocioRequest("Rosa", "rosa@example.org")).email()).isEqualTo("rosa@example.org");
		assertThat(servicio.actualizar(creado.id(), new SocioRequest("Rosa", "Rosa@Example.org")).email()).isEqualTo("Rosa@Example.org");
	}

	@Test
	void anUpdateCannotTakeTheEmailOfAnotherMember() {
		servicio.crear(new SocioRequest("Uno", "uno@example.org"));
		SocioResponse dos = servicio.crear(new SocioRequest("Dos", "dos@example.org"));

		assertError(() -> servicio.actualizar(dos.id(), new SocioRequest("Dos", "UNO@example.org")), ErrorCode.DUPLICATE_EMAIL);
	}

	@Test
	void aMemberWithActiveLoansCannotBeDeactivated() {
		Socio socio = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY, 14));

		assertError(() -> servicio.desactivar(socio.getId()), ErrorCode.MEMBER_HAS_ACTIVE_LOANS);
		assertThat(servicio.obtener(socio.getId()).activo()).isTrue();
	}

	@Test
	void aMemberWithOnlyReturnedLoansCanBeDeactivatedAndReactivated() {
		Socio socio = socio();
		Prestamo prestamo = prestamo(ejemplar(), socio, HOY.minusDays(10), 14);
		prestamo.devolver(HOY.minusDays(5));
		prestamos.saveAndFlush(prestamo);

		assertThat(servicio.desactivar(socio.getId()).activo()).isFalse();
		assertThat(servicio.reactivar(socio.getId()).activo()).isTrue();
	}

	@Test
	void aMemberWithoutHistoryCanBeDeleted() {
		SocioResponse creado = servicio.crear(new SocioRequest("Efimero", "efimero@example.org"));

		servicio.eliminar(creado.id());

		assertError(() -> servicio.obtener(creado.id()), ErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void aMemberThatAppearsInALoanIsNeverDeleted() {
		Socio socio = socio();
		Prestamo prestamo = prestamo(ejemplar(), socio, HOY.minusDays(10), 14);
		prestamo.devolver(HOY.minusDays(5));
		prestamos.saveAndFlush(prestamo);

		assertError(() -> servicio.eliminar(socio.getId()), ErrorCode.MEMBER_HAS_HISTORY);
		assertThat(servicio.obtener(socio.getId()).nombre()).isEqualTo(socio.getNombre());
	}

	@Test
	void filtersByNameOrEmailIgnoringCaseWithoutScoreInTheList() {
		servicio.crear(new SocioRequest("Carmen Ficticia", "carmen@example.org"));
		servicio.crear(new SocioRequest("Pedro Inventado", "pedro.carmenes@example.org"));
		servicio.crear(new SocioRequest("Luisa Imaginaria", "luisa@example.org"));

		PaginaDto<SocioResumen> pagina = servicio.listar("CARMEN", 0, 20);

		assertThat(pagina.items()).extracting(SocioResumen::nombre).containsExactlyInAnyOrder("Carmen Ficticia", "Pedro Inventado");
		assertThat(servicio.listar("imaginaria", 0, 20).items()).extracting(SocioResumen::email).containsExactly("luisa@example.org");
	}

}
