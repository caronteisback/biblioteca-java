package dev.portafolio.biblioteca.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MultaServiceTests extends BaseIntegracionTest {

	@Autowired
	private MultaService servicio;

	@Autowired
	private PrestamoService prestamosServicio;

	@Autowired
	private ScoreCalculator score;

	/** Lends and returns 5 days late through the service, which creates the fine like production does. */
	private MultaResponse multaGenerada(Socio socio) {
		Prestamo vencido = prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(19), 14));
		return prestamosServicio.devolver(vencido.getId()).multa();
	}

	@Test
	void paysTheWholeFineAndRecordsTheDate() {
		MultaResponse multa = multaGenerada(socio());
		assertThat(multa.estado()).isEqualTo(EstadoMulta.PENDING);

		MultaResponse pagada = servicio.pagar(multa.id());

		assertThat(pagada.estado()).isEqualTo(EstadoMulta.PAID);
		assertThat(pagada.fechaPago()).isEqualTo(HOY);
		assertThat(pagada.monto()).isEqualByComparingTo("5.00");
		assertThat(servicio.obtener(multa.id()).estado()).isEqualTo(EstadoMulta.PAID);
	}

	@Test
	void aFineCannotBePaidTwice() {
		MultaResponse multa = multaGenerada(socio());
		servicio.pagar(multa.id());

		assertError(() -> servicio.pagar(multa.id()), ErrorCode.FINE_ALREADY_PAID);
	}

	@Test
	void anUnknownFineIsNotFound() {
		assertError(() -> servicio.obtener(-1L), ErrorCode.FINE_NOT_FOUND);
		assertError(() -> servicio.pagar(-1L), ErrorCode.FINE_NOT_FOUND);
	}

	@Test
	void payingUnblocksNewLoansButDoesNotGiveTheScoreBack() {
		Socio socio = socio();
		MultaResponse multa = multaGenerada(socio);
		assertError(() -> prestamosServicio.crear(new PrestamoRequest(socio.getId(), ejemplar().getId())), ErrorCode.MEMBER_HAS_PENDING_FINE);

		servicio.pagar(multa.id());

		PrestamoResponse nuevo = prestamosServicio.crear(new PrestamoRequest(socio.getId(), ejemplar().getId()));
		assertThat(nuevo.activo()).isTrue();
		assertThat(score.calcular(prestamos.findBySocioId(socio.getId()), HOY)).isEqualTo(85);
	}

	@Test
	void filtersByMemberAndByStatus() {
		Socio ana = socio();
		Socio luis = socio();
		MultaResponse deAnaPagada = multaGenerada(ana);
		servicio.pagar(deAnaPagada.id());
		MultaResponse deAnaPendiente = multaGenerada(ana);
		MultaResponse deLuis = multaGenerada(luis);

		PaginaDto<MultaResponse> deAna = servicio.listar(ana.getId(), null, 0, 20);
		PaginaDto<MultaResponse> pendientesDeAna = servicio.listar(ana.getId(), EstadoMulta.PENDING, 0, 20);
		PaginaDto<MultaResponse> pagadasDeAna = servicio.listar(ana.getId(), EstadoMulta.PAID, 0, 20);
		PaginaDto<MultaResponse> deLuisSolo = servicio.listar(luis.getId(), null, 0, 20);

		assertThat(deAna.items()).extracting(MultaResponse::id).containsExactly(deAnaPagada.id(), deAnaPendiente.id());
		assertThat(pendientesDeAna.items()).extracting(MultaResponse::id).containsExactly(deAnaPendiente.id());
		assertThat(pagadasDeAna.items()).extracting(MultaResponse::id).containsExactly(deAnaPagada.id());
		assertThat(deLuisSolo.items()).extracting(MultaResponse::id).containsExactly(deLuis.id());
	}

	@Test
	void withoutFiltersItListsEveryFine() {
		multaGenerada(socio());

		assertThat(servicio.listar(null, null, 0, 100).totalElements()).isGreaterThanOrEqualTo(1);
	}

}
