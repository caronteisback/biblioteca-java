package dev.portafolio.biblioteca.config;

import dev.portafolio.biblioteca.dto.EjemplarRequest;
import dev.portafolio.biblioteca.dto.EjemplarResponse;
import dev.portafolio.biblioteca.dto.LibroRequest;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.SocioRequest;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.repository.LibroRepository;
import dev.portafolio.biblioteca.repository.SocioRepository;
import dev.portafolio.biblioteca.repository.UsuarioRepository;
import dev.portafolio.biblioteca.security.SeguridadProperties;
import dev.portafolio.biblioteca.service.EjemplarService;
import dev.portafolio.biblioteca.service.LibroService;
import dev.portafolio.biblioteca.service.MultaService;
import dev.portafolio.biblioteca.service.PrestamoService;
import dev.portafolio.biblioteca.service.SocioService;
import dev.portafolio.biblioteca.service.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A small fictional library to look at: books, copies, members and loans in every state a screen can show
 * (on time, due soon, renewed, overdue, returned late with a pending fine, returned late and paid).
 *
 * <p>Everything goes through the real services, so the rules apply to the demo as to any request. The only
 * exception is time: a loan is always made today, so "weeks ago" is made by moving its dates back in the
 * database, which keeps "overdue" overdue whichever day the demo is started. It loads only into an empty
 * catalog and is started only by the {@code demo} profile (see {@link CargadorDeDemo}). All the data is made up.
 *
 * <p>It also creates three accounts to sign in with: a librarian and the members Rosa and Mateo (the ones with an
 * overdue loan and a pending fine). Their password is the one of {@code BIBLIOTECA_DEMO_PASSWORD}; it is not in the code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatosDeEjemplo {

	private static final String[][] LIBROS = {
			{ "El Faro Imaginado", "Clara Montero", "2001" },
			{ "Cartografía de la Niebla", "Tomás Arrieta", "2008" },
			{ "La Biblioteca de Arena", "Inés Valcárcel", "1997" },
			{ "Cuentos del Río Lento", "Mateo Salinas", "2013" },
			{ "Manual del Jardín Urbano", "Paula Ferrer", "2019" },
			{ "Historia Breve de los Relojes", "Álvaro Quintana", "2005" },
			{ "Noches en la Estación Norte", "Lucía Ordóñez", "2016" },
			{ "Aprender Álgebra sin Miedo", "Ricardo Bellido", "2011" },
			{ "El Cartero de las Islas", "Marina Cáceres", "1999" },
			{ "Fundamentos de Redes", "Sergio Lantigua", "2020" },
			{ "Recetas de la Abuela Remota", "Rosario Paredes", "2010" },
			{ "Atlas de Aves del Sur", "Gonzalo Vidal", "2014" },
			{ "Poemas de Marea Baja", "Elisa Roldán", "2003" },
			{ "Guía de Estrellas Fugaces", "Bruno Ocampo", "2017" },
			{ "Ajedrez para Curiosos", "Nicolás Duarte", "2007" },
			{ "La Casa del Eco", "Teresa Linares", "2012" },
			{ "Introducción a la Estadística", "Héctor Maldonado", "2018" },
			{ "Viaje al Centro del Papel", "Julia Benavente", "2002" },
			{ "El Último Tranvía", "Óscar Ledesma", "2009" },
			{ "Memorias de un Cartógrafo", "Federico Pinedo", "1995" },
	};

	private static final String[][] SOCIOS = {
			{ "Andrés Paredes", "andres.paredes@example.org" },
			{ "Rosa Valdivia", "rosa.valdivia@example.org" },
			{ "Mateo Quispe", "mateo.quispe@example.org" },
			{ "Lucía Herrera", "lucia.herrera@example.org" },
			{ "Valeria Campos", "valeria.campos@example.org" },
			{ "Camila Torres", "camila.torres@example.org" },
			{ "Diego Salazar", "diego.salazar@example.org" },
			{ "Hugo Rivas", "hugo.rivas@example.org" },
	};

	private final LibroRepository libros;
	private final LibroService libroService;
	private final EjemplarService ejemplarService;
	private final SocioService socioService;
	private final PrestamoService prestamoService;
	private final MultaService multaService;
	private final JdbcTemplate jdbc;
	private final SocioRepository socioRepository;
	private final UsuarioRepository usuarios;
	private final UsuarioService usuarioService;
	private final SeguridadProperties seguridad;

	/**
	 * Loads the demo catalog when it is empty and the accounts when there are none. Returns whether the catalog was
	 * loaded; the accounts are created on their own so a demo database from before the sign-in gets them too.
	 */
	public boolean cargar() {
		boolean catalogo = cargarCatalogo();
		cargarCuentas();
		return catalogo;
	}

	private void cargarCuentas() {
		if (usuarios.count() > 0) {
			return;
		}
		String password = seguridad.demoPassword();
		if (password == null || password.isBlank()) {
			throw new IllegalStateException("The demo profile creates accounts and needs BIBLIOTECA_DEMO_PASSWORD "
					+ "(at least " + UsuarioService.LONGITUD_MINIMA_PASSWORD + " characters).");
		}
		usuarioService.crearBibliotecario("bibliotecaria@example.org", password);
		for (String[] socio : SOCIOS) {
			if (socio[0].equals("Rosa Valdivia") || socio[0].equals("Mateo Quispe")) {
				socioRepository.findByEmailIgnoreCase(socio[1])
						.ifPresent(encontrado -> usuarioService.crearSocio(socio[1], password, encontrado.getId()));
			}
		}
		log.info("Demo accounts created: bibliotecaria@example.org, rosa.valdivia@example.org, mateo.quispe@example.org.");
	}

	private boolean cargarCatalogo() {
		if (libros.count() > 0) {
			log.info("The catalog already has books: the demo catalog is not loaded.");
			return false;
		}

		List<List<EjemplarResponse>> copias = new ArrayList<>();
		long codigo = 1;
		for (int i = 0; i < LIBROS.length; i++) {
			String[] datos = LIBROS[i];
			var libro = libroService.crear(new LibroRequest(isbn13(i), datos[0], datos[1], Integer.valueOf(datos[2])));
			List<EjemplarResponse> delLibro = new ArrayList<>();
			for (int k = 0; k <= i % 3; k++) {
				delLibro.add(ejemplarService.crear(libro.id(), new EjemplarRequest(String.format("EJ-%03d", codigo++))));
			}
			copias.add(delLibro);
		}

		List<SocioResponse> socios = new ArrayList<>();
		for (String[] datos : SOCIOS) {
			socios.add(socioService.crear(new SocioRequest(datos[0], datos[1])));
		}
		long andres = socios.get(0).id();
		long rosa = socios.get(1).id();
		long mateo = socios.get(2).id();
		long lucia = socios.get(3).id();
		long valeria = socios.get(4).id();
		long camila = socios.get(5).id();
		long hugo = socios.get(7).id();

		// The history of the last weeks, returned on time: it is what the dashboard counts per week and ranks by book.
		// It goes first, before any member has an overdue loan or a fine that would stop a new loan.
		historial(socios, copias);

		// Andrés: one loan that falls due in two days and one that he renewed.
		prestar(andres, copias.get(0).get(0), 12);
		prestamoService.renovar(prestar(andres, copias.get(1).get(0), 3));

		// Rosa: an active loan that is four days overdue.
		prestar(rosa, copias.get(2).get(0), 18);

		// Mateo: returned eleven days late, with the fine still pending (he cannot borrow).
		devolver(prestar(mateo, copias.get(3).get(0), 25));

		// Lucía: returned eight days late and paid the fine (her score stays lower).
		multaService.pagar(devolver(prestar(lucia, copias.get(4).get(0), 22)));

		// Valeria: two loans on time. Camila: only an old loan, returned on time.
		prestar(valeria, copias.get(5).get(0), 3);
		prestar(valeria, copias.get(8).get(0), 6);
		devolver(prestar(camila, copias.get(6).get(0), 5));

		// A deactivated member and a retired copy.
		socioService.desactivar(hugo);
		ejemplarService.darDeBaja(copias.get(7).get(copias.get(7).size() - 1).id());

		log.info("Demo data loaded: {} books, {} members.", LIBROS.length, socios.size());
		return true;
	}

	/**
	 * Loans of the last weeks that were returned on time: book, copy of the book, member, days ago it started and days it
	 * lasted. The member is 0 Andrés, 1 Valeria, 2 Camila or 3 Lucía. The loans of a copy do not overlap one another or the
	 * ones it has now. Together with the current loans they give a ranking with a clear first place and a few weeks of history.
	 */
	private static final int[][] HISTORIAL = {
			{ 0, 0, 3, 40, 6 }, { 0, 0, 1, 33, 9 }, { 0, 0, 2, 22, 3 }, { 0, 0, 0, 18, 5 },
			{ 1, 1, 0, 35, 8 }, { 1, 1, 1, 21, 6 }, { 1, 1, 3, 9, 4 },
			{ 9, 0, 2, 30, 10 }, { 9, 0, 0, 16, 7 }, { 9, 0, 1, 6, 3 },
			{ 4, 1, 0, 14, 5 }, { 4, 1, 2, 28, 6 },
			{ 5, 1, 3, 24, 4 }, { 5, 1, 2, 11, 6 },
			{ 6, 0, 1, 31, 7 },
			{ 12, 0, 0, 38, 5 }, { 12, 0, 3, 7, 3 },
			{ 15, 0, 1, 13, 6 },
			{ 18, 0, 2, 4, 2 },
	};

	/**
	 * Fills the history. A loan is made today through the service (so the rules apply) and then moved back in time and
	 * marked as returned on a day of its own: returning it through the service would return it today, and late.
	 */
	private void historial(List<SocioResponse> socios, List<List<EjemplarResponse>> copias) {
		long[] miembros = { socios.get(0).id(), socios.get(4).id(), socios.get(5).id(), socios.get(3).id() };
		for (int[] fila : HISTORIAL) {
			long id = prestar(miembros[fila[2]], copias.get(fila[0]).get(fila[1]), fila[3]);
			jdbc.update("update prestamo set fecha_devolucion = fecha_prestamo + ? where id = ?", fila[4], id);
		}
	}

	/** Lends a copy today and moves the loan back in time as if it had been made that many days ago. */
	private long prestar(long socioId, EjemplarResponse ejemplar, int hacia) {
		long id = prestamoService.crear(new PrestamoRequest(socioId, ejemplar.id())).id();
		jdbc.update("update prestamo set fecha_prestamo = fecha_prestamo - ?, fecha_vencimiento = fecha_vencimiento - ? where id = ?",
				hacia, hacia, id);
		return id;
	}

	/** Returns a loan today; when it is late, the service creates the fine. Gives the id of the fine, or 0 if none. */
	private long devolver(long prestamoId) {
		var respuesta = prestamoService.devolver(prestamoId);
		return respuesta.multa() == null ? 0 : respuesta.multa().id();
	}

	/** A well-formed ISBN-13 (978 prefix, with its check digit) for the n-th demo book. */
	static String isbn13(int n) {
		String base = String.format("978%09d", 100_000_000 + n * 7_919);
		int suma = 0;
		for (int i = 0; i < 12; i++) {
			suma += (base.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
		}
		return base + (10 - suma % 10) % 10;
	}

}
