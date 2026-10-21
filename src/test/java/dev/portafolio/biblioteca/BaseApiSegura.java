package dev.portafolio.biblioteca;

import com.jayway.jsonpath.JsonPath;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.entity.Usuario;
import dev.portafolio.biblioteca.service.UsuarioService;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * For tests that go through the whole API with real accounts: they create the accounts, sign in through the real
 * endpoint and use the token that comes back, so the filters, the token and the rules are all exercised.
 * Each concrete test class adds {@code @AutoConfigureMockMvc}.
 */
public abstract class BaseApiSegura extends BaseIntegracionTest {

	/** Long enough for the policy of the accounts (12 characters). It protects nothing outside the tests. */
	protected static final String PASSWORD = "a-password-for-the-tests";

	private static final AtomicLong SEQ_CUENTAS = new AtomicLong(1000);

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected UsuarioService cuentas;

	protected Usuario bibliotecario() {
		return cuentas.crearBibliotecario("bibliotecario" + SEQ_CUENTAS.incrementAndGet() + "@example.org", PASSWORD);
	}

	protected Usuario cuentaDe(Socio socio) {
		return cuentas.crearSocio("cuenta" + SEQ_CUENTAS.incrementAndGet() + "@example.org", PASSWORD, socio.getId());
	}

	/** Signs in through the real endpoint and returns the bearer token. */
	protected String entrar(String email) throws Exception {
		String cuerpo = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.token");
	}

	protected String token(Usuario usuario) throws Exception {
		return entrar(usuario.getEmail());
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

}
