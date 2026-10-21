package dev.portafolio.biblioteca.security;

import dev.portafolio.biblioteca.entity.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Signs the token of an account that has just proved who it is. The time comes from the injectable clock. */
@Component
@RequiredArgsConstructor
public class EmisorDeTokens {

	/** Who issued the token, and who the decoder accepts it from. */
	public static final String EMISOR = "biblioteca-java";

	/** The token and the moment it stops being valid. */
	public record Token(String valor, Instant expiraEn) {
	}

	private final JwtEncoder encoder;
	private final Clock clock;
	private final SeguridadProperties propiedades;

	public Token emitir(Usuario usuario) {
		Instant ahora = clock.instant();
		Instant expira = ahora.plus(propiedades.jwtDuracion());
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(EMISOR)
				.subject(String.valueOf(usuario.getId()))
				.issuedAt(ahora)
				.expiresAt(expira)
				.id(UUID.randomUUID().toString())
				.claim("rol", usuario.getRol().name());
		if (usuario.getSocio() != null) {
			claims.claim("socioId", usuario.getSocio().getId());
		}
		String valor = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
		return new Token(valor, expira);
	}

}
