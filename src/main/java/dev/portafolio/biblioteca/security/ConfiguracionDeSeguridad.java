package dev.portafolio.biblioteca.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.portafolio.biblioteca.entity.Rol;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * How the API is protected.
 *
 * <ul>
 * <li><b>Stateless:</b> no session and no cookie. Every request carries its own {@code Authorization: Bearer}
 * token, so there is nothing for a forged cross-site request to ride on and CSRF protection is switched off on purpose.
 * <li><b>Who may pass</b> is read from {@link ReglasDeAcceso}, in order; whatever no rule names needs a valid token.
 * <li><b>The token</b> is a JWT signed with HMAC-SHA256. The decoder accepts only that algorithm, from our issuer,
 * and checks the expiry against the same injectable clock that issued it, with no tolerance.
 * <li><b>Errors</b> use the {@code {code, message}} shape ({@link ErroresDeSeguridad}).
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SeguridadProperties.class)
class ConfiguracionDeSeguridad {

	@Bean
	SecurityFilterChain cadenaDeFiltros(HttpSecurity http, ErroresDeSeguridad errores) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.authorizeHttpRequests(acceso -> {
					for (ReglasDeAcceso.Regla regla : ReglasDeAcceso.REGLAS) {
						var coincide = regla.metodo() == null
								? acceso.requestMatchers(regla.patron())
								: acceso.requestMatchers(regla.metodo(), regla.patron());
						if (regla.esPublica()) {
							coincide.permitAll();
						} else {
							coincide.hasAnyRole(regla.roles().stream().map(Rol::name).toArray(String[]::new));
						}
					}
					// Nothing is served at these paths unless the dev profile turns it on; if it is off they answer 404.
					acceso.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/v3/api-docs", "/v3/api-docs/**",
							"/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll();
					acceso.anyRequest().authenticated();
				})
				.oauth2ResourceServer(servidor -> servidor
						.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor()))
						.authenticationEntryPoint(errores)
						.accessDeniedHandler(errores))
				.exceptionHandling(excepciones -> excepciones
						.authenticationEntryPoint(errores)
						.accessDeniedHandler(errores));
		return http.build();
	}

	/** The role of the token becomes the authority ROLE_x; a token with an unknown role gets none and is refused. */
	private static JwtAuthenticationConverter convertidor() {
		Converter<Jwt, List<GrantedAuthority>> autoridades = jwt -> {
			try {
				return List.of(new SimpleGrantedAuthority("ROLE_" + Rol.valueOf(jwt.getClaimAsString("rol")).name()));
			} catch (IllegalArgumentException | NullPointerException e) {
				return List.of();
			}
		};
		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(autoridades::convert);
		return convertidor;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// BCrypt today; the stored prefix ({bcrypt}) lets a stronger algorithm replace it without breaking existing hashes.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	SecretKey claveDeFirma(SeguridadProperties propiedades) {
		return new SecretKeySpec(propiedades.jwtSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey claveDeFirma) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(claveDeFirma));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey claveDeFirma, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveDeFirma).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator tiempo = new JwtTimestampValidator(Duration.ZERO);
		tiempo.setClock(clock);
		OAuth2TokenValidator<Jwt> validadores = new DelegatingOAuth2TokenValidator<>(tiempo, new JwtIssuerValidator(EmisorDeTokens.EMISOR));
		decoder.setJwtValidator(validadores);
		return decoder;
	}

}
