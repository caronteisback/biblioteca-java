package dev.portafolio.biblioteca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Libro;
import dev.portafolio.biblioteca.entity.Prestamo;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.repository.EjemplarRepository;
import dev.portafolio.biblioteca.repository.LibroRepository;
import dev.portafolio.biblioteca.repository.MultaRepository;
import dev.portafolio.biblioteca.repository.PrestamoRepository;
import dev.portafolio.biblioteca.repository.SocioRepository;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real Oracle XE (Testcontainers) with the Flyway schema and a frozen clock ({@link #HOY}). Every
 * test runs in a transaction that is rolled back, so tests do not see each other's rows. All data
 * is fictional.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, RelojDePrueba.class })
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegracionTest {

	protected static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	/** The call must fail with a {@link BusinessException} carrying exactly this error code. */
	protected static void assertError(ThrowingCallable call, ErrorCode code) {
		assertThatThrownBy(call)
				.isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(code));
	}

	private static final AtomicLong SEQ = new AtomicLong(1000);

	@Autowired
	protected LibroRepository libros;

	@Autowired
	protected EjemplarRepository ejemplares;

	@Autowired
	protected SocioRepository socios;

	@Autowired
	protected PrestamoRepository prestamos;

	@Autowired
	protected MultaRepository multas;

	@Autowired
	protected JdbcTemplate jdbc;

	protected Libro libro() {
		long n = SEQ.incrementAndGet();
		return libros.saveAndFlush(Libro.builder()
				.isbn(String.format("978%010d", n))
				.titulo("Libro de prueba " + n)
				.autor("Autora Ficticia")
				.anioPublicacion(2001)
				.build());
	}

	protected Ejemplar ejemplar(Libro libro) {
		long n = SEQ.incrementAndGet();
		return ejemplares.saveAndFlush(Ejemplar.builder().libro(libro).codigo("EJ-" + n).build());
	}

	protected Ejemplar ejemplar() {
		return ejemplar(libro());
	}

	protected Socio socio() {
		long n = SEQ.incrementAndGet();
		return socios.saveAndFlush(Socio.builder()
				.nombre("Socio de prueba " + n)
				.email("socio" + n + "@example.org")
				.fechaRegistro(HOY.minusDays(30))
				.build());
	}

	/** Builds, without saving, a loan that started on {@code desde} and is due {@code dias} later. */
	protected Prestamo prestamo(Ejemplar ejemplar, Socio socio, LocalDate desde, int dias) {
		return Prestamo.builder()
				.ejemplar(ejemplar)
				.socio(socio)
				.fechaPrestamo(desde)
				.fechaVencimiento(desde.plusDays(dias))
				.build();
	}

}
