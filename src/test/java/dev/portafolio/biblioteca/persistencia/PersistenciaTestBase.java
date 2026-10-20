package dev.portafolio.biblioteca.persistencia;

import dev.portafolio.biblioteca.TestcontainersConfiguration;
import dev.portafolio.biblioteca.ejemplar.Ejemplar;
import dev.portafolio.biblioteca.ejemplar.EjemplarRepository;
import dev.portafolio.biblioteca.libro.Libro;
import dev.portafolio.biblioteca.libro.LibroRepository;
import dev.portafolio.biblioteca.multa.MultaRepository;
import dev.portafolio.biblioteca.prestamo.Prestamo;
import dev.portafolio.biblioteca.prestamo.PrestamoRepository;
import dev.portafolio.biblioteca.socio.Socio;
import dev.portafolio.biblioteca.socio.SocioRepository;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real Oracle XE (Testcontainers) with the Flyway schema. Every test runs in a transaction that is
 * rolled back, so tests do not see each other's rows. All data is fictional.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
abstract class PersistenciaTestBase {

	protected static final LocalDate HOY = LocalDate.of(2026, 10, 19);

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
