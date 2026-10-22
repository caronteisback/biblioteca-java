package dev.portafolio.biblioteca.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The life of a loan told over HTTP against Oracle with the real services and the real error
 * handler. The clock is frozen at 2026-10-19, so "late" loans are seeded in the database.
 */
@WithMockUser(roles = "LIBRARIAN")
@AutoConfigureMockMvc
class PrestamosApiTests extends BaseIntegracionTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EntityManagerFactory fabrica;

	@PersistenceContext
	private EntityManager em;

	private String idDe(String json) {
		return json.replaceAll("^\\{\"id\":(\\d+).*$", "$1");
	}

	private String prestar(Socio socio, Ejemplar ejemplar) throws Exception {
		return mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON)
						.content("{\"socioId\":" + socio.getId() + ",\"ejemplarId\":" + ejemplar.getId() + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void aLoanIsCreatedRenewedThreeTimesAndReturnedOnTime() throws Exception {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();

		String creado = prestar(socio, ejemplar);
		String id = idDe(creado);
		mvc.perform(get("/api/v1/prestamos/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaPrestamo").value("2026-10-19"))
				.andExpect(jsonPath("$.fechaVencimiento").value("2026-11-02"));

		mvc.perform(post("/api/v1/prestamos/" + id + "/renovacion")).andExpect(status().isOk()).andExpect(jsonPath("$.fechaVencimiento").value("2026-11-16"));
		mvc.perform(post("/api/v1/prestamos/" + id + "/renovacion")).andExpect(status().isOk()).andExpect(jsonPath("$.fechaVencimiento").value("2026-11-30"));
		mvc.perform(post("/api/v1/prestamos/" + id + "/renovacion")).andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaVencimiento").value("2026-12-14"))
				.andExpect(jsonPath("$.renovaciones").value(3));
		mvc.perform(post("/api/v1/prestamos/" + id + "/renovacion"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("RENEWAL_LIMIT_REACHED"));

		mvc.perform(post("/api/v1/prestamos/" + id + "/devolucion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false))
				.andExpect(jsonPath("$.multa").doesNotExist());
		mvc.perform(post("/api/v1/prestamos/" + id + "/devolucion"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));
	}

	@Test
	void everyLoanNamesItsBookAndItsCopy() throws Exception {
		Socio socio = socio();
		Ejemplar ejemplar = ejemplar();

		String creado = prestar(socio, ejemplar);
		String id = idDe(creado);
		assertThat(JsonPath.<String>read(creado, "$.tituloLibro")).isEqualTo(ejemplar.getLibro().getTitulo());
		assertThat(JsonPath.<String>read(creado, "$.codigoEjemplar")).isEqualTo(ejemplar.getCodigo());

		mvc.perform(get("/api/v1/prestamos/" + id))
				.andExpect(jsonPath("$.tituloLibro").value(ejemplar.getLibro().getTitulo()))
				.andExpect(jsonPath("$.codigoEjemplar").value(ejemplar.getCodigo()));
		mvc.perform(get("/api/v1/prestamos").param("socioId", String.valueOf(socio.getId())))
				.andExpect(jsonPath("$.items[0].tituloLibro").value(ejemplar.getLibro().getTitulo()))
				.andExpect(jsonPath("$.items[0].codigoEjemplar").value(ejemplar.getCodigo()));
		mvc.perform(post("/api/v1/prestamos/" + id + "/renovacion"))
				.andExpect(jsonPath("$.tituloLibro").value(ejemplar.getLibro().getTitulo()));
		mvc.perform(post("/api/v1/prestamos/" + id + "/devolucion"))
				.andExpect(jsonPath("$.tituloLibro").value(ejemplar.getLibro().getTitulo()))
				.andExpect(jsonPath("$.codigoEjemplar").value(ejemplar.getCodigo()));
	}

	@Test
	void listingLoansCostsTheSameNumberOfQueriesWhateverTheNumberOfLoans() throws Exception {
		Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		Socio conUno = socio();
		Socio conCuatro = socio();
		prestamos.saveAndFlush(prestamo(ejemplar(), conUno, HOY, 14));
		for (int i = 0; i < 4; i++) {
			prestamos.saveAndFlush(prestamo(ejemplar(), conCuatro, HOY, 14));
		}

		long conUnPrestamo = consultasAlListar(estadisticas, conUno);
		long conCuatroPrestamos = consultasAlListar(estadisticas, conCuatro);

		assertThat(conCuatroPrestamos).as("queries to list 4 loans vs 1 loan").isEqualTo(conUnPrestamo);
	}

	private long consultasAlListar(Statistics estadisticas, Socio socio) throws Exception {
		// The test runs inside one transaction: without this the loans, copies and books are still in memory and no query is needed.
		em.flush();
		em.clear();
		estadisticas.clear();
		String cuerpo = mvc.perform(get("/api/v1/prestamos").param("socioId", String.valueOf(socio.getId())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(cuerpo, "$.items[*].tituloLibro")).isNotEmpty().doesNotContainNull();
		return estadisticas.getPrepareStatementCount();
	}

	@Test
	void aCopyOnLoanCannotBeLentToAnotherMember() throws Exception {
		Ejemplar ejemplar = ejemplar();
		prestar(socio(), ejemplar);

		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON)
						.content("{\"socioId\":" + socio().getId() + ",\"ejemplarId\":" + ejemplar.getId() + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COPY_ALREADY_ON_LOAN"));
	}

	@Test
	void aLateReturnLeadsToAFineThatBlocksUntilItIsPaid() throws Exception {
		Socio socio = socio();
		Prestamo vencido = prestamos.saveAndFlush(prestamo(ejemplar(), socio, HOY.minusDays(19), 14));
		String nuevo = "{\"socioId\":" + socio.getId() + ",\"ejemplarId\":%d}";

		mvc.perform(get("/api/v1/prestamos/" + vencido.getId()))
				.andExpect(jsonPath("$.diasRetraso").value(5))
				.andExpect(jsonPath("$.alertas[0].code").value("OVERDUE"))
				.andExpect(jsonPath("$.alertas[0].message").value("Overdue by 5 day(s). Fine so far: S/ 5.00."));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(nuevo.formatted(ejemplar().getId())))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MEMBER_HAS_OVERDUE_LOAN"));

		String devolucion = mvc.perform(post("/api/v1/prestamos/" + vencido.getId() + "/devolucion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.multa.monto").value(5.00))
				.andExpect(jsonPath("$.multa.estado").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		String multaId = devolucion.replaceAll("^.*\"multa\":\\{\"id\":(\\d+).*$", "$1");

		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(nuevo.formatted(ejemplar().getId())))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("MEMBER_HAS_PENDING_FINE"));
		mvc.perform(get("/api/v1/multas").param("socioId", String.valueOf(socio.getId())).param("estado", "PENDING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(Long.parseLong(multaId)));
		mvc.perform(get("/api/v1/socios/" + socio.getId()))
				.andExpect(jsonPath("$.score").value(85))
				.andExpect(jsonPath("$.alertas[0].code").value("PENDING_FINE"));

		mvc.perform(post("/api/v1/multas/" + multaId + "/pago"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAID"))
				.andExpect(jsonPath("$.fechaPago").value("2026-10-19"));
		mvc.perform(post("/api/v1/multas/" + multaId + "/pago"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("FINE_ALREADY_PAID"));

		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content(nuevo.formatted(ejemplar().getId())))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/v1/socios/" + socio.getId()))
				.andExpect(jsonPath("$.score").value(85))
				.andExpect(jsonPath("$.alertas").isEmpty());
	}

	@Test
	void anInvalidLoanRequestAndUnknownIdsUseTheDocumentedErrors() throws Exception {
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("socioId: must not be null")));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON)
						.content("{\"socioId\":999999,\"ejemplarId\":" + ejemplar().getId() + "}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		mvc.perform(get("/api/v1/prestamos/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
		mvc.perform(post("/api/v1/prestamos/999999/renovacion")).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/prestamos/999999/devolucion")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/multas/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FINE_NOT_FOUND"));
		mvc.perform(post("/api/v1/multas/999999/pago")).andExpect(status().isNotFound());
	}

	@Test
	void listsTheLoansOfAMemberFilteredByStatus() throws Exception {
		Socio socio = socio();
		String vigente = idDe(prestar(socio, ejemplar()));
		String devuelto = idDe(prestar(socio, ejemplar()));
		mvc.perform(post("/api/v1/prestamos/" + devuelto + "/devolucion")).andExpect(status().isOk());

		mvc.perform(get("/api/v1/prestamos").param("socioId", String.valueOf(socio.getId())).param("activo", "true"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(Long.parseLong(vigente)));
		mvc.perform(get("/api/v1/prestamos").param("socioId", String.valueOf(socio.getId())).param("activo", "false"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(Long.parseLong(devuelto)));
		mvc.perform(get("/api/v1/prestamos").param("socioId", String.valueOf(socio.getId())))
				.andExpect(jsonPath("$.totalElements").value(2));
	}

}
