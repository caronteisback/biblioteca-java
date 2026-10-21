package dev.portafolio.biblioteca.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import dev.portafolio.biblioteca.BaseApiSegura;
import dev.portafolio.biblioteca.entity.Ejemplar;
import dev.portafolio.biblioteca.entity.Socio;
import dev.portafolio.biblioteca.entity.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing in, the token and who may call what, through the real filters and the real database. The tokens come from
 * the real login; the forged ones are built here to prove each way of cheating is refused with the same answer.
 */
@AutoConfigureMockMvc
class SeguridadApiTests extends BaseApiSegura {

	@Autowired
	private Clock clock;

	@PersistenceContext
	private EntityManager em;

	private static String login(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	// ---- signing in

	@Test
	void aLibrarianSignsInAndGetsATokenAndTheirAccount() throws Exception {
		Usuario bibliotecario = bibliotecario();

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.expiraEn").value("2026-10-19T16:00:00Z"))
				.andExpect(jsonPath("$.usuario.rol").value("LIBRARIAN"))
				.andExpect(jsonPath("$.usuario.email").value(bibliotecario.getEmail()))
				.andExpect(jsonPath("$.usuario.socio").doesNotExist());
	}

	@Test
	void aMemberSignsInAndTheirScoreAndAlertsComeWithIt() throws Exception {
		Usuario cuenta = cuentaDe(socio());

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(cuenta.getEmail(), PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.rol").value("MEMBER"))
				.andExpect(jsonPath("$.usuario.socio.score").value(100))
				.andExpect(jsonPath("$.usuario.socio.alertas").isEmpty());
	}

	@Test
	void theEmailIsMatchedIgnoringCase() throws Exception {
		Usuario bibliotecario = bibliotecario();

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login(bibliotecario.getEmail().toUpperCase(), PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void aWrongPasswordAndAnUnknownEmailGetExactlyTheSameAnswer() throws Exception {
		Usuario bibliotecario = bibliotecario();

		String conPasswordMala = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login(bibliotecario.getEmail(), "wrong-password-123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();
		String conEmailDesconocido = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("nobody-" + System.nanoTime() + "@example.org", "wrong-password-123")))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(conPasswordMala).isEqualTo(conEmailDesconocido);
	}

	@Test
	void anAccountThatIsSwitchedOffCannotSignIn() throws Exception {
		Usuario bibliotecario = bibliotecario();
		jdbc.update("update usuario set activo = 0 where id = ?", bibliotecario.getId());
		em.clear();

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void afterFiveFailuresInARowTheEmailIsLockedEvenForTheRightPassword() throws Exception {
		Usuario bibliotecario = bibliotecario();
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), "wrong-password-123")))
					.andExpect(status().isUnauthorized());
		}

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), PASSWORD)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
	}

	@Test
	void aSuccessfulSignInStartsTheCountAgain() throws Exception {
		Usuario bibliotecario = bibliotecario();
		for (int i = 0; i < 4; i++) {
			mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), "wrong-password-123")))
					.andExpect(status().isUnauthorized());
		}
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), PASSWORD)))
				.andExpect(status().isOk());

		for (int i = 0; i < 4; i++) {
			mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), "wrong-password-123")))
					.andExpect(status().isUnauthorized());
		}
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(bibliotecario.getEmail(), PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void theLoginRequestIsValidated() throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\",\"password\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@example.org\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void noAnswerEverCarriesAPasswordOrAHash() throws Exception {
		Usuario cuenta = cuentaDe(socio());
		String token = token(cuenta);

		String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(cuenta.getEmail(), PASSWORD)))
				.andReturn().getResponse().getContentAsString();
		String yo = mvc.perform(get("/api/v1/me").header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString();

		assertThat(login).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("hash").doesNotContain("{bcrypt}");
		assertThat(yo).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("hash").doesNotContain("{bcrypt}");
	}

	// ---- without a valid token

	@Test
	void withoutATokenEveryOperationIsRefusedInTheCommonShape() throws Exception {
		mvc.perform(get("/api/v1/libros"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.message").value("You must sign in to use this operation."));
		mvc.perform(post("/api/v1/prestamos").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/v1/socios/1")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownAddressDoesNotRevealWhetherItExistsToSomeoneWhoIsNotSignedIn() throws Exception {
		mvc.perform(get("/api/v1/no-existe")).andExpect(status().isUnauthorized());
		mvc.perform(get("/no-existe")).andExpect(status().isUnauthorized());
	}

	@Test
	void aSignedInLibrarianGetsTheNormalNotFoundForAnUnknownAddress() throws Exception {
		String token = token(bibliotecario());

		mvc.perform(get("/api/v1/no-existe").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
	}

	@Test
	void aMalformedTokenIsRefused() throws Exception {
		for (String basura : List.of("not-a-token", "a.b.c", "", "Bearer")) {
			mvc.perform(get("/api/v1/libros").header("Authorization", "Bearer " + basura))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}
		mvc.perform(get("/api/v1/libros").header("Authorization", "Basic YWRtaW46YWRtaW4=")).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
		String falso = firmar("another-secret-of-more-than-32-bytes-xxxxxxxxxx", clock.instant().plus(Duration.ofHours(1)),
				EmisorDeTokens.EMISOR, "LIBRARIAN", "1");

		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(falso)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void anExpiredTokenIsRefused() throws Exception {
		Usuario bibliotecario = bibliotecario();
		String valido = token(bibliotecario);
		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(valido))).andExpect(status().isOk());

		String vencido = firmarConLaClaveReal(clock.instant().minusSeconds(1), EmisorDeTokens.EMISOR, "LIBRARIAN", String.valueOf(bibliotecario.getId()));

		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(vencido)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenFromAnotherIssuerIsRefused() throws Exception {
		String ajeno = firmarConLaClaveReal(clock.instant().plus(Duration.ofHours(1)), "someone-else", "LIBRARIAN", "1");

		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(ajeno))).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenWithoutSignatureIsRefused() throws Exception {
		Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
		String cabecera = base64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String contenido = base64.encodeToString(("{\"iss\":\"" + EmisorDeTokens.EMISOR + "\",\"sub\":\"1\",\"rol\":\"LIBRARIAN\",\"exp\":"
				+ clock.instant().plus(Duration.ofHours(1)).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));

		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(cabecera + "." + contenido + ".")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void editingTheRoleOfARealTokenBreaksItsSignature() throws Exception {
		String deSocio = token(cuentaDe(socio()));
		String[] partes = deSocio.split("\\.");
		String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8).replace("\"MEMBER\"", "\"LIBRARIAN\"");
		String editado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(contenido.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];
		assertThat(contenido).contains("LIBRARIAN");

		mvc.perform(get("/api/v1/socios").header("Authorization", bearer(editado)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aValidTokenWithAnUnknownRoleIsSignedInButMayDoNothing() throws Exception {
		String conRolInventado = firmarConLaClaveReal(clock.instant().plus(Duration.ofHours(1)), EmisorDeTokens.EMISOR, "ADMIN", "1");

		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(conRolInventado)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	// ---- roles

	@Test
	void aMemberReadsTheCatalogButNothingElse() throws Exception {
		String token = token(cuentaDe(socio()));
		Ejemplar ejemplar = ejemplar();

		mvc.perform(get("/api/v1/libros").header("Authorization", bearer(token))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/libros/" + ejemplar.getLibro().getId()).header("Authorization", bearer(token))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/libros/" + ejemplar.getLibro().getId() + "/ejemplares").header("Authorization", bearer(token))).andExpect(status().isOk());

		for (String ruta : List.of("/api/v1/socios", "/api/v1/prestamos", "/api/v1/multas", "/api/v1/ejemplares/" + ejemplar.getId())) {
			mvc.perform(get(ruta).header("Authorization", bearer(token)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		mvc.perform(post("/api/v1/libros").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/libros/" + ejemplar.getLibro().getId()).header("Authorization", bearer(token))).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/prestamos").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void aLibrarianDoesEverythingButHasNoDataOfTheirOwn() throws Exception {
		String token = token(bibliotecario());

		mvc.perform(get("/api/v1/socios").header("Authorization", bearer(token))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/multas").header("Authorization", bearer(token))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("LIBRARIAN"))
				.andExpect(jsonPath("$.socio").doesNotExist());
		mvc.perform(get("/api/v1/me/prestamos").header("Authorization", bearer(token))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/me/multas").header("Authorization", bearer(token))).andExpect(status().isForbidden());
	}

	@Test
	void aMemberSeesOnlyTheirOwnLoansAndFines() throws Exception {
		Socio ana = socio();
		Socio berta = socio();
		Ejemplar deAna = ejemplar();
		Ejemplar deBerta = ejemplar();
		var prestamoDeAna = prestamos.saveAndFlush(prestamo(deAna, ana, HOY, 14));
		prestamos.saveAndFlush(prestamo(deBerta, berta, HOY, 14));
		String tokenDeAna = token(cuentaDe(ana));

		String cuerpo = mvc.perform(get("/api/v1/me/prestamos").header("Authorization", bearer(tokenDeAna)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<Integer>>read(cuerpo, "$.items[*].socioId")).containsExactly(ana.getId().intValue());
		assertThat(JsonPath.<List<Integer>>read(cuerpo, "$.items[*].id")).containsExactly(prestamoDeAna.getId().intValue());

		// The member is never read from the request: asking for another one changes nothing.
		String conOtroSocio = mvc.perform(get("/api/v1/me/prestamos").param("socioId", String.valueOf(berta.getId())).header("Authorization", bearer(tokenDeAna)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<Integer>>read(conOtroSocio, "$.items[*].socioId")).containsExactly(ana.getId().intValue());

		mvc.perform(get("/api/v1/me/multas").header("Authorization", bearer(tokenDeAna)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void aMemberCannotAskForAnotherMembersLoansByAnyOtherRoute() throws Exception {
		Socio berta = socio();
		var prestamoDeBerta = prestamos.saveAndFlush(prestamo(ejemplar(), berta, HOY, 14));
		String tokenDeAna = token(cuentaDe(socio()));

		mvc.perform(get("/api/v1/prestamos/" + prestamoDeBerta.getId()).header("Authorization", bearer(tokenDeAna))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/socios/" + berta.getId()).header("Authorization", bearer(tokenDeAna))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/prestamos").param("socioId", String.valueOf(berta.getId())).header("Authorization", bearer(tokenDeAna)))
				.andExpect(status().isForbidden());
	}

	@Test
	void deletingAMemberAlsoDeletesTheirAccount() throws Exception {
		Socio socio = socio();
		Usuario cuenta = cuentaDe(socio);
		String tokenDelBibliotecario = token(bibliotecario());

		mvc.perform(delete("/api/v1/socios/" + socio.getId()).header("Authorization", bearer(tokenDelBibliotecario)))
				.andExpect(status().isNoContent());

		Integer restantes = jdbc.queryForObject("select count(*) from usuario where id = ?", Integer.class, cuenta.getId());
		assertThat(restantes).isZero();
	}

	// ---- the rest of the chain

	@Test
	void aPreflightFromAnAllowedOriginIsAnsweredWithoutAToken() throws Exception {
		mvc.perform(options("/api/v1/libros")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "GET")
						.header("Access-Control-Request-Headers", "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")));
	}

	@Test
	void theHealthCheckIsOpenAndTheOtherActuatorEndpointsAreNot() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
	}

	@Test
	void theApiDescriptionStaysHiddenWhenItIsSwitchedOff() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

	@Test
	void responsesToSignedInUsersAreNotCached() throws Exception {
		String token = token(bibliotecario());

		mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(content().string(not(containsString("token"))));
	}

	// ---- helpers

	private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
		return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
	}

	private String firmar(String secreto, Instant expira, String emisor, String rol, String sujeto) {
		var clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return construir(new NimbusJwtEncoder(new ImmutableSecret<>(clave)), expira, emisor, rol, sujeto);
	}

	@Autowired
	private javax.crypto.SecretKey claveDeFirma;

	private String firmarConLaClaveReal(Instant expira, String emisor, String rol, String sujeto) {
		return construir(new NimbusJwtEncoder(new ImmutableSecret<>(claveDeFirma)), expira, emisor, rol, sujeto);
	}

	private String construir(NimbusJwtEncoder encoder, Instant expira, String emisor, String rol, String sujeto) {
		Instant emitido = expira.minus(Duration.ofHours(1));
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(emisor).subject(sujeto).issuedAt(emitido).expiresAt(expira).claim("rol", rol).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

}
